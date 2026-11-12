// Made with Claude.

package com.swent.polyloop.model.dispute

import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
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
 * [DisputeRepositoryFirestore] with Firebase mocked: each action calls the right Cloud Function
 * with the right data and turns a lost race into the exception the app expects. The Functions
 * themselves are tested in functions/src, on the emulator.
 */
class DisputeRepositoryFirestoreTest {

  private val db = mockk<FirebaseFirestore>()
  private val functions = mockk<FirebaseFunctions>()
  private val callable = mockk<HttpsCallableReference>()
  private val sent = slot<Any>()
  private val repository = DisputeRepositoryFirestore(db = db, functions = { functions })

  @Before
  fun setUp() {
    every { functions.getHttpsCallable(any()) } returns callable
    every { callable.call(capture(sent)) } returns Tasks.forResult(mockk<HttpsCallableResult>())
  }

  private fun assertCalled(name: String, data: Map<String, Any>) {
    verify { functions.getHttpsCallable(name) }
    assertEquals(data, sent.captured)
  }

  // ---------- actions ----------

  @Test
  fun proposeSendsTheSplit() = runTest {
    assertTrue(repository.propose("r1", 40).isSuccess)
    assertCalled("proposeSplit", mapOf("rentalId" to "r1", "lenderPercent" to 40))
  }

  @Test
  fun acceptSendsTheSplitTheUserSaw() = runTest {
    assertTrue(repository.accept("r1", 70).isSuccess)
    assertCalled("acceptSplit", mapOf("rentalId" to "r1", "lenderPercent" to 70))
  }

  @Test
  fun escalateSendsTheRentalId() = runTest {
    assertTrue(repository.escalate("r1").isSuccess)
    assertCalled("escalateDispute", mapOf("rentalId" to "r1"))
  }

  /** Makes the next call refused with [code] and [details], as an HttpsError from a Function. */
  private fun refusedWith(
      details: Any?,
      code: FirebaseFunctionsException.Code = FirebaseFunctionsException.Code.FAILED_PRECONDITION,
  ): FirebaseFunctionsException {
    // Its constructor is internal to the SDK, so the error is a mock of the same class.
    val refusal = mockk<FirebaseFunctionsException>(relaxed = true)
    every { refusal.code } returns code
    every { refusal.details } returns details
    every { callable.call(any()) } returns Tasks.forException(refusal)
    return refusal
  }

  @Test
  fun aLostRaceIsReadFromTheReasonOfTheRefusal() = runTest {
    refusedWith(mapOf("reason" to "PROPOSAL_CHANGED"))
    assertTrue(repository.accept("r1", 70).exceptionOrNull() is ProposalChangedException)

    refusedWith(mapOf("reason" to "STAGE_CHANGED"))
    assertTrue(repository.propose("r1", 40).exceptionOrNull() is StageChangedException)
    assertTrue(repository.accept("r1", 70).exceptionOrNull() is StageChangedException)
    assertTrue(repository.escalate("r1").exceptionOrNull() is StageChangedException)
  }

  @Test
  fun anyOtherRefusalStaysAsItIs() = runTest {
    val refusals =
        listOf(
            refusedWith(details = null),
            refusedWith(mapOf("reason" to "SOMETHING_ELSE")),
            refusedWith(
                mapOf("reason" to "STAGE_CHANGED"),
                code = FirebaseFunctionsException.Code.PERMISSION_DENIED,
            ),
        )

    refusals.forEach { refusal ->
      every { callable.call(any()) } returns Tasks.forException(refusal)
      assertSame(refusal, repository.propose("r1", 40).exceptionOrNull())
    }
  }

  // ---------- observeDispute ----------

  private val at = Instant.parse("2026-10-14T10:00:00Z")
  private val listener = slot<EventListener<DocumentSnapshot>>()

  /** Observes `disputes/r1`, whose listener receives [data] (null: no document) on subscribing. */
  private fun documentHolds(data: Map<String, Any>?) {
    val collection = mockk<CollectionReference>()
    val document = mockk<DocumentReference>()
    val snapshot =
        mockk<DocumentSnapshot> { every { getData(ServerTimestampBehavior.ESTIMATE) } returns data }
    every { db.collection(DisputeFields.COLLECTION) } returns collection
    every { collection.document("r1") } returns document
    every { document.addSnapshotListener(capture(listener)) } answers
        {
          listener.captured.onEvent(snapshot, null)
          mockk(relaxed = true)
        }
  }

  @Test
  fun observeDisputeReadsTheDocument() = runTest {
    documentHolds(mapOf("stage" to "SETTLE", "escalatesAt" to Timestamp(at)))

    assertEquals(
        Dispute("r1", DisputeStage.SETTLE, emptyList(), at),
        repository.observeDispute("r1").first(),
    )
  }

  @Test
  fun observeDisputeGivesNullWithoutDocumentOrForAnInvalidId() = runTest {
    documentHolds(null)

    assertNull(repository.observeDispute("r1").first())
    // Only "r1" is mocked, so reaching Firestore for "a/b" would throw.
    assertNull(repository.observeDispute("a/b").first())
  }

  @Test
  fun aDocumentThatBreaksTheSchemaEndsTheFlowWithAnError() = runTest {
    documentHolds(mapOf("stage" to "SETTLE", "escalatesAt" to "not a date"))
    var error: Throwable? = null

    val emitted = repository.observeDispute("r1").catch { error = it }.toList()

    assertTrue(emitted.isEmpty())
    assertTrue(error is IllegalStateException)
  }
}
