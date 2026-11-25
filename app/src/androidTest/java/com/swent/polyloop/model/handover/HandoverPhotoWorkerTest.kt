// Made with Claude.

package com.swent.polyloop.model.handover

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import com.google.firebase.storage.storageMetadata
import com.swent.polyloop.utils.FirebaseEmulator
import java.io.File
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs [HandoverPhotoWorker] against the Firebase emulators and their rules. */
@RunWith(AndroidJUnit4::class)
class HandoverPhotoWorkerTest {

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var uid: String
  private lateinit var rentalId: String
  private lateinit var file: File
  private val takenAt = System.currentTimeMillis() - 60_000
  private val slotId by lazy { photoSlotId(uid, HandoverPhase.PICKUP, 0) }
  private val document by lazy { Firebase.firestore.document("rentals/$rentalId/photos/$slotId") }

  @Before
  fun setUp() {
    FirebaseEmulator.connect()
    uid = runBlocking { FirebaseEmulator.signUpVerified() }
    rentalId = seedRental("ACCEPTED")
    file = File(context.cacheDir, "queued-${UUID.randomUUID()}.jpg").apply { writeBytes(BYTES) }
  }

  @After
  fun tearDown() {
    file.delete()
    Firebase.auth.signOut()
  }

  private fun seedRental(status: String) =
      "rental-${UUID.randomUUID()}"
          .also {
            FirebaseEmulator.createDocumentAsAdmin(
                "rentals/$it",
                mapOf("participants" to listOf(uid, "someone"), "status" to status),
            )
          }

  private suspend fun runWorker(rental: String = rentalId, takenBy: String = uid): Result =
      TestListenableWorkerBuilder<HandoverPhotoWorker>(context)
          .setInputData(
              workDataOf(
                  HandoverPhotoWorker.UID to takenBy,
                  HandoverPhotoWorker.RENTAL_ID to rental,
                  HandoverPhotoWorker.PHASE to HandoverPhase.PICKUP.name,
                  HandoverPhotoWorker.SLOT_ID to slotId,
                  HandoverPhotoWorker.TAKEN_AT to takenAt,
                  HandoverPhotoWorker.FILE to file.path,
              )
          )
          .build()
          .doWork()

  @Test
  fun aQueuedPhotoIsSentWithTheTimeItWasTakenAndItsFileDeleted() = runBlocking {
    assertEquals(Result.success(), runWorker())

    val sent = document.get(Source.SERVER).await()
    assertEquals(Timestamp(Date(takenAt)), sent.getTimestamp(HandoverFields.TAKEN_AT))
    val stored = Firebase.storage.reference.child("${document.path}.jpg").getBytes(1024).await()
    assertEquals(BYTES.toList(), stored.toList())
    assertFalse(file.exists())
  }

  @Test
  fun sendingAgainAfterAStoppedRunIsSafe() = runBlocking {
    // An earlier run uploaded the file and was stopped before writing the document.
    Firebase.storage.reference
        .child("${document.path}.jpg")
        .putBytes(BYTES, storageMetadata { contentType = "image/jpeg" })
        .await()

    assertEquals(Result.success(), runWorker())
    // Run once more, as WorkManager may: the photo is already there.
    file.writeBytes(BYTES)
    assertEquals(Result.success(), runWorker())
    assertEquals(uid, document.get(Source.SERVER).await().getString(HandoverFields.TAKEN_BY))
  }

  @Test
  fun aPhotoTheServerRefusesIsDroppedAndReportedAsRefused() = runBlocking {
    val refused = Result.failure(workDataOf(HandoverPhotoWorker.REFUSED to true))

    // The pickup is not open: the rental is still a request.
    assertEquals(refused, runWorker(rental = seedRental("REQUESTED")))
    assertFalse(file.exists())
    // Its file is gone: nothing left to send.
    assertEquals(refused, runWorker())
  }

  @Test
  fun aPhotoWaitsWhileItsUserIsSignedOutButNotForAnotherUser() = runBlocking {
    Firebase.auth.signOut()
    assertEquals(Result.retry(), runWorker())
    assertTrue(file.exists())

    // Someone else signs in on the phone: the rules would refuse the photo from them.
    FirebaseEmulator.signUpVerified()
    assertEquals(Result.failure(workDataOf(HandoverPhotoWorker.REFUSED to true)), runWorker())
    assertFalse(file.exists())
  }

  private companion object {
    val BYTES = byteArrayOf(1, 2, 3)
  }
}
