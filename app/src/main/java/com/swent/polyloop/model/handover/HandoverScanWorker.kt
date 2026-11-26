// Made with Claude.

package com.swent.polyloop.model.handover

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.swent.polyloop.model.RepositoryProvider
import com.swent.polyloop.model.firestore.enumOrNull
import java.time.Instant

/**
 * Sends one QR scan the phone checked offline, once the phone is online, even after the app was
 * closed: the code and when it was scanned go to the server through
 * [HandoverRepository.confirmScan], which accepts a pickup up to the end of the day after the
 * pickup day. Sending it again is safe: a handover already confirmed changes nothing.
 *
 * It succeeds once the server confirms the scan, asks to be retried when it could not be sent (no
 * answer, nobody signed in yet), and fails with the [REFUSAL] in its output when the server refused
 * it, as sending it again would be refused too.
 */
class HandoverScanWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    val rentalId = inputData.getString(RENTAL_ID)
    val phase = enumOrNull<HandoverPhase>(inputData.getString(PHASE))
    val code = inputData.getString(CODE)
    val scannedAt = inputData.getLong(SCANNED_AT, -1)
    // Nothing to send: sending it again would not help.
    if (rentalId == null || phase == null || code == null || scannedAt < 0) {
      return Result.failure(workDataOf(REFUSAL to REFUSED))
    }
    val error =
        RepositoryProvider.handoverRepository
            .confirmScan(rentalId, phase, code, Instant.ofEpochMilli(scannedAt))
            .exceptionOrNull() ?: return Result.success()
    val refusal = refusalName(error) ?: return Result.retry()
    return Result.failure(workDataOf(REFUSAL to refusal))
  }

  companion object {
    /** The input: which handover of which rental, the code scanned and when (epoch ms). */
    const val RENTAL_ID = "rentalId"
    const val PHASE = "phase"
    const val CODE = "code"
    const val SCANNED_AT = "scannedAt"

    /** In the output of a failure: why the server refused the scan, see [refusalName]. */
    const val REFUSAL = "refusal"

    private const val WRONG_CODE = "WRONG_CODE"
    private const val RENTAL_CHANGED = "RENTAL_CHANGED"
    private const val REFUSED = "REFUSED"

    /** The name kept for a refusal [error] of `confirmScan`, or null if sending again may work. */
    fun refusalName(error: Throwable): String? =
        when (error) {
          is WrongCodeException -> WRONG_CODE
          is RentalChangedException -> RENTAL_CHANGED
          is ScanRefusedException -> REFUSED
          else -> null
        }

    /** The error of a refusal kept as [name] by [refusalName], or null if it is not one. */
    fun refusalError(name: String?): Exception? =
        when (name) {
          WRONG_CODE -> WrongCodeException()
          RENTAL_CHANGED -> RentalChangedException()
          REFUSED -> ScanRefusedException()
          else -> null
        }
  }
}
