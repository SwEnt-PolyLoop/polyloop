// Made with Claude.

package com.swent.polyloop.model.handover

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.storage
import com.google.firebase.storage.storageMetadata
import com.swent.polyloop.utils.FirebaseEmulator
import java.io.File
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real [HandoverRepositoryFirestore] against the Firebase emulators and their rules. */
@RunWith(AndroidJUnit4::class)
class HandoverRepositoryFirestoreEmulatorTest {

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var repository: HandoverRepositoryFirestore
  private lateinit var uid: String

  @Before
  fun setUp() {
    FirebaseEmulator.connect()
    repository = HandoverRepositoryFirestore()
    uid = runBlocking { FirebaseEmulator.signUpVerified() }
  }

  @After
  fun tearDown() {
    Firebase.auth.signOut()
  }

  /** A rental between the signed-in user and someone else, written as an admin. */
  private fun seedRental(status: String = "ACCEPTED") =
      "rental-${UUID.randomUUID()}"
          .also {
            FirebaseEmulator.createDocumentAsAdmin(
                "rentals/$it",
                mapOf("participants" to listOf(uid, "someone"), "status" to status),
            )
          }

  /** A photo document written as an admin, skipping Storage. */
  private fun seedPhoto(
      rentalId: String,
      takenBy: String,
      phase: String,
      seconds: Long,
      id: String = UUID.randomUUID().toString(),
  ) =
      FirebaseEmulator.createDocumentAsAdmin(
          "rentals/$rentalId/photos/$id",
          mapOf(
              "takenBy" to takenBy,
              "phase" to phase,
              "takenAt" to Timestamp(seconds, 0),
              "url" to "https://example.com/$seconds.jpg",
          ),
      )

  /** A small file in the app's cache, as the camera would leave a photo. */
  private fun photoFile(): String {
    val file = File(context.cacheDir, "photo-${UUID.randomUUID()}.jpg")
    file.writeBytes(byteArrayOf(1, 2, 3))
    return Uri.fromFile(file).toString()
  }

  @Test
  fun anAddedPhotoIsUploadedAndObservedWithItsUrl() = runBlocking {
    val rentalId = seedRental()

    assertTrue(repository.addPhoto(rentalId, HandoverPhase.PICKUP, photoFile()).isSuccess)

    val photos =
        withTimeout(10_000) {
          repository.observeMyPhotos(rentalId, HandoverPhase.PICKUP).first { it.isNotEmpty() }
        }
    assertEquals(HandoverPhase.PICKUP, photos.single().phase)
    assertTrue(photos.single().url.startsWith("http"))
  }

  @Test
  fun onlyMyPhotosOfThePhaseAreObservedOldestFirst() = runBlocking {
    val rentalId = seedRental()
    seedPhoto(rentalId, uid, "PICKUP", seconds = 2_000)
    seedPhoto(rentalId, uid, "PICKUP", seconds = 1_000)
    seedPhoto(rentalId, "someone", "PICKUP", seconds = 1_500)
    seedPhoto(rentalId, uid, "RETURN", seconds = 1_200)

    val photos =
        withTimeout(10_000) { repository.observeMyPhotos(rentalId, HandoverPhase.PICKUP).first() }
    assertEquals(
        listOf("https://example.com/1000.jpg", "https://example.com/2000.jpg"),
        photos.map { it.url },
    )
  }

  @Test
  fun addPhotoFailsWhenTheRentalIsNotAccepted() = runBlocking {
    val rentalId = seedRental(status = "REQUESTED")

    assertTrue(repository.addPhoto(rentalId, HandoverPhase.PICKUP, photoFile()).isFailure)
  }

  @Test
  fun anInvalidIdOrNobodySignedInFails() = runBlocking {
    val invalid = repository.addPhoto("a/b", HandoverPhase.PICKUP, photoFile())
    assertTrue(invalid.exceptionOrNull() is IllegalArgumentException)
    assertEquals(
        emptyList<HandoverPhoto>(),
        repository.observeMyPhotos("", HandoverPhase.PICKUP).first(),
    )

    Firebase.auth.signOut()
    val signedOut = repository.addPhoto(seedRental(), HandoverPhase.PICKUP, photoFile())
    assertTrue(signedOut.exceptionOrNull() is IllegalStateException)
    val flowError = runCatching { repository.observeMyPhotos("r1", HandoverPhase.PICKUP).first() }
    assertTrue(flowError.exceptionOrNull() is IllegalStateException)
  }

  private fun myPhotoIds(rentalId: String) = runBlocking {
    withTimeout(10_000) {
      repository.observeMyPhotos(rentalId, HandoverPhase.PICKUP).first().map { it.id }.sorted()
    }
  }

  @Test
  fun addedPhotosTakeTheLowestFreeSlots() = runBlocking {
    val rentalId = seedRental()

    assertTrue(repository.addPhoto(rentalId, HandoverPhase.PICKUP, photoFile()).isSuccess)
    assertTrue(repository.addPhoto(rentalId, HandoverPhase.PICKUP, photoFile()).isSuccess)

    assertEquals(listOf("${uid}_PICKUP_0", "${uid}_PICKUP_1"), myPhotoIds(rentalId))
  }

  @Test
  fun photosWithRandomIdsUseNoSlot() = runBlocking {
    val rentalId = seedRental()
    seedPhoto(rentalId, uid, "PICKUP", seconds = 1_000) // an old photo, with a random id

    assertTrue(repository.addPhoto(rentalId, HandoverPhase.PICKUP, photoFile()).isSuccess)

    assertTrue("${uid}_PICKUP_0" in myPhotoIds(rentalId))
  }

  @Test
  fun withAllSlotsUsedAddPhotoFailsAndAddsNothing() = runBlocking {
    val rentalId = seedRental()
    repeat(HandoverPhoto.MAX_PHOTOS) {
      seedPhoto(rentalId, uid, "PICKUP", seconds = it.toLong(), id = "${uid}_PICKUP_$it")
    }

    val result = repository.addPhoto(rentalId, HandoverPhase.PICKUP, photoFile())

    assertTrue(result.exceptionOrNull() is IllegalStateException)
    assertEquals(HandoverPhoto.MAX_PHOTOS, myPhotoIds(rentalId).size)
  }

  @Test
  fun photosAddedAtOnceGetDifferentSlots() = runBlocking {
    val rentalId = seedRental()

    val results =
        List(3) { async { repository.addPhoto(rentalId, HandoverPhase.PICKUP, photoFile()) } }
            .awaitAll()

    assertTrue(results.all { it.isSuccess })
    assertEquals(List(3) { "${uid}_PICKUP_$it" }, myPhotoIds(rentalId))
  }

  @Test
  fun aPhotoWhoseWriteIsStillWaitingToBeSentKeepsItsSlot() = runBlocking {
    val rentalId = seedRental()
    Firebase.firestore.disableNetwork().await()
    try {
      // Uploaded, but the write is not confirmed, so it stays queued on the phone.
      assertTrue(repository.addPhoto(rentalId, HandoverPhase.PICKUP, photoFile()).isSuccess)
      assertTrue(repository.addPhoto(rentalId, HandoverPhase.PICKUP, photoFile()).isSuccess)

      assertEquals(listOf("${uid}_PICKUP_0", "${uid}_PICKUP_1"), myPhotoIds(rentalId))
    } finally {
      Firebase.firestore.enableNetwork().await()
    }
  }

  /** A file in the user's slot [slot] with no document, as an upload whose document was lost. */
  private fun orphanFile(rentalId: String, slot: Int) = runBlocking {
    Firebase.storage.reference
        .child("rentals/$rentalId/photos/${uid}_PICKUP_$slot.jpg")
        .putFile(Uri.parse(photoFile()), storageMetadata { contentType = "image/jpeg" })
        .await()
  }

  @Test
  fun aSlotWhoseFileExistsWithoutADocumentIsSkipped() = runBlocking {
    val rentalId = seedRental()
    orphanFile(rentalId, slot = 0)

    assertTrue(repository.addPhoto(rentalId, HandoverPhase.PICKUP, photoFile()).isSuccess)
    assertEquals(listOf("${uid}_PICKUP_1"), myPhotoIds(rentalId))

    // Slot 0 is set aside for the rest of the session, so the next photo goes to slot 2.
    assertTrue(repository.addPhoto(rentalId, HandoverPhase.PICKUP, photoFile()).isSuccess)
    assertEquals(listOf("${uid}_PICKUP_1", "${uid}_PICKUP_2"), myPhotoIds(rentalId))
  }

  @Test
  fun aReallyNotAuthorizedUploadFailsWithoutBurningAnySlot() = runBlocking {
    val rentalId = seedRental(status = "REQUESTED")

    val refused = repository.addPhoto(rentalId, HandoverPhase.PICKUP, photoFile())

    assertEquals(
        StorageException.ERROR_NOT_AUTHORIZED,
        (refused.exceptionOrNull() as? StorageException)?.errorCode,
    )
    // Once the rental is accepted, the first slot is still free: nothing was burned.
    FirebaseEmulator.updateDocumentAsAdmin("rentals/$rentalId", mapOf("status" to "ACCEPTED"))
    assertTrue(repository.addPhoto(rentalId, HandoverPhase.PICKUP, photoFile()).isSuccess)
    assertEquals(listOf("${uid}_PICKUP_0"), myPhotoIds(rentalId))
  }
}
