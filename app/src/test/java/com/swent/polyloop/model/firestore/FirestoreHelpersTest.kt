// Made with Claude.

package com.swent.polyloop.model.firestore

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreHelpersTest {

  private enum class Color {
    RED,
    BLUE,
  }

  private val firestoreError =
      FirebaseFirestoreException("unavailable", FirebaseFirestoreException.Code.UNAVAILABLE)

  // ---------- ids and field readers ----------

  @Test
  fun isValidDocumentIdRejectsBlankIdsAndSlashes() {
    assertTrue(isValidDocumentId("rental-1"))
    assertFalse(isValidDocumentId(""))
    assertFalse(isValidDocumentId("  "))
    assertFalse(isValidDocumentId("rentals/1"))
  }

  @Test
  fun intOrNullAcceptsOnlyLongsThatFitInAnInt() {
    assertEquals(15, intOrNull(15L))
    assertNull(intOrNull(12.5))
    assertNull(intOrNull("15"))
    assertNull(intOrNull(Int.MAX_VALUE.toLong() + 1))
    assertNull(intOrNull(null))
  }

  @Test
  fun enumOrNullFindsTheConstantByName() {
    assertEquals(Color.BLUE, enumOrNull<Color>("BLUE"))
    assertNull(enumOrNull<Color>("blue"))
    assertNull(enumOrNull<Color>(null))
  }

  @Test
  fun dateOrNullParsesIsoDatesOnly() {
    assertEquals(LocalDate.of(2026, 10, 20), dateOrNull("2026-10-20"))
    assertNull(dateOrNull("20.10.2026"))
    assertNull(dateOrNull(20261020L))
    assertNull(dateOrNull(null))
  }

  @Test
  fun instantOrNullReadsTimestampsOnly() {
    val instant = Instant.parse("2026-10-14T10:00:00Z")

    assertEquals(instant, instantOrNull(Timestamp(instant)))
    assertNull(instantOrNull("2026-10-14T10:00:00Z"))
    assertNull(instantOrNull(null))
  }

  // ---------- withFirebaseErrors ----------

  @Test
  fun withFirebaseErrorsReturnsTheResultOfTheBlock() = runTest {
    assertEquals(Result.success(1), withFirebaseErrors { Result.success(1) })
  }

  @Test
  fun withFirebaseErrorsTurnsFirebaseExceptionsIntoFailures() = runTest {
    val result = withFirebaseErrors<Int> { throw firestoreError }

    assertSame(firestoreError, result.exceptionOrNull())
  }

  @Test
  fun withFirebaseErrorsTurnsACancelledTaskIntoAFailure() = runTest {
    val cancelled = CancellationException("task cancelled by Firebase")

    val result = withFirebaseErrors<Int> { throw cancelled }

    assertSame(cancelled, result.exceptionOrNull())
  }

  @Test
  fun withFirebaseErrorsRethrowsWhenTheCallerIsCancelled() = runTest {
    var returned = false
    val job = launch {
      withFirebaseErrors<Int> { awaitCancellation() }
      returned = true
    }
    runCurrent()

    job.cancel()
    runCurrent()

    assertTrue(job.isCancelled)
    assertFalse(returned)
  }

  @Test(expected = IllegalStateException::class)
  fun withFirebaseErrorsDoesNotHideBugs() = runTest {
    withFirebaseErrors<Int> { throw IllegalStateException("bug") }
  }

  // ---------- confirmWrite ----------

  @Test
  fun confirmWriteIsTrueWhenTheServerConfirms() = runTest {
    assertTrue(confirmWrite { Tasks.forResult<Void>(null) })
  }

  @Test
  fun confirmWriteIsFalseWhenTheServerNeverConfirms() = runTest {
    assertFalse(confirmWrite { TaskCompletionSource<Void>().task })
  }

  // ---------- snapshotsAsFlow ----------

  private val query = mockk<Query>()
  private val registration = mockk<ListenerRegistration>(relaxed = true)
  private val listener = slot<EventListener<QuerySnapshot>>()

  private fun listenToQuery() {
    every { query.addSnapshotListener(capture(listener)) } returns registration
  }

  @Test
  fun snapshotsAsFlowEmitsEverySnapshotAndStopsListeningAfterwards() = runTest {
    listenToQuery()
    val first = mockk<QuerySnapshot> { every { size() } returns 1 }
    val second = mockk<QuerySnapshot> { every { size() } returns 2 }

    val sizes = async { query.snapshotsAsFlow { it.size() }.take(2).toList() }
    runCurrent()
    listener.captured.onEvent(first, null)
    listener.captured.onEvent(second, null)

    assertEquals(listOf(1, 2), sizes.await())
    verify { registration.remove() }
  }

  @Test
  fun snapshotsAsFlowThrowsReadErrorsToTheCollector() = runTest {
    listenToQuery()

    val error = async {
      var caught: Throwable? = null
      query.snapshotsAsFlow { it.size() }.catch { caught = it }.toList()
      caught
    }
    runCurrent()
    listener.captured.onEvent(null, firestoreError)

    assertSame(firestoreError, error.await())
  }

  @Test
  fun snapshotsAsFlowIgnoresAnEventWithoutSnapshotOrError() = runTest {
    listenToQuery()
    val snapshot = mockk<QuerySnapshot> { every { size() } returns 3 }

    val size = async { query.snapshotsAsFlow { it.size() }.first() }
    runCurrent()
    listener.captured.onEvent(null, null)
    listener.captured.onEvent(snapshot, null)

    assertEquals(3, size.await())
  }

  // ---------- snapshotsAsFlow on a document ----------

  private val document = mockk<DocumentReference>()
  private val documentListener = slot<EventListener<DocumentSnapshot>>()

  private fun listenToDocument() {
    every { document.addSnapshotListener(capture(documentListener)) } returns registration
  }

  @Test
  fun documentSnapshotsAsFlowEmitsEveryChangeAndStopsListeningAfterwards() = runTest {
    listenToDocument()
    val first = mockk<DocumentSnapshot> { every { id } returns "u1" }
    val second = mockk<DocumentSnapshot> { every { id } returns "u2" }

    val ids = async { document.snapshotsAsFlow { it.id }.take(2).toList() }
    runCurrent()
    documentListener.captured.onEvent(first, null)
    documentListener.captured.onEvent(second, null)

    assertEquals(listOf("u1", "u2"), ids.await())
    verify { registration.remove() }
  }

  @Test
  fun documentSnapshotsAsFlowThrowsReadErrorsToTheCollector() = runTest {
    listenToDocument()

    val error = async {
      var caught: Throwable? = null
      document.snapshotsAsFlow { it.id }.catch { caught = it }.toList()
      caught
    }
    runCurrent()
    documentListener.captured.onEvent(null, firestoreError)

    assertSame(firestoreError, error.await())
  }

  @Test
  fun documentSnapshotsAsFlowIgnoresAnEventWithoutSnapshotOrError() = runTest {
    listenToDocument()
    val snapshot = mockk<DocumentSnapshot> { every { id } returns "u9" }

    val id = async { document.snapshotsAsFlow { it.id }.first() }
    runCurrent()
    documentListener.captured.onEvent(null, null)
    documentListener.captured.onEvent(snapshot, null)

    assertEquals("u9", id.await())
  }
}
