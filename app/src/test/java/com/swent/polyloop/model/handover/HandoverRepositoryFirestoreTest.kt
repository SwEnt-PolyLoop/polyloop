// Made with Claude.

package com.swent.polyloop.model.handover

import android.net.Uri
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.storage.FirebaseStorage
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
    every { photoRef.putFile(any(), any()) } returns upload
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
}
