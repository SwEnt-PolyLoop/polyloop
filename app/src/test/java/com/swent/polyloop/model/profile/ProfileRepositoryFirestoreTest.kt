// Made with Claude.

package com.swent.polyloop.model.profile

import android.net.Uri
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import java.util.concurrent.Executor
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
import org.junit.After
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

  @After
  fun tearDown() {
    unmockkStatic(Uri::class)
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

  @Test
  fun createProfileIfMissingFailsForAnInvalidIdWithoutCallingFirestore() = runTest {
    val blank = repository.createProfileIfMissing("", "john@epfl.ch", "John")
    val withSlash = repository.createProfileIfMissing("users/uid123", "john@epfl.ch", "John")

    assertTrue(blank.exceptionOrNull() is IllegalArgumentException)
    assertTrue(withSlash.exceptionOrNull() is IllegalArgumentException)
    verify(exactly = 0) { db.collection(any()) }
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
  fun updateProfileFailsForAnInvalidIdWithoutCallingFirestore() = runTest {
    val blank = repository.updateProfile(" ", "John", "")
    val withSlash = repository.updateProfile("users/uid123", "John", "")

    assertTrue(blank.exceptionOrNull() is IllegalArgumentException)
    assertTrue(withSlash.exceptionOrNull() is IllegalArgumentException)
    verify(exactly = 0) { db.collection(any()) }
  }

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

  // ---------- uploadProfilePhoto ----------

  private val storage = mockk<FirebaseStorage>()
  private val photoRef = mockk<StorageReference>()
  private val localPhoto = mockk<Uri>()
  private val photoUri = "file:///data/profile_photos/photo.jpg"
  private val photoUrl = "https://storage.test/users/uid123/profile.jpg"

  private var storageAccesses = 0

  /** A repository whose Storage holds `users/uid123/profile.jpg`; counts each Storage access. */
  private fun uploadRepository(): ProfileRepositoryFirestore {
    // Uri is an Android class, empty in unit tests, so its parsing is replaced.
    mockkStatic(Uri::class)
    every { Uri.parse(photoUri) } returns localPhoto
    val root = mockk<StorageReference>()
    every { storage.reference } returns root
    every { root.child("users/uid123/profile.jpg") } returns photoRef
    val downloadUrl = mockk<Uri>()
    every { downloadUrl.toString() } returns photoUrl
    every { photoRef.downloadUrl } returns Tasks.forResult(downloadUrl)
    return ProfileRepositoryFirestore(
        db,
        storageProvider = {
          storageAccesses++
          storage
        },
    )
  }

  /** A finished upload, failed with [error] if it is set. */
  private fun finishedUpload(error: Exception? = null): UploadTask = mockk {
    every { isComplete } returns true
    every { isCanceled } returns false
    every { exception } returns error
    every { result } returns mockk()
    // Cancelling a finished upload does nothing, as in the real SDK.
    every { cancel() } returns false
  }

  private val storageError = FirebaseNetworkException("offline")

  /**
   * True when [error] is [storageError]. Coroutines rethrow a copy of an error that crosses the
   * upload's timeout, so the type and message are compared, not the object.
   */
  private fun isStorageError(error: Throwable?): Boolean =
      error is FirebaseNetworkException && error.message == storageError.message

  @Test
  fun uploadProfilePhotoUploadsAJpegAndReturnsItsDownloadUrl() = runTest {
    val repository = uploadRepository()
    val metadata = slot<StorageMetadata>()
    every { photoRef.putFile(localPhoto, capture(metadata)) } returns finishedUpload()

    val result = repository.uploadProfilePhoto("uid123", photoUri)

    assertEquals(Result.success(photoUrl), result)
    assertEquals("image/jpeg", metadata.captured.contentType)
  }

  @Test
  fun uploadProfilePhotoDoesNotChangeTheProfile() = runTest {
    val repository = uploadRepository()
    every { photoRef.putFile(any(), any()) } returns finishedUpload()

    repository.uploadProfilePhoto("uid123", photoUri)

    verify(exactly = 0) { db.collection(any()) }
  }

  @Test
  fun uploadProfilePhotoFailsWithTheCauseWhenTheUploadFails() = runTest {
    val repository = uploadRepository()
    every { photoRef.putFile(any(), any()) } returns finishedUpload(storageError)

    val result = repository.uploadProfilePhoto("uid123", photoUri)

    assertTrue(isStorageError(result.exceptionOrNull()))
    verify(exactly = 0) { photoRef.downloadUrl }
  }

  @Test
  fun uploadProfilePhotoFailsWithTheCauseWhenTheUrlCannotBeRead() = runTest {
    val repository = uploadRepository()
    every { photoRef.putFile(any(), any()) } returns finishedUpload()
    every { photoRef.downloadUrl } returns Tasks.forException(storageError)

    assertTrue(isStorageError(repository.uploadProfilePhoto("uid123", photoUri).exceptionOrNull()))
  }

  @Test
  fun uploadProfilePhotoFailsForAnInvalidIdWithoutCallingStorage() = runTest {
    val repository = uploadRepository()

    val blank = repository.uploadProfilePhoto(" ", photoUri)
    val withSlash = repository.uploadProfilePhoto("users/uid123", photoUri)

    assertTrue(blank.exceptionOrNull() is IllegalArgumentException)
    assertTrue(withSlash.exceptionOrNull() is IllegalArgumentException)
    assertEquals(0, storageAccesses)
  }

  @Test
  fun onlyUploadsTouchStorage() = runTest {
    val repository = uploadRepository()
    profileExists(true)
    every { document.update(any<Map<String, Any>>()) } returns Tasks.forResult(null)

    repository.createProfileIfMissing("uid123", "john@epfl.ch", "John")
    repository.updateProfile("uid123", "John", "")

    assertEquals(0, storageAccesses)
  }

  /** An upload that never finishes, as when offline; records whether it was cancelled. */
  private var uploadCancelled = false

  private fun neverEndingUpload(): UploadTask = mockk {
    every { isComplete } returns false
    every {
      addOnCompleteListener(any<Executor>(), any<OnCompleteListener<UploadTask.TaskSnapshot>>())
    } returns this
    every { cancel() } answers
        {
          uploadCancelled = true
          true
        }
  }

  @Test
  fun uploadProfilePhotoTimesOutAndCancelsAnUploadThatNeverEnds() = runTest {
    val repository = uploadRepository()
    every { photoRef.putFile(any(), any()) } returns neverEndingUpload()

    val result = repository.uploadProfilePhoto("uid123", photoUri)

    assertTrue(result.exceptionOrNull() is TimeoutException)
    assertTrue(uploadCancelled)
    verify(exactly = 0) { photoRef.downloadUrl }
  }

  @Test
  fun uploadProfilePhotoTimesOutWhenTheUrlIsNeverRead() = runTest {
    val repository = uploadRepository()
    every { photoRef.putFile(any(), any()) } returns finishedUpload()
    every { photoRef.downloadUrl } returns TaskCompletionSource<Uri>().task

    val result = repository.uploadProfilePhoto("uid123", photoUri)

    assertTrue(result.exceptionOrNull() is TimeoutException)
  }
}
