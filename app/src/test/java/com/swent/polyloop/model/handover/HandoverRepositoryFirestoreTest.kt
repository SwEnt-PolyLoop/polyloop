// Made with Claude.

package com.swent.polyloop.model.handover

import android.net.Uri
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.util.concurrent.Executor
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The upload of [HandoverRepositoryFirestore], with Firebase mocked: an upload that never ends must
 * be cancelled whether the wait times out or the caller is cancelled, so no file is left in Storage
 * without its document. The rest is covered by the emulator test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HandoverRepositoryFirestoreTest {

  private val photoUri = "file:///data/listing_photos/photo.jpg"
  private val photoRef = mockk<StorageReference>()

  // The ids of the user's own photos in the local snapshot, which also holds writes still waiting
  // to be sent, and the Storage paths the uploads were started for.
  private var existingIds = emptyList<String>()
  private val uploadedPaths = mutableListOf<String>()
  private var uploadCancelled = false

  /** An upload that never finishes, as when offline; records whether it was cancelled. */
  private val upload: UploadTask = mockk {
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

  // What the upload of a path does (by default, never finishes) and what reading its metadata
  // gives.
  private var uploadFor: (String) -> UploadTask = { upload }
  private var metadata: Task<StorageMetadata> = Tasks.forResult(mockk())

  private fun failedUpload(error: Exception): UploadTask = mockk {
    every { isComplete } returns true
    every { isCanceled } returns false
    every { exception } returns error
  }

  private fun storageError(httpCode: Int) =
      StorageException.fromExceptionAndHttpCode(null, httpCode)!!

  private lateinit var repository: HandoverRepositoryFirestore

  @Before
  fun setUp() {
    // Uri is an Android class, empty in unit tests, so its parsing is replaced.
    mockkStatic(Uri::class)
    every { Uri.parse(photoUri) } returns mockk()
    val auth = mockk<FirebaseAuth> { every { currentUser?.uid } returns "u1" }
    val registration = mockk<ListenerRegistration>(relaxed = true)
    val query = mockk<Query>()
    every { query.addSnapshotListener(any<EventListener<QuerySnapshot>>()) } answers
        {
          val snapshot = mockk<QuerySnapshot>()
          every { snapshot.documents } returns
              existingIds.map { id -> mockk { every { this@mockk.id } returns id } }
          firstArg<EventListener<QuerySnapshot>>().onEvent(snapshot, null)
          registration
        }
    val ownPhotos = mockk<Query> { every { whereEqualTo("phase", "PICKUP") } returns query }
    val photos =
        mockk<CollectionReference> {
          every { whereEqualTo("takenBy", "u1") } returns ownPhotos
          every { document(any<String>()) } answers
              {
                val id = firstArg<String>()
                mockk { every { path } returns "rentals/r1/photos/$id" }
              }
        }
    val rental = mockk<DocumentReference> { every { collection("photos") } returns photos }
    val rentals = mockk<CollectionReference> { every { document("r1") } returns rental }
    val db = mockk<FirebaseFirestore> { every { collection("rentals") } returns rentals }
    val storage = mockk<FirebaseStorage>()
    every { storage.reference.child(any<String>()) } answers
        {
          uploadedPaths.add(firstArg())
          photoRef
        }
    every { photoRef.putFile(any(), any()) } answers { uploadFor(uploadedPaths.last()) }
    every { photoRef.metadata } answers { metadata }
    repository = HandoverRepositoryFirestore(db, auth) { storage }
  }

  @After
  fun tearDown() {
    unmockkStatic(Uri::class)
  }

  @Test
  fun anUploadThatTakesTooLongIsCancelled() = runTest {
    val result = repository.addPhoto("r1", HandoverPhase.PICKUP, photoUri)

    assertTrue(result.exceptionOrNull() is TimeoutException)
    assertTrue(uploadCancelled)
  }

  @Test
  fun leavingDuringTheUploadCancelsIt() = runTest {
    val call = launch { repository.addPhoto("r1", HandoverPhase.PICKUP, photoUri) }
    runCurrent() // the upload has started and is being waited for

    call.cancel() // what leaving the screen does to the ViewModel's coroutine
    call.join()

    assertTrue(uploadCancelled)
    verify(exactly = 0) { photoRef.downloadUrl }
  }

  @Test
  fun aPhotoGetsTheLowestFreeSlotOfTheUser() = runTest {
    // A random id, as before slots, and another user's slot use none.
    existingIds = listOf("u1_PICKUP_0", "u1_PICKUP_2", "random-id", "u2_PICKUP_1")

    repository.addPhoto("r1", HandoverPhase.PICKUP, photoUri)

    assertEquals(listOf("rentals/r1/photos/u1_PICKUP_1.jpg"), uploadedPaths)
  }

  @Test
  fun theSlotOfAPhotoStillWaitingToBeSentIsUsed() = runTest {
    // Offline, the local snapshot already holds the photo whose write is queued.
    existingIds = List(3) { "u1_PICKUP_$it" }

    repository.addPhoto("r1", HandoverPhase.PICKUP, photoUri)

    assertEquals(listOf("rentals/r1/photos/u1_PICKUP_3.jpg"), uploadedPaths)
  }

  @Test
  fun withAllSlotsUsedNothingIsUploaded() = runTest {
    existingIds = List(HandoverPhoto.MAX_PHOTOS) { "u1_PICKUP_$it" }

    val result = repository.addPhoto("r1", HandoverPhase.PICKUP, photoUri)

    assertTrue(result.exceptionOrNull() is IllegalStateException)
    assertTrue(uploadedPaths.isEmpty())
    verify(exactly = 0) { photoRef.putFile(any(), any()) }
  }

  @Test
  fun photosAddedAtOnceGetDifferentSlots() = runTest {
    val first = launch { repository.addPhoto("r1", HandoverPhase.PICKUP, photoUri) }
    val second = launch { repository.addPhoto("r1", HandoverPhase.PICKUP, photoUri) }
    runCurrent() // both uploads have started and are being waited for

    assertEquals(
        setOf("rentals/r1/photos/u1_PICKUP_0.jpg", "rentals/r1/photos/u1_PICKUP_1.jpg"),
        uploadedPaths.toSet(),
    )
    first.cancel()
    second.cancel()
  }

  @Test
  fun aSlotIsFreeAgainWhenItsUploadFails() = runTest {
    repository.addPhoto("r1", HandoverPhase.PICKUP, photoUri) // times out
    repository.addPhoto("r1", HandoverPhase.PICKUP, photoUri)

    assertEquals(
        List(2) { "rentals/r1/photos/u1_PICKUP_0.jpg" },
        uploadedPaths,
    )
  }

  // ---------- a slot whose file exists without a document ----------

  private val notAuthorized = 403
  private val notFound = 404

  @Test
  fun theErrorCodesUsedByTheTestsAreTheExpectedOnes() {
    assertEquals(StorageException.ERROR_NOT_AUTHORIZED, storageError(notAuthorized).errorCode)
    assertEquals(StorageException.ERROR_OBJECT_NOT_FOUND, storageError(notFound).errorCode)
  }

  @Test
  fun aSlotWhoseFileExistsWithoutADocumentIsSkippedAndTheNextOneIsUsed() = runTest {
    uploadFor = { path ->
      if (path.endsWith("u1_PICKUP_0.jpg")) failedUpload(storageError(notAuthorized)) else upload
    }

    val call = launch { repository.addPhoto("r1", HandoverPhase.PICKUP, photoUri) }
    runCurrent() // slot 0 was refused, its file exists, slot 1 is being uploaded

    assertEquals(
        listOf("rentals/r1/photos/u1_PICKUP_0.jpg", "rentals/r1/photos/u1_PICKUP_1.jpg"),
        uploadedPaths,
    )
    call.cancel()
  }

  @Test
  fun aBurnedSlotIsNotTriedAgainByTheNextCall() = runTest {
    uploadFor = { path ->
      if (path.endsWith("u1_PICKUP_0.jpg")) failedUpload(storageError(notAuthorized)) else upload
    }
    val first = launch { repository.addPhoto("r1", HandoverPhase.PICKUP, photoUri) }
    runCurrent()
    first.cancel()
    first.join()

    val second = launch { repository.addPhoto("r1", HandoverPhase.PICKUP, photoUri) }
    runCurrent()

    // Slot 0 once, then slot 1 twice (the first call was cancelled, which freed it).
    assertEquals(
        listOf(
            "rentals/r1/photos/u1_PICKUP_0.jpg",
            "rentals/r1/photos/u1_PICKUP_1.jpg",
            "rentals/r1/photos/u1_PICKUP_1.jpg",
        ),
        uploadedPaths,
    )
    second.cancel()
  }

  @Test
  fun aRefusedUploadWhoseFileDoesNotExistKeepsTheOriginalFailureAndBurnsNothing() = runTest {
    val refused = storageError(notAuthorized)
    uploadFor = { failedUpload(refused) }
    metadata = Tasks.forException(storageError(notFound))

    val result = repository.addPhoto("r1", HandoverPhase.PICKUP, photoUri)

    assertEquals(refused, result.exceptionOrNull())
    assertEquals(listOf("rentals/r1/photos/u1_PICKUP_0.jpg"), uploadedPaths)

    // Nothing was burned: the next call starts from slot 0 again.
    repository.addPhoto("r1", HandoverPhase.PICKUP, photoUri)
    assertEquals(
        List(2) { "rentals/r1/photos/u1_PICKUP_0.jpg" },
        uploadedPaths,
    )
  }

  @Test
  fun aMetadataReadThatFailsKeepsTheOriginalFailure() = runTest {
    val refused = storageError(notAuthorized)
    uploadFor = { failedUpload(refused) }
    metadata = Tasks.forException(storageError(503))

    val result = repository.addPhoto("r1", HandoverPhase.PICKUP, photoUri)

    assertEquals(refused, result.exceptionOrNull())
    assertEquals(1, uploadedPaths.size)
  }

  @Test
  fun anErrorOtherThanNotAuthorizedIsNeverTreatedAsAnExistingFile() = runTest {
    val failure = storageError(503)
    uploadFor = { failedUpload(failure) }

    val result = repository.addPhoto("r1", HandoverPhase.PICKUP, photoUri)

    assertEquals(failure, result.exceptionOrNull())
    assertEquals(1, uploadedPaths.size)
    verify(exactly = 0) { photoRef.metadata }
  }

  @Test
  fun whenEverySlotIsBurnedTheAllPhotosTakenFailureIsKept() = runTest {
    uploadFor = { failedUpload(storageError(notAuthorized)) }

    val result = repository.addPhoto("r1", HandoverPhase.PICKUP, photoUri)

    assertTrue(result.exceptionOrNull() is IllegalStateException)
    assertEquals(HandoverPhoto.MAX_PHOTOS, uploadedPaths.toSet().size)
    assertEquals(HandoverPhoto.MAX_PHOTOS, uploadedPaths.size)
  }
}
