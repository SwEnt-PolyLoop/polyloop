// Made with Claude.

package com.swent.polyloop.model.handover

import androidx.core.net.toUri
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.storageMetadata
import com.swent.polyloop.model.RepositoryProvider
import com.swent.polyloop.model.firestore.FUNCTIONS_REGION
import com.swent.polyloop.model.firestore.callCloudFunction
import com.swent.polyloop.model.firestore.confirmWrite
import com.swent.polyloop.model.firestore.isValidDocumentId
import com.swent.polyloop.model.firestore.refusalReason
import com.swent.polyloop.model.firestore.snapshotsAsFlow
import com.swent.polyloop.model.firestore.withFirebaseErrors
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
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
 * `confirmHandover`, and "Item is fine" to `confirmReturnOk`: only Cloud Functions move the rental
 * on.
 *
 * A slot whose file exists in Storage without a document (an upload that finished but whose
 * document was never written) can never be written again, as the rules refuse to replace a file:
 * [addPhoto] skips it for the rest of the session and tries the next slot.
 *
 * @param functions gives the Cloud Functions client. A provider, not an instance, so that Firebase
 *   is only touched when a Function is called.
 */
class HandoverRepositoryFirestore(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val functions: () -> FirebaseFunctions = {
      FirebaseFunctions.getInstance(FUNCTIONS_REGION)
    },
    storageProvider: () -> FirebaseStorage = { RepositoryProvider.storage },
) : HandoverRepository {

  // Lazy, so creating the repository does not touch Storage; only addPhoto does.
  private val storage by lazy(storageProvider)

  // One lock per rental, so that two photos added at once never pick the same slot.
  private val slotLocks = ConcurrentHashMap<String, Mutex>()

  // The slots chosen by an addPhoto call that has not written its document yet, per rental.
  private val reservedIds = ConcurrentHashMap<String, MutableSet<String>>()

  // The slots whose file exists in Storage without a document (an upload that finished but whose
  // document was never written), per rental, for the rest of the session. The rules never let a
  // file be replaced, so writing to such a slot again is refused: it is skipped instead.
  private val burnedIds = ConcurrentHashMap<String, MutableSet<String>>()

  /** The user's own photos of [phase], the query behind [observeMyPhotos] and the slots. */
  private fun myPhotos(rentalId: String, uid: String, phase: HandoverPhase) =
      photos(rentalId)
          .whereEqualTo(HandoverFields.TAKEN_BY, uid)
          .whereEqualTo(HandoverFields.PHASE, phase.name)

  override fun observeMyPhotos(rentalId: String, phase: HandoverPhase): Flow<List<HandoverPhoto>> {
    val uid = auth.currentUser?.uid ?: return flow { throw IllegalStateException("Signed out") }
    if (!isValidDocumentId(rentalId)) return flowOf(emptyList())
    // Sorted here: sorting on a third field in Firestore would need a composite index.
    return myPhotos(rentalId, uid, phase)
        .snapshotsAsFlow { snapshot ->
          snapshot.documents
              .mapNotNull { doc ->
                // A photo just added has no server time yet; the phone's clock stands in for it.
                doc.getData(ServerTimestampBehavior.ESTIMATE)?.let { photoFromDocument(doc.id, it) }
              }
              .sortedBy { it.takenAt }
        }
        .distinctUntilChanged()
  }

  override suspend fun addPhoto(
      rentalId: String,
      phase: HandoverPhase,
      photoUri: String,
  ): Result<Unit> {
    if (!isValidDocumentId(rentalId)) {
      return Result.failure(IllegalArgumentException("Invalid rental id"))
    }
    val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Signed out"))
    return withFirebaseErrors {
      // At most one attempt per slot: a slot whose file already exists is burned, the next is
      // tried.
      for (attempt in 0 until HandoverPhoto.MAX_PHOTOS) {
        val slotId = reserveSlot(rentalId, uid, phase) ?: break
        val document = photos(rentalId).document(slotId)
        val ref = storage.reference.child("${document.path}.jpg")
        try {
          return@withFirebaseErrors upload(document, ref, uid, phase, photoUri)
        } catch (e: StorageException) {
          // Only "not authorized" can mean that the file is already there, and only if it is. Any
          // other error, or a metadata read that fails (e.g. offline), is the real failure.
          if (e.errorCode != StorageException.ERROR_NOT_AUTHORIZED || !fileExists(ref)) throw e
          burnedIds.getOrPut(rentalId) { ConcurrentHashMap.newKeySet() }.add(slotId)
        } finally {
          // The document is written, so the next call sees the slot in the snapshot, or the upload
          // failed and the slot is free again (or burned, above, before it is freed).
          reservedIds[rentalId]?.remove(slotId)
        }
      }
      Result.failure(
          IllegalStateException(
              "All ${HandoverPhoto.MAX_PHOTOS} ${phase.name} photos are already taken"
          )
      )
    }
  }

  /**
   * Picks the lowest slot of the user that is neither in the local snapshot of their photos, which
   * includes writes still waiting to be sent, nor chosen by a call still uploading, nor burned (its
   * file exists without a document). Null when all are used.
   */
  private suspend fun reserveSlot(rentalId: String, uid: String, phase: HandoverPhase): String? =
      slotLocks
          .getOrPut(rentalId) { Mutex() }
          .withLock {
            val reserved = reservedIds.getOrPut(rentalId) { ConcurrentHashMap.newKeySet() }
            val existing =
                myPhotos(rentalId, uid, phase)
                    .snapshotsAsFlow { it.documents.map { d -> d.id } }
                    .first()
            lowestFreeSlot(uid, phase, existing + reserved + burnedIds[rentalId].orEmpty())
                ?.let { photoSlotId(uid, phase, it) }
                ?.also { reserved.add(it) }
          }

  /**
   * Whether the file of [ref] exists, from its metadata. False when it is not found, and also when
   * the read fails for another reason or takes more than [METADATA_TIMEOUT_MS] (e.g. offline): the
   * caller then keeps the original failure.
   */
  private suspend fun fileExists(ref: StorageReference): Boolean =
      try {
        withTimeoutOrNull(METADATA_TIMEOUT_MS) { ref.metadata.await() } != null
      } catch (e: StorageException) {
        false
      }

  private suspend fun upload(
      document: DocumentReference,
      ref: StorageReference,
      uid: String,
      phase: HandoverPhase,
      photoUri: String,
  ): Result<Unit> {
    val upload = ref.putFile(photoUri.toUri(), storageMetadata { contentType = "image/jpeg" })
    // Offline, Storage keeps retrying for minutes, so stop waiting after a while.
    val url =
        try {
          withTimeoutOrNull(UPLOAD_TIMEOUT_MS) {
            upload.await()
            ref.downloadUrl.await().toString()
          }
        } catch (e: CancellationException) {
          // The screen was left: stop the upload too, or its file would stay in Storage with no
          // document, and the rules forbid deleting it.
          upload.cancel()
          throw e
        }
    if (url == null) {
      upload.cancel()
      return Result.failure(TimeoutException("Photo not uploaded in time"))
    }
    // Not confirmed in time (offline): the write stays queued and is sent later, as messages.
    confirmWrite {
      document.set(
          mapOf(
              HandoverFields.TAKEN_BY to uid,
              HandoverFields.PHASE to phase.name,
              HandoverFields.TAKEN_AT to FieldValue.serverTimestamp(),
              HandoverFields.URL to url,
          )
      )
    }
    return Result.success(Unit)
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

  /** Calls the Cloud Function `confirmReturnOk`; its refusals are passed on as they come. */
  override suspend fun confirmReturnOk(rentalId: String): Result<Unit> {
    if (!isValidDocumentId(rentalId)) {
      return Result.failure(IllegalArgumentException("Invalid rental id"))
    }
    if (auth.currentUser == null) return Result.failure(IllegalStateException("Signed out"))
    return callCloudFunction(functions(), CONFIRM_RETURN_OK, mapOf("rentalId" to rentalId))
  }

  private fun photos(rentalId: String) =
      db.collection(HandoverFields.RENTALS).document(rentalId).collection(HandoverFields.PHOTOS)

  private companion object {
    const val UPLOAD_TIMEOUT_MS = 30_000L

    /**
     * The server has just answered when the metadata is read, so a long wait only delays the error.
     */
    const val METADATA_TIMEOUT_MS = 10_000L
    const val SCAN_TIMEOUT_MS = 30_000L
    const val CONFIRM_HANDOVER = "confirmHandover"
    const val CONFIRM_RETURN_OK = "confirmReturnOk"

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
