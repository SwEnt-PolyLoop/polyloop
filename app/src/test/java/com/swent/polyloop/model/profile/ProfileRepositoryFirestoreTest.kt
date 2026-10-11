// Made with Claude.

package com.swent.polyloop.model.profile

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.concurrent.TimeoutException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryFirestoreTest {

  private lateinit var db: FirebaseFirestore
  private lateinit var document: DocumentReference
  private lateinit var repository: ProfileRepositoryFirestore

  @Before
  fun setUp() {
    db = mockk()
    document = mockk()
    val users = mockk<CollectionReference>()
    every { db.collection("users") } returns users
    every { users.document("uid123") } returns document
    every { document.set(any(), any()) } returns Tasks.forResult(null)

    repository = ProfileRepositoryFirestore(db)
  }

  private fun profileExists(exists: Boolean) {
    val snapshot = mockk<DocumentSnapshot>()
    every { snapshot.exists() } returns exists
    every { document.get() } returns Tasks.forResult(snapshot)
  }

  private val unavailable =
      FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)

  private val john = UserProfile(uid = "uid123", name = "John", email = "john@epfl.ch")

  private fun snapshotWith(data: Map<String, Any?>?): DocumentSnapshot {
    val snapshot = mockk<DocumentSnapshot>()
    every { snapshot.data } returns data
    return snapshot
  }

  private val johnData = mapOf<String, Any?>("name" to "John", "email" to "john@epfl.ch")

  @Test
  fun createsProfileWithOnlyAppOwnedFieldsWhenMissing() = runTest {
    profileExists(false)

    val result = repository.createProfileIfMissing("uid123", "john@epfl.ch", "John")

    assertEquals(Result.success(Unit), result)
    // Merged, so the fields Cloud Functions write (e.g. the rating) are kept.
    verify {
      document.set(
          mapOf("name" to "John", "email" to "john@epfl.ch", "photoUrl" to ""),
          SetOptions.merge(),
      )
    }
  }

  @Test
  fun doesNotWriteWhenProfileAlreadyExists() = runTest {
    profileExists(true)

    val result = repository.createProfileIfMissing("uid123", "john@epfl.ch", "John")

    assertEquals(Result.success(Unit), result)
    verify(exactly = 0) { document.set(any(), any()) }
  }

  @Test
  fun failsWithCauseWhenProfileCannotBeRead() = runTest {
    every { document.get() } returns Tasks.forException(unavailable)

    val result = repository.createProfileIfMissing("uid123", "john@epfl.ch", "John")

    assertSame(unavailable, result.exceptionOrNull())
  }

  @Test
  fun failsWithCauseWhenProfileCannotBeWritten() = runTest {
    profileExists(false)
    every { document.set(any(), any()) } returns Tasks.forException(unavailable)

    val result = repository.createProfileIfMissing("uid123", "john@epfl.ch", "John")

    assertSame(unavailable, result.exceptionOrNull())
  }

  @Test
  fun failsWithTimeoutWhenTheWriteIsNeverConfirmed() = runTest {
    profileExists(false)
    // Like a write saved on the phone whose confirmation never comes (connection dropped).
    every { document.set(any(), any()) } returns TaskCompletionSource<Void>().task

    val result = repository.createProfileIfMissing("uid123", "john@epfl.ch", "John")

    assertTrue(result.exceptionOrNull() is TimeoutException)
  }

  @Test
  fun taskCancelledByFirebaseIsReportedAsFailure() = runTest {
    every { document.get() } returns Tasks.forCanceled()

    val result = repository.createProfileIfMissing("uid123", "john@epfl.ch", "John")

    assertTrue(result.exceptionOrNull() is CancellationException)
  }

  @Test
  fun callerCancellationIsRethrownInsteadOfReportedAsFailure() = runTest {
    // A read that never answers, so the call is still waiting when the caller is cancelled.
    every { document.get() } returns TaskCompletionSource<DocumentSnapshot>().task
    var result: Result<Unit>? = null

    val caller =
        launch(start = CoroutineStart.UNDISPATCHED) {
          result = repository.createProfileIfMissing("uid123", "john@epfl.ch", "John")
        }
    caller.cancel()
    caller.join()

    assertTrue(caller.isCancelled)
    assertNull(result)
  }

  @Test
  fun errorsOtherThanFirebaseAreNotHidden() = runTest {
    val bug = IllegalArgumentException("Invalid document reference")
    every { document.get() } throws bug

    val thrown = runCatching {
      repository.createProfileIfMissing("uid123", "john@epfl.ch", "John")
    }
        .exceptionOrNull()

    assertSame(bug, thrown)
  }

  // ---------- getProfile ----------

  @Test
  fun getProfileReadsTheDocument() = runTest {
    every { document.get() } returns Tasks.forResult(snapshotWith(johnData))

    assertEquals(Result.success(john), repository.getProfile("uid123"))
  }

  @Test
  fun getProfileReturnsNullWhenThereIsNoProfile() = runTest {
    every { document.get() } returns Tasks.forResult(snapshotWith(null))

    assertEquals(Result.success(null), repository.getProfile("uid123"))
  }

  @Test
  fun getProfileReturnsNullForAnInvalidIdWithoutCallingFirestore() = runTest {
    assertEquals(Result.success(null), repository.getProfile(""))
    assertEquals(Result.success(null), repository.getProfile("users/uid123"))
    verify(exactly = 0) { db.collection(any()) }
  }

  @Test
  fun getProfileFailsWithCauseWhenItCannotBeRead() = runTest {
    every { document.get() } returns Tasks.forException(unavailable)

    assertSame(unavailable, repository.getProfile("uid123").exceptionOrNull())
  }

  // ---------- observeProfile ----------

  @Test
  fun observeProfileEmitsEachChangeAndStopsListeningWhenCollectionStops() = runTest {
    val listener = slot<EventListener<DocumentSnapshot>>()
    val registration = mockk<ListenerRegistration>(relaxed = true)
    every { document.addSnapshotListener(capture(listener)) } returns registration
    val emitted = mutableListOf<UserProfile?>()

    val collector =
        launch(UnconfinedTestDispatcher(testScheduler)) {
          repository.observeProfile("uid123").toList(emitted)
        }
    listener.captured.onEvent(snapshotWith(johnData), null)
    listener.captured.onEvent(snapshotWith(johnData), null) // same data: not emitted again
    listener.captured.onEvent(snapshotWith(johnData + ("name" to "Johnny")), null)
    listener.captured.onEvent(snapshotWith(null), null) // profile deleted
    collector.cancelAndJoin()

    assertEquals(listOf(john, john.copy(name = "Johnny"), null), emitted)
    verify { registration.remove() }
  }

  @Test
  fun observeProfileThrowsTheReadErrorToTheCollector() = runTest {
    val listener = slot<EventListener<DocumentSnapshot>>()
    every { document.addSnapshotListener(capture(listener)) } returns mockk(relaxed = true)
    var caught: Throwable? = null

    launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.observeProfile("uid123").catch { caught = it }.collect {}
    }
    listener.captured.onEvent(null, unavailable)

    assertSame(unavailable, caught)
  }

  @Test
  fun observeProfileEmitsNullForAnInvalidId() = runTest {
    assertNull(repository.observeProfile("").first())
    verify(exactly = 0) { db.collection(any()) }
  }

  // ---------- updateProfile ----------

  @Test
  fun updateProfileWritesOnlyNameAndPhoto() = runTest {
    every { document.update(any<Map<String, Any>>()) } returns Tasks.forResult(null)

    val result = repository.updateProfile("uid123", "Johnny", "https://example.com/john.jpg")

    assertEquals(Result.success(Unit), result)
    verify {
      document.update(mapOf("name" to "Johnny", "photoUrl" to "https://example.com/john.jpg"))
    }
  }

  @Test
  fun updateProfileFailsWithCauseWhenTheProfileIsMissing() = runTest {
    val notFound =
        FirebaseFirestoreException("no profile", FirebaseFirestoreException.Code.NOT_FOUND)
    every { document.update(any<Map<String, Any>>()) } returns Tasks.forException(notFound)

    assertSame(notFound, repository.updateProfile("uid123", "John", "").exceptionOrNull())
  }

  @Test
  fun updateProfileFailsWithTimeoutWhenTheWriteIsNeverConfirmed() = runTest {
    every { document.update(any<Map<String, Any>>()) } returns TaskCompletionSource<Void>().task

    val result = repository.updateProfile("uid123", "John", "")

    assertTrue(result.exceptionOrNull() is TimeoutException)
  }
}
