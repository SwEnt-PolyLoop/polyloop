// Made with Claude.

package com.swent.polyloop.model.handover

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.storageMetadata
import com.swent.polyloop.model.RepositoryProvider
import com.swent.polyloop.model.firestore.enumOrNull
import java.io.File
import java.util.Date
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Sends one condition photo saved by the upload queue, once the phone is online, even after the app
 * was closed: its file to Cloud Storage, then its document to Firestore, at the slot and with the
 * time chosen when it was taken. Sending it again is safe: a photo whose document exists is done,
 * and a file already in Storage is not uploaded twice.
 *
 * It succeeds once the document is written, asks to be retried when the connection failed, and
 * fails with [REFUSED] in its output when the server refused the photo (e.g. it arrived more than a
 * day after it was taken), as sending it again would be refused too. The phone's copy of the file
 * is deleted unless it is retried.
 */
class HandoverPhotoWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    val uid = Firebase.auth.currentUser?.uid
    val rentalId = inputData.getString(RENTAL_ID)
    val slotId = inputData.getString(SLOT_ID)
    val phase = enumOrNull<HandoverPhase>(inputData.getString(PHASE))
    val takenAt = inputData.getLong(TAKEN_AT, -1)
    val file = inputData.getString(FILE)?.let(::File)
    // Nothing to send, or nobody signed in to send it as: sending it again would not help.
    if (uid == null || rentalId == null || slotId == null || phase == null || takenAt < 0) {
      return refused(file)
    }
    if (file == null || !file.exists()) return refused(file)
    val document =
        Firebase.firestore
            .collection(HandoverFields.RENTALS)
            .document(rentalId)
            .collection(HandoverFields.PHOTOS)
            .document(slotId)
    return try {
      if (!document.existsOnServer()) {
        val ref = RepositoryProvider.storage.reference.child("${document.path}.jpg")
        if (!ref.exists()) {
          ref.putFile(Uri.fromFile(file), storageMetadata { contentType = "image/jpeg" }).await()
        }
        val photo =
            mapOf(
                HandoverFields.TAKEN_BY to uid,
                HandoverFields.PHASE to phase.name,
                HandoverFields.TAKEN_AT to Timestamp(Date(takenAt)),
                HandoverFields.URL to ref.downloadUrl.await().toString(),
            )
        document.set(photo).await()
      }
      file.delete()
      Result.success()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      when {
        !isRefusal(e) -> Result.retry()
        // A write left queued by an earlier run that was stopped may have created it meanwhile.
        document.existsOnServer() -> Result.success().also { file.delete() }
        else -> refused(file)
      }
    }
  }

  private fun refused(file: File?): Result {
    file?.delete()
    return Result.failure(workDataOf(REFUSED to true))
  }

  private suspend fun DocumentReference.existsOnServer() = get(Source.SERVER).await().exists()

  private suspend fun StorageReference.exists(): Boolean =
      try {
        metadata.await()
        true
      } catch (e: StorageException) {
        if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw e
        false
      }

  private fun isRefusal(e: Exception) =
      (e is FirebaseFirestoreException &&
          e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) ||
          (e is StorageException && e.errorCode == StorageException.ERROR_NOT_AUTHORIZED)

  companion object {
    /** The input: which photo of which rental, when it was taken (epoch ms), and its file. */
    const val RENTAL_ID = "rentalId"
    const val PHASE = "phase"
    const val SLOT_ID = "slotId"
    const val TAKEN_AT = "takenAt"
    const val FILE = "file"

    /** In the output of a failure: the server refused the photo. */
    const val REFUSED = "refused"
  }
}
