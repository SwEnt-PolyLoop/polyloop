// Made with Claude.

package com.swent.polyloop.model.handover

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
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
  // 5 minutes ago: the rules refuse a photo from the future by the emulator's clock, and the
  // device's may run a little ahead of it.
  private val takenAt = System.currentTimeMillis() - 5 * 60_000
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

  private suspend fun runWorker(
      rental: String = rentalId,
      takenBy: String = uid,
      db: FirebaseFirestore = Firebase.firestore,
  ): Result =
      TestListenableWorkerBuilder<HandoverPhotoWorker>(context)
          .setWorkerFactory(
              object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ) =
                    HandoverPhotoWorker(appContext, workerParameters, Firebase.auth, db) {
                      Firebase.storage
                    }
              }
          )
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
    upload(tag = takenAt.toString())

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
  fun aFileLeftAtTheSlotByAnotherPhotoIsNeverSavedAsThisOne() = runBlocking {
    // E.g. a photo whose document the rules refused (too late): its file stays in Storage.
    upload(tag = (takenAt - 86_400_000).toString())

    assertEquals(Result.failure(workDataOf(HandoverPhotoWorker.REFUSED to true)), runWorker())
    assertFalse(document.get(Source.SERVER).await().exists())
  }

  /** Puts a file at this photo's slot in Storage, tagged with the time [tag]. */
  private suspend fun upload(tag: String) {
    val metadata = storageMetadata {
      contentType = "image/jpeg"
      setCustomMetadata(HandoverPhotoWorker.TAKEN_AT, tag)
    }
    Firebase.storage.reference.child("${document.path}.jpg").putBytes(BYTES, metadata).await()
  }

  @Test
  fun aPhotoThatCannotReachTheServerIsRetriedAndKept() = runBlocking {
    // A Firestore of its own app, as useEmulator works only before an instance is used, on a port
    // of the device itself where nothing listens. (Storage would keep retrying for 20 minutes.)
    val app = FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, "unreachable")
    try {
      val unreachable = Firebase.firestore(app).apply { useEmulator("127.0.0.1", 9) }

      assertEquals(Result.retry(), runWorker(db = unreachable))
      assertTrue(file.exists())
    } finally {
      app.delete()
    }
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
