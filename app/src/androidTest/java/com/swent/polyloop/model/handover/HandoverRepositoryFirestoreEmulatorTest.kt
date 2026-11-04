// Made with Claude.

package com.swent.polyloop.model.handover

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.swent.polyloop.utils.FirebaseEmulator
import java.io.File
import java.util.UUID
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
  private fun seedPhoto(rentalId: String, takenBy: String, phase: String, seconds: Long) =
      FirebaseEmulator.createDocumentAsAdmin(
          "rentals/$rentalId/photos/${UUID.randomUUID()}",
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
}
