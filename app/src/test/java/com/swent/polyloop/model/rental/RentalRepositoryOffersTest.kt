// Made with Claude.

package com.swent.polyloop.model.rental

import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The negotiation calls of [RentalRepositoryFirestore]: each calls the right Cloud Function with
 * the right data and reports a refusal as a failure. The Functions themselves are tested in
 * functions/src, on the emulator.
 */
class RentalRepositoryOffersTest {

  private val db = mockk<FirebaseFirestore>()
  private val functions = mockk<FirebaseFunctions>()
  private val callable = mockk<HttpsCallableReference>()
  private val sent = slot<Any>()
  private val repository = RentalRepositoryFirestore(db = db, functions = { functions })

  @Before
  fun setUp() {
    every { functions.getHttpsCallable(any()) } returns callable
    every { callable.call(capture(sent)) } returns Tasks.forResult(mockk<HttpsCallableResult>())
  }

  private fun assertCalled(name: String, data: Map<String, Any>) {
    verify { functions.getHttpsCallable(name) }
    assertEquals(data, sent.captured)
  }

  @Test
  fun makeOfferSendsTheDepositAndBothAddresses() = runTest {
    val result = repository.makeOffer("r1", 180, "Route Cantonale 1", "Rue de Lausanne 10")

    assertTrue(result.isSuccess)
    assertCalled(
        "makeOffer",
        mapOf(
            "rentalId" to "r1",
            "deposit" to 180,
            "pickupAddress" to "Route Cantonale 1",
            "returnAddress" to "Rue de Lausanne 10",
        ),
    )
  }

  @Test
  fun acceptOfferSendsTheAcceptAction() = runTest {
    assertTrue(repository.acceptOffer("r1").isSuccess)
    assertCalled("respondToOffer", mapOf("rentalId" to "r1", "action" to "ACCEPT"))
  }

  @Test
  fun declineOfferSendsTheDeclineAction() = runTest {
    assertTrue(repository.declineOffer("r1").isSuccess)
    assertCalled("respondToOffer", mapOf("rentalId" to "r1", "action" to "DECLINE"))
  }

  @Test
  fun counterOfferSendsTheCounterActionWithItsDeposit() = runTest {
    assertTrue(repository.counterOffer("r1", 120).isSuccess)
    assertCalled(
        "respondToOffer",
        mapOf("rentalId" to "r1", "action" to "COUNTER", "deposit" to 120),
    )
  }

  @Test
  fun declineRequestSendsTheRentalId() = runTest {
    assertTrue(repository.declineRequest("r1").isSuccess)
    assertCalled("declineRequest", mapOf("rentalId" to "r1"))
  }

  @Test
  fun aRefusalFromTheServerIsAFailure() = runTest {
    // Its constructor is internal to the SDK, so the error is a mock of the same class.
    val refusal = mockk<FirebaseFunctionsException>(relaxed = true)
    every { callable.call(any()) } returns Tasks.forException(refusal)

    assertSame(refusal, repository.acceptOffer("r1").exceptionOrNull())
  }

  // ---------- observeRental ----------

  private val document = mockk<DocumentReference>()
  private val listener = slot<EventListener<DocumentSnapshot>>()

  private fun documentIsObserved(emit: (EventListener<DocumentSnapshot>) -> Unit) {
    val collection = mockk<CollectionReference>()
    every { db.collection(RentalFields.COLLECTION) } returns collection
    every { collection.document("r1") } returns document
    every { document.addSnapshotListener(capture(listener)) } answers
        {
          emit(listener.captured)
          mockk(relaxed = true)
        }
  }

  private fun snapshot(data: Map<String, Any>?): DocumentSnapshot = mockk {
    every { id } returns "r1"
    every { getData(ServerTimestampBehavior.ESTIMATE) } returns data
  }

  private val storedRental: Map<String, Any> =
      mapOf(
          RentalFields.LISTING_ID to "listing-1",
          RentalFields.OWNER_ID to "owner",
          RentalFields.BORROWER_ID to "borrower",
          RentalFields.START_DATE to "2026-12-01",
          RentalFields.END_DATE to "2026-12-03",
          RentalFields.STATUS to "REQUESTED",
          RentalFields.PRICE_PER_DAY to 5L,
          RentalFields.TOTAL_PRICE to 15L,
          RentalFields.CREATED_AT to Timestamp(Instant.parse("2026-11-04T10:00:00Z")),
      )

  @Test
  fun observeRentalEmitsTheRental() = runTest {
    documentIsObserved { it.onEvent(snapshot(storedRental), null) }

    assertEquals("r1", repository.observeRental("r1").first()?.id)
  }

  @Test
  fun observeRentalEmitsNullWhenItDoesNotExist() = runTest {
    documentIsObserved { it.onEvent(snapshot(null), null) }

    assertNull(repository.observeRental("r1").first())
  }

  @Test
  fun observeRentalEmitsNullForAnInvalidIdWithoutListening() = runTest {
    assertNull(repository.observeRental("").first())
    verify(exactly = 0) { db.collection(any()) }
  }

  @Test
  fun observeRentalThrowsReadErrorsToTheCollector() = runTest {
    val error =
        FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
    documentIsObserved { it.onEvent(null, error) }
    var caught: Throwable? = null

    repository.observeRental("r1").catch { caught = it }.toList()

    assertSame(error, caught)
  }
}
