// Made with Claude.

package com.swent.polyloop.model.handover

import androidx.core.net.toUri
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.swent.polyloop.model.RepositoryProvider
import com.swent.polyloop.model.firestore.FUNCTIONS_REGION
import com.swent.polyloop.model.firestore.callCloudFunction
import com.swent.polyloop.model.firestore.isValidDocumentId
import com.swent.polyloop.model.firestore.refusalReason
import com.swent.polyloop.model.firestore.snapshotsAsFlow
import com.swent.polyloop.model.firestore.withFirebaseErrors
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [HandoverRepository] backed by Firebase: each photo's file is in Cloud Storage and its document
 * in Firestore, both at `rentals/{rentalId}/photos/{photoId}` (the file with `.jpg`). The id is the
 * photo's slot, see [photoSlotId]. The code to show is at
 * `rentals/{rentalId}/handoverCodes/{phase}`, readable only by the person who shows it; the
 * fingerprint of the code to scan is on the rental. A scan goes to the Cloud Function
 * `confirmHandover`, the only one that moves the rental on.
 *
 * A photo is not uploaded here: [addPhoto] picks its slot and hands it to the
 * [HandoverUploadQueue], whose [HandoverPhotoWorker] sends it once the phone is online.
 *
 * @param functions gives the Cloud Functions client. A provider, not an instance, so that Firebase
 *   is only touched when a Function is called.
 * @param uploadQueueProvider gives the upload queue, a provider so that WorkManager is only touched
 *   when photos are used.
 */
class HandoverRepositoryFirestore(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val functions: () -> FirebaseFunctions = {
      FirebaseFunctions.getInstance(FUNCTIONS_REGION)
    },
    uploadQueueProvider: () -> HandoverUploadQueue = { RepositoryProvider.handoverUploadQueue },
) : HandoverRepository {

  private val uploadQueue by lazy(uploadQueueProvider)

  // One lock per rental, so that two photos added at once never pick the same slot.
  private val slotLocks = ConcurrentHashMap<String, Mutex>()

  /** The user's own photos of [phase], the query behind [observeMyPhotos] and the slots. */
  private fun myPhotos(rentalId: String, uid: String, phase: HandoverPhase) =
      photos(rentalId)
          .whereEqualTo(HandoverFields.TAKEN_BY, uid)
          .whereEqualTo(HandoverFields.PHASE, phase.name)

  override fun observeMyPhotos(rentalId: String, phase: HandoverPhase): Flow<List<HandoverPhoto>> {
    val uid = auth.currentUser?.uid ?: return flow { throw IllegalStateException("Signed out") }
    if (!isValidDocumentId(rentalId)) return flowOf(emptyList())
    // Sorted here: sorting on a third field in Firestore would need a composite index.
    val sent =
        myPhotos(rentalId, uid, phase).snapshotsAsFlow { snapshot ->
          snapshot.documents.mapNotNull { doc ->
            doc.getData(ServerTimestampBehavior.ESTIMATE)?.let { photoFromDocument(doc.id, it) }
          }
        }
    return combine(sent, uploadQueue.observe(rentalId, phase)) { sentPhotos, queued ->
          val sentIds = sentPhotos.map { it.id }.toSet()
          val waiting =
              queued
                  .filter { !it.refused && it.slotId !in sentIds }
                  .map { HandoverPhoto(it.slotId, phase, it.takenAt, it.file.toUri().toString()) }
          (sentPhotos + waiting).sortedBy { it.takenAt }
        }
        .distinctUntilChanged()
  }

  override suspend fun addPhoto(
      rentalId: String,
      phase: HandoverPhase,
      photoUri: String,
      takenAt: Instant,
  ): Result<Unit> {
    if (!isValidDocumentId(rentalId)) {
      return Result.failure(IllegalArgumentException("Invalid rental id"))
    }
    val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Signed out"))
    return withFirebaseErrors {
      // The lowest slot neither sent (the local snapshot includes writes still waiting to be sent)
      // nor in the queue. Queued inside the lock, so the next call sees it.
      slotLocks
          .getOrPut(rentalId) { Mutex() }
          .withLock {
            val sent =
                myPhotos(rentalId, uid, phase)
                    .snapshotsAsFlow { it.documents.map { d -> d.id } }
                    .first()
            val queued = uploadQueue.observe(rentalId, phase).first().map { it.slotId }
            val slot =
                lowestFreeSlot(uid, phase, sent + queued)
                    ?: return@withLock Result.failure(
                        IllegalStateException(
                            "All ${HandoverPhoto.MAX_PHOTOS} ${phase.name} photos are already taken"
                        )
                    )
            val file =
                photoUri.toUri().path?.let(::File)
                    ?: return@withLock Result.failure(IllegalArgumentException("Not a file"))
            try {
              uploadQueue.enqueue(rentalId, phase, photoSlotId(uid, phase, slot), takenAt, file)
              Result.success(Unit)
            } catch (e: IOException) {
              Result.failure(e) // the photo could not be copied, e.g. the phone is full
            }
          }
    }
  }

  // A plain get() asks the server and, offline, gives the copy the phone keeps from the last read.
  override suspend fun getCodeToShow(rentalId: String, phase: HandoverPhase): Result<String?> =
      readText(rentalId) {
        rental(rentalId)
            .collection(HandoverFields.CODES)
            .document(phase.name)
            .get()
            .await()
            .get(HandoverFields.CODE)
      }

  override suspend fun getCodeFingerprint(
      rentalId: String,
      phase: HandoverPhase,
  ): Result<String?> =
      readText(rentalId) {
        rental(rentalId).get().await().get(HandoverFields.fingerprintField(phase))
      }

  override suspend fun confirmScan(
      rentalId: String,
      phase: HandoverPhase,
      code: String,
  ): Result<Unit> {
    val data = mapOf("rentalId" to rentalId, "phase" to phase.name, "code" to code)
    // Back waits for the answer, and a stalled call would only fail after about 70 s, so stop
    // waiting sooner. Sending it again is safe: a pickup already confirmed changes nothing.
    val result =
        withTimeoutOrNull(SCAN_TIMEOUT_MS) {
          callCloudFunction(functions(), CONFIRM_HANDOVER, data)
        } ?: return Result.failure(TimeoutException("Scan not sent in time"))
    return result.exceptionOrNull()?.let(::scanRefusal)?.let { Result.failure(it) } ?: result
  }

  /**
   * Reads one text field with [read], for a signed-in user and a valid [rentalId]. A missing or
   * blank field, or one that is not text, gives null.
   */
  private suspend fun readText(rentalId: String, read: suspend () -> Any?): Result<String?> {
    if (!isValidDocumentId(rentalId)) {
      return Result.failure(IllegalArgumentException("Invalid rental id"))
    }
    if (auth.currentUser == null) return Result.failure(IllegalStateException("Signed out"))
    return withFirebaseErrors { Result.success((read() as? String)?.takeIf { it.isNotBlank() }) }
  }

  /**
   * The exception the app expects when [error] is a refusal by the server: its own exception for a
   * known reason, [ScanRefusedException] for any other refusal, or null when the scan did not get
   * an answer (e.g. the connection dropped), which sending it again may fix.
   */
  private fun scanRefusal(error: Throwable): Exception? =
      when (refusalReason(error)) {
        WRONG_CODE -> WrongCodeException()
        RENTAL_CHANGED -> RentalChangedException()
        else ->
            ScanRefusedException().takeIf {
              (error as? FirebaseFunctionsException)?.code in REFUSAL_CODES
            }
      }

  private fun rental(rentalId: String) = db.collection(HandoverFields.RENTALS).document(rentalId)

  /** Always fails for now: the Cloud Function that releases the deposit does not exist yet. */
  override suspend fun confirmReturnOk(rentalId: String): Result<Unit> =
      Result.failure(UnsupportedOperationException("Confirming a return is not available yet"))

  private fun photos(rentalId: String) =
      db.collection(HandoverFields.RENTALS).document(rentalId).collection(HandoverFields.PHOTOS)

  private companion object {
    const val SCAN_TIMEOUT_MS = 30_000L
    const val CONFIRM_HANDOVER = "confirmHandover"

    /** The reasons of a refusal by `confirmHandover` (see [refusalReason]). */
    const val WRONG_CODE = "WRONG_CODE"
    const val RENTAL_CHANGED = "RENTAL_CHANGED"

    /** The codes of a refusal by `confirmHandover`'s checks, which sending again would not fix. */
    val REFUSAL_CODES =
        setOf(
            FirebaseFunctionsException.Code.FAILED_PRECONDITION,
            FirebaseFunctionsException.Code.PERMISSION_DENIED,
            FirebaseFunctionsException.Code.NOT_FOUND,
            FirebaseFunctionsException.Code.INVALID_ARGUMENT,
            FirebaseFunctionsException.Code.UNAUTHENTICATED,
        )
  }
}
