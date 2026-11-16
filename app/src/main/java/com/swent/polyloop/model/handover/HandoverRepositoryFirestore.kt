// Made with Claude.

package com.swent.polyloop.model.handover

import androidx.core.net.toUri
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storageMetadata
import com.swent.polyloop.model.RepositoryProvider
import com.swent.polyloop.model.firestore.confirmWrite
import com.swent.polyloop.model.firestore.isValidDocumentId
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
 * photo's slot, see [photoSlotId].
 */
class HandoverRepositoryFirestore(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val auth: FirebaseAuth = Firebase.auth,
    storageProvider: () -> FirebaseStorage = { RepositoryProvider.storage },
) : HandoverRepository {

  // Lazy, so creating the repository does not touch Storage; only addPhoto does.
  private val storage by lazy(storageProvider)

  // One lock per rental, so that two photos added at once never pick the same slot.
  private val slotLocks = ConcurrentHashMap<String, Mutex>()

  // The slots chosen by an addPhoto call that has not written its document yet, per rental.
  private val reservedIds = ConcurrentHashMap<String, MutableSet<String>>()

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
      val slotId =
          reserveSlot(rentalId, uid, phase)
              ?: return@withFirebaseErrors Result.failure(
                  IllegalStateException(
                      "All ${HandoverPhoto.MAX_PHOTOS} ${phase.name} photos are already taken"
                  )
              )
      try {
        upload(rentalId, uid, phase, slotId, photoUri)
      } finally {
        // The document is written, so the next call sees the slot in the snapshot, or the upload
        // failed and the slot is free again.
        reservedIds[rentalId]?.remove(slotId)
      }
    }
  }

  /**
   * Picks the lowest slot of the user that is neither in the local snapshot of their photos, which
   * includes writes still waiting to be sent, nor chosen by a call still uploading. Null when all
   * are used.
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
            lowestFreeSlot(uid, phase, existing + reserved)
                ?.let { photoSlotId(uid, phase, it) }
                ?.also { reserved.add(it) }
          }

  private suspend fun upload(
      rentalId: String,
      uid: String,
      phase: HandoverPhase,
      slotId: String,
      photoUri: String,
  ): Result<Unit> {
    val document = photos(rentalId).document(slotId)
    val ref = storage.reference.child("${document.path}.jpg")
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

  private fun photos(rentalId: String) =
      db.collection(HandoverFields.RENTALS).document(rentalId).collection(HandoverFields.PHOTOS)

  private companion object {
    const val UPLOAD_TIMEOUT_MS = 30_000L
  }
}
