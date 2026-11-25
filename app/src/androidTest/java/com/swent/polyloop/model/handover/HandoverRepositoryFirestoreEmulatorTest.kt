// Made with Claude.

package com.swent.polyloop.model.handover

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.swent.polyloop.utils.FirebaseEmulator
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
    // A test WorkManager, whose jobs run only when a test says so (sendQueuedPhotos).
    WorkManagerTestInitHelper.initializeTestWorkManager(
        context,
        Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
    )
    repository = HandoverRepositoryFirestore(uploadQueueProvider = { HandoverUploadQueue(context) })
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

  /** Runs every queued photo's job, as WorkManager does once the phone is online. */
  private suspend fun sendQueuedPhotos() {
    val driver = WorkManagerTestInitHelper.getTestDriver(context)!!
    WorkManager.getInstance(context)
        .getWorkInfosByTagFlow(HandoverPhotoWorker::class.java.name)
        .first()
        .forEach { driver.setAllConstraintsMet(it.id) }
  }

  /**
   * Adds a pickup photo to [rentalId], taken 5 minutes ago: the rules refuse a photo from the
   * future by the emulator's clock, and the device's may run a little ahead of it.
   */
  private suspend fun addPhoto(rentalId: String, photoUri: String = photoFile()) =
      repository.addPhoto(rentalId, HandoverPhase.PICKUP, photoUri, Instant.now().minusSeconds(300))

  @Test
  fun anAddedPhotoShowsAtOnceAndThenWithItsUrlOnceTheQueueSentIt() =
      runBlocking<Unit> {
        val rentalId = seedRental()

        assertTrue(addPhoto(rentalId).isSuccess)

        val photos = repository.observeMyPhotos(rentalId, HandoverPhase.PICKUP)
        // Waiting in the queue: shown from the phone's copy.
        assertTrue(photos.first().single().url.startsWith("file:"))
        // Sent by the queue's job, once the phone is online.
        sendQueuedPhotos()
        withTimeout(20_000) { photos.first { it.singleOrNull()?.url?.startsWith("http") == true } }
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
  fun anInvalidIdOrNobodySignedInFails() = runBlocking {
    val invalid = addPhoto("a/b")
    assertTrue(invalid.exceptionOrNull() is IllegalArgumentException)
    assertEquals(
        emptyList<HandoverPhoto>(),
        repository.observeMyPhotos("", HandoverPhase.PICKUP).first(),
    )

    Firebase.auth.signOut()
    val signedOut = addPhoto(seedRental())
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

    assertTrue(addPhoto(rentalId).isSuccess)
    assertTrue(addPhoto(rentalId).isSuccess)

    assertEquals(listOf("${uid}_PICKUP_0", "${uid}_PICKUP_1"), myPhotoIds(rentalId))
  }

  @Test
  fun photosWithRandomIdsUseNoSlot() = runBlocking {
    val rentalId = seedRental()
    seedPhoto(rentalId, uid, "PICKUP", seconds = 1_000) // an old photo, with a random id

    assertTrue(addPhoto(rentalId).isSuccess)

    assertTrue("${uid}_PICKUP_0" in myPhotoIds(rentalId))
  }

  @Test
  fun withAllSlotsUsedAddPhotoFailsAndAddsNothing() = runBlocking {
    val rentalId = seedRental()
    repeat(HandoverPhoto.MAX_PHOTOS) {
      seedPhoto(rentalId, uid, "PICKUP", seconds = it.toLong(), id = "${uid}_PICKUP_$it")
    }

    val result = addPhoto(rentalId)

    assertTrue(result.exceptionOrNull() is IllegalStateException)
    assertEquals(HandoverPhoto.MAX_PHOTOS, myPhotoIds(rentalId).size)
  }

  @Test
  fun photosAddedAtOnceGetDifferentSlots() = runBlocking {
    val rentalId = seedRental()

    val results = List(3) { async { addPhoto(rentalId) } }.awaitAll()

    assertTrue(results.all { it.isSuccess })
    assertEquals(List(3) { "${uid}_PICKUP_$it" }, myPhotoIds(rentalId))
  }

  @Test
  fun confirmReturnOkFailsUntilItsFunctionExists() = runBlocking {
    assertTrue(repository.confirmReturnOk("r1").exceptionOrNull() is UnsupportedOperationException)
  }
}
