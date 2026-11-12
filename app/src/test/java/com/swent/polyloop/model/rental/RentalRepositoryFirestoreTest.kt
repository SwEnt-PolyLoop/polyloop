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
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RentalRepositoryFirestoreTest {

  private val db = mockk<FirebaseFirestore>()
  private val collection = mockk<CollectionReference>()
  private val repository = RentalRepositoryFirestore(db, functions = { functions })

  private val firestoreError =
      FirebaseFirestoreException("unavailable", FirebaseFirestoreException.Code.UNAVAILABLE)

  private val listing =
      Listing(
          id = "listing-1",
          ownerId = "owner",
          title = "Camping tent",
          description = "Light 2-person tent.",
          category = ListingCategory.SPORTS_OUTDOOR,
          photoUrls = List(3) { "https://example.com/$it.jpg" },
          pricePerDay = 5,
          itemValue = 120,
          availableFrom = LocalDate.of(2026, 10, 1),
          availableTo = LocalDate.of(2026, 12, 31),
          status = ListingStatus.PUBLISHED,
          pickupArea = "Ecublens",
          dropOffArea = "Renens",
      )

  private val borrower = AuthUser(uid = "borrower", email = "loca@epfl.ch", name = "Loca")
  private val start = LocalDate.of(2026, 10, 20)
  private val end = LocalDate.of(2026, 10, 22)

  /** The fields of a stored rental, as Firestore returns them, created at [createdAt]. */
  private fun storedData(createdAt: Instant, status: String = "REQUESTED"): Map<String, Any> =
      mapOf(
          RentalFields.LISTING_ID to "listing-1",
          RentalFields.LISTING_TITLE to "Camping tent",
          RentalFields.OWNER_ID to "owner",
          RentalFields.OWNER_NAME to "Vida",
          RentalFields.BORROWER_ID to "borrower",
          RentalFields.BORROWER_NAME to "Loca",
          RentalFields.START_DATE to "2026-10-20",
          RentalFields.END_DATE to "2026-10-22",
          RentalFields.STATUS to status,
          RentalFields.PRICE_PER_DAY to 5L,
          RentalFields.TOTAL_PRICE to 15L,
          RentalFields.PICKUP_AREA to "Ecublens",
          RentalFields.DROP_OFF_AREA to "Renens",
          RentalFields.CREATED_AT to Timestamp(createdAt),
          RentalFields.LAST_MESSAGE_AT to Timestamp(createdAt),
      )

  private fun expectedRental(id: String, createdAt: Instant) =
      Rental(
          id = id,
          listingId = "listing-1",
          listingTitle = "Camping tent",
          ownerId = "owner",
          ownerName = "Vida",
          borrowerId = "borrower",
          borrowerName = "Loca",
          startDate = start,
          endDate = end,
          status = RentalStatus.REQUESTED,
          pricePerDay = 5,
          totalPrice = 15,
          pickupArea = "Ecublens",
          dropOffArea = "Renens",
          createdAt = createdAt,
      )

  /** A document snapshot of [id] holding [data] (null: the document does not exist). */
  private fun snapshot(id: String, data: Map<String, Any>?): DocumentSnapshot = mockk {
    every { this@mockk.id } returns id
    every { getData(ServerTimestampBehavior.ESTIMATE) } returns data
  }

  private val createdAt = Instant.parse("2026-10-14T10:00:00Z")

  @Before
  fun setUp() {
    every { db.collection(RentalFields.COLLECTION) } returns collection
  }

  // ---------- requestRental ----------

  private val functions = mockk<FirebaseFunctions>()
  private val callable = mockk<HttpsCallableReference>()
  private val sent = slot<Any>()

  private fun functionIsCalled() {
    every { functions.getHttpsCallable(any()) } returns callable
    every { callable.call(capture(sent)) } returns Tasks.forResult(mockk<HttpsCallableResult>())
  }

  /** Makes the call refused with [code] and [details], as an HttpsError from the Function. */
  private fun refusedWith(
      details: Any?,
      code: FirebaseFunctionsException.Code = FirebaseFunctionsException.Code.FAILED_PRECONDITION,
  ): FirebaseFunctionsException {
    // Its constructor is internal to the SDK, so the error is a mock of the same class.
    val refusal = mockk<FirebaseFunctionsException>(relaxed = true)
    every { refusal.code } returns code
    every { refusal.details } returns details
    every { functions.getHttpsCallable(any()) } returns callable
    every { callable.call(any()) } returns Tasks.forException(refusal)
    return refusal
  }

  @Test
  fun requestRentalCallsTheFunctionWithOnlyAFreshIdTheListingAndTheDates() = runTest {
    functionIsCalled()

    val id = repository.requestRental("listing-1", start, end).getOrThrow()

    verify { functions.getHttpsCallable("requestRental") }
    assertEquals(
        mapOf(
            "rentalId" to id,
            "listingId" to "listing-1",
            "startDate" to "2026-10-20",
            "endDate" to "2026-10-22",
        ),
        sent.captured,
    )
    assertEquals(id, UUID.fromString(id).toString())
  }

  @Test
  fun requestRentalGeneratesANewIdForEachCall() = runTest {
    functionIsCalled()

    val first = repository.requestRental("listing-1", start, end).getOrThrow()
    val second = repository.requestRental("listing-1", start, end).getOrThrow()

    assertNotEquals(first, second)
  }

  @Test
  fun requestRentalRejectsReversedDatesWithoutCalling() = runTest {
    val result = repository.requestRental("listing-1", end, start)

    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    verify(exactly = 0) { functions.getHttpsCallable(any()) }
  }

  @Test
  fun requestRentalFailsWithInsufficientFundsOnlyForThatReason() = runTest {
    refusedWith(mapOf("reason" to "INSUFFICIENT_FUNDS"))

    val result = repository.requestRental("listing-1", start, end)

    assertTrue(result.exceptionOrNull() is InsufficientFundsException)
  }

  @Test
  fun requestRentalKeepsEveryOtherRefusalAsItIs() = runTest {
    val refusals =
        listOf(
            refusedWith(mapOf("reason" to "SOMETHING_ELSE")),
            refusedWith(null),
            refusedWith("INSUFFICIENT_FUNDS"),
            refusedWith(
                mapOf("reason" to "INSUFFICIENT_FUNDS"),
                code = FirebaseFunctionsException.Code.PERMISSION_DENIED,
            ),
            // The id is already used by another request.
            refusedWith(null, code = FirebaseFunctionsException.Code.ALREADY_EXISTS),
        )

    refusals.forEach { refusal ->
      every { callable.call(any()) } returns Tasks.forException(refusal)
      assertSame(refusal, repository.requestRental("listing-1", start, end).exceptionOrNull())
    }
  }

  @Test
  fun requestRentalFailsWithTheCauseWhenTheCallFails() = runTest {
    every { functions.getHttpsCallable(any()) } returns callable
    every { callable.call(any()) } returns Tasks.forException(firestoreError)

    val result = repository.requestRental("listing-1", start, end)

    assertSame(firestoreError, result.exceptionOrNull())
  }

  // ---------- getRental ----------

  private val rentalDocument = mockk<DocumentReference>()

  @Test
  fun getRentalReadsTheRental() = runTest {
    every { collection.document("rental-1") } returns rentalDocument
    every { rentalDocument.get() } returns
        Tasks.forResult(snapshot("rental-1", storedData(createdAt)))

    assertEquals(
        expectedRental("rental-1", createdAt),
        repository.getRental("rental-1").getOrThrow(),
    )
  }

  @Test
  fun getRentalReturnsNullForAMissingOrMalformedRental() = runTest {
    every { collection.document("missing") } returns rentalDocument
    every { rentalDocument.get() } returns Tasks.forResult(snapshot("missing", null))
    val malformed = mockk<DocumentReference>()
    every { collection.document("malformed") } returns malformed
    every { malformed.get() } returns
        Tasks.forResult(snapshot("malformed", storedData(createdAt, status = "UNKNOWN")))

    assertNull(repository.getRental("missing").getOrThrow())
    assertNull(repository.getRental("malformed").getOrThrow())
  }

  @Test
  fun getRentalReturnsNullForAnInvalidIdWithoutReading() = runTest {
    assertNull(repository.getRental("").getOrThrow())
    assertNull(repository.getRental("rentals/1").getOrThrow())
    verify(exactly = 0) { db.collection(any()) }
  }

  @Test
  fun getRentalFailsWhenTheReadFails() = runTest {
    every { collection.document("rental-1") } returns rentalDocument
    every { rentalDocument.get() } returns Tasks.forException(firestoreError)

    assertSame(firestoreError, repository.getRental("rental-1").exceptionOrNull())
  }

  // ---------- observeRentalsAsBorrower / AsLender ----------

  private val query = mockk<Query>()
  private val registration = mockk<ListenerRegistration>(relaxed = true)
  private val listener = slot<EventListener<QuerySnapshot>>()

  private fun queryOn(field: String, uid: String) {
    every { collection.whereEqualTo(field, uid) } returns query
    every { query.addSnapshotListener(capture(listener)) } returns registration
  }

  private fun querySnapshot(vararg documents: DocumentSnapshot): QuerySnapshot = mockk {
    every { this@mockk.documents } returns documents.toList()
  }

  @Test
  fun observeRentalsAsBorrowerEmitsTheBorrowersRentalsNewestFirst() = runTest {
    queryOn(RentalFields.BORROWER_ID, "borrower")
    val older = Instant.parse("2026-10-13T10:00:00Z")

    val rentals = async { repository.observeRentalsAsBorrower("borrower").first() }
    runCurrent()
    listener.captured.onEvent(
        querySnapshot(
            snapshot("old", storedData(older)),
            snapshot("broken", storedData(older, status = "UNKNOWN")),
            snapshot("new", storedData(createdAt)),
        ),
        null,
    )

    assertEquals(
        listOf(expectedRental("new", createdAt), expectedRental("old", older)),
        rentals.await(),
    )
  }

  @Test
  fun observeRentalsAsLenderQueriesByOwnerAndEmitsEachChange() = runTest {
    queryOn(RentalFields.OWNER_ID, "owner")

    val emissions = async { repository.observeRentalsAsLender("owner").take(2).toList() }
    runCurrent()
    listener.captured.onEvent(querySnapshot(), null)
    listener.captured.onEvent(querySnapshot(snapshot("r1", storedData(createdAt))), null)

    assertEquals(listOf(emptyList(), listOf(expectedRental("r1", createdAt))), emissions.await())
  }

  @Test
  fun observeThrowsReadErrorsToTheCollector() = runTest {
    queryOn(RentalFields.BORROWER_ID, "borrower")

    val error = async {
      var caught: Throwable? = null
      repository.observeRentalsAsBorrower("borrower").catch { caught = it }.toList()
      caught
    }
    runCurrent()
    listener.captured.onEvent(null, firestoreError)

    assertSame(firestoreError, error.await())
  }
}
