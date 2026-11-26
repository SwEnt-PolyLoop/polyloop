// Made with Claude.

package com.swent.polyloop.model.handover

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A condition photo waiting in the upload queue, or refused by the server ([refused]). */
data class QueuedPhoto(
    val slotId: String,
    val takenAt: Instant,
    val file: File,
    val refused: Boolean,
)

/**
 * The local upload queue for condition photos and QR scans (see docs/ARCHITECTURE.md): each photo
 * is copied to its own folder in the app's files, so the camera's cleanup never deletes it, and
 * sent by a [HandoverPhotoWorker] once the phone is online, even after the app was closed or the
 * phone restarted; a scan is sent the same way by a [HandoverScanWorker]. What waits is read from
 * WorkManager itself, with no other storage.
 */
class HandoverUploadQueue(
    context: Context,
    private val workManager: WorkManager = WorkManager.getInstance(context),
) {
  private val folder = File(context.filesDir, FOLDER)

  /**
   * Copies the photo at [photo] and queues it as [slotId] of [rentalId] in [phase], taken by [uid]
   * at [takenAt]. A slot whose job still waits keeps that job and its time, but its file is
   * replaced: callers queue only free slots. Returns once the job is saved, so [observe] lists it
   * from then on; if it cannot be saved, the copy is deleted and the error thrown.
   */
  suspend fun enqueue(
      rentalId: String,
      phase: HandoverPhase,
      uid: String,
      slotId: String,
      takenAt: Instant,
      photo: File,
  ) {
    val copy = queuedFile(rentalId, slotId)
    photo.copyTo(copy.apply { parentFile?.mkdirs() }, overwrite = true)
    val request =
        OneTimeWorkRequestBuilder<HandoverPhotoWorker>()
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .setInputData(
                workDataOf(
                    HandoverPhotoWorker.UID to uid,
                    HandoverPhotoWorker.RENTAL_ID to rentalId,
                    HandoverPhotoWorker.PHASE to phase.name,
                    HandoverPhotoWorker.SLOT_ID to slotId,
                    HandoverPhotoWorker.TAKEN_AT to takenAt.toEpochMilli(),
                    HandoverPhotoWorker.FILE to copy.path,
                )
            )
            // A job's input cannot be read back, so what the screen needs is kept in its tags.
            .addTag(photosTag(rentalId, phase))
            .addTag(SLOT + slotId)
            .addTag(TAKEN_AT + takenAt.toEpochMilli())
            .build()
    try {
      workManager.enqueueUniqueWork("$rentalId/$slotId", ExistingWorkPolicy.KEEP, request).await()
    } catch (e: CancellationException) {
      throw e // the job may be saved already, and needs its copy
    } catch (e: Exception) {
      // No job will send it (e.g. WorkManager's database failed). A copy that cannot be deleted
      // only takes space, and must not hide the error.
      runCatching { Files.deleteIfExists(copy.toPath()) }
      throw e
    }
  }

  /**
   * The photos of [rentalId] in [phase] still waiting to be sent, and those the server refused
   * lately (WorkManager forgets finished jobs after about a day). Sent ones are left out.
   */
  fun observe(rentalId: String, phase: HandoverPhase): Flow<List<QueuedPhoto>> =
      workManager.getWorkInfosByTagFlow(photosTag(rentalId, phase)).map { jobs ->
        jobs.mapNotNull { job -> queuedPhoto(rentalId, job) }.sortedBy { it.takenAt }
      }

  /**
   * Queues the [code] scanned offline in [phase] of [rentalId] at [scannedAt], for a
   * [HandoverScanWorker]. Queuing it again while one waits changes nothing; a refused one is
   * replaced. Returns once the job is saved, so [observeScan] lists it from then on.
   */
  suspend fun enqueueScan(
      rentalId: String,
      phase: HandoverPhase,
      code: String,
      scannedAt: Instant,
  ) {
    val request =
        OneTimeWorkRequestBuilder<HandoverScanWorker>()
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .setInputData(
                workDataOf(
                    HandoverScanWorker.RENTAL_ID to rentalId,
                    HandoverScanWorker.PHASE to phase.name,
                    HandoverScanWorker.CODE to code,
                    HandoverScanWorker.SCANNED_AT to scannedAt.toEpochMilli(),
                )
            )
            .addTag(SCANNED_AT + scannedAt.toEpochMilli())
            .build()
    workManager
        .enqueueUniqueWork(scanName(rentalId, phase), ExistingWorkPolicy.KEEP, request)
        .await()
  }

  /**
   * The scan of [phase] of [rentalId] still waiting to be sent, or the last one the server refused
   * lately (WorkManager forgets finished jobs after about a day), or null if there is none.
   */
  fun observeScan(rentalId: String, phase: HandoverPhase): Flow<QueuedScan?> =
      workManager.getWorkInfosForUniqueWorkFlow(scanName(rentalId, phase)).map { jobs ->
        jobs.firstNotNullOfOrNull(::queuedScan)
      }

  private fun queuedScan(job: WorkInfo): QueuedScan? {
    val refusal =
        HandoverScanWorker.refusalError(job.outputData.getString(HandoverScanWorker.REFUSAL))
    if (job.state.isFinished && refusal == null) return null
    val scannedAt = job.tags.tagValue(SCANNED_AT)?.toLongOrNull() ?: return null
    return QueuedScan(Instant.ofEpochMilli(scannedAt), refusal)
  }

  private fun queuedPhoto(rentalId: String, job: WorkInfo): QueuedPhoto? {
    val refused = job.outputData.getBoolean(HandoverPhotoWorker.REFUSED, false)
    if (job.state.isFinished && !refused) return null
    val slotId = job.tags.tagValue(SLOT) ?: return null
    val takenAt = job.tags.tagValue(TAKEN_AT)?.toLongOrNull() ?: return null
    return QueuedPhoto(slotId, Instant.ofEpochMilli(takenAt), queuedFile(rentalId, slotId), refused)
  }

  private fun Set<String>.tagValue(prefix: String) = firstOrNull {
    it.startsWith(prefix)
  }
      ?.removePrefix(prefix)

  private fun queuedFile(rentalId: String, slotId: String) = File(folder, "$rentalId/$slotId.jpg")

  private fun photosTag(rentalId: String, phase: HandoverPhase) = "handover/$rentalId/${phase.name}"

  // One scan per handover: each person scans once, at pickup or at return.
  private fun scanName(rentalId: String, phase: HandoverPhase) = "scan/$rentalId/${phase.name}"

  private companion object {
    const val FOLDER = "handover_upload_queue"
    const val SLOT = "slot:"
    const val TAKEN_AT = "takenAt:"
    const val SCANNED_AT = "scannedAt:"
  }
}
