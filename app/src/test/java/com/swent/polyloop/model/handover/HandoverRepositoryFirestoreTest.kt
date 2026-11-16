// Made with Claude.

package com.swent.polyloop.model.handover

import android.net.Uri
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
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
    val photoDocument = mockk<DocumentReference> { every { path } returns "rentals/r1/photos/p1" }
    val photos = mockk<CollectionReference> { every { document() } returns photoDocument }
    val rental = mockk<DocumentReference> { every { collection("photos") } returns photos }
    val rentals = mockk<CollectionReference> { every { document("r1") } returns rental }
    val db = mockk<FirebaseFirestore> { every { collection("rentals") } returns rentals }
    val storage = mockk<FirebaseStorage>()
    every { storage.reference.child("rentals/r1/photos/p1.jpg") } returns photoRef
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
}
