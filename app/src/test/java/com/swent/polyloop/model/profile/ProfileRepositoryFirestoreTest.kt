// Made with Claude.

package com.swent.polyloop.model.profile

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.TimeoutException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
}
