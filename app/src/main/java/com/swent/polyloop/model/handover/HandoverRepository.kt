// Made with Claude.

package com.swent.polyloop.model.handover

import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** The condition photos of rentals, taken with the in-app camera. */
interface HandoverRepository {

  /**
   * The photos the signed-in user took of the rental [rentalId] in [phase], oldest first, including
   * those still waiting in the upload queue (their url is then the phone's file) and those the
   * server refused, each marked by its [HandoverPhoto.sync]. Emits again when one is added. The
   * flow fails with an [IllegalStateException] if nobody is signed in, or with the cause if they
   * cannot be loaded.
   */
  fun observeMyPhotos(rentalId: String, phase: HandoverPhase): Flow<List<HandoverPhoto>>

  /**
   * Adds the photo file at [photoUri], taken at [takenAt] by the server's clock, as the signed-in
   * user's photo of [rentalId] in [phase]. It goes into the local upload queue, which sends it once
   * the phone is online, and shows in [observeMyPhotos] from then on; the caller may delete the
   * file. Fails with an [IllegalArgumentException] if [rentalId] is invalid, an
   * [IllegalStateException] if nobody is signed in or all the user's photos of [phase] are taken,
   * or the cause if it cannot be queued. The server may still refuse it when it is sent.
   */
  suspend fun addPhoto(
      rentalId: String,
      phase: HandoverPhase,
      photoUri: String,
      takenAt: Instant,
  ): Result<Unit>

  /**
   * The secret code the signed-in user shows as a QR code in [phase] of [rentalId]: the lender at
   * pickup, the borrower at return. Null if there is none (not this user's turn, or not created
   * yet). Offline, it is read from the phone's copy. Fails if nobody is signed in or it cannot be
   * read.
   */
  suspend fun getCodeToShow(rentalId: String, phase: HandoverPhase): Result<String?>

  /**
   * The fingerprint of the code the signed-in user scans in [phase] of [rentalId] (see
   * [HandoverCode]), or null if there is none. Offline, it is read from the phone's copy. Fails if
   * nobody is signed in or it cannot be read.
   */
  suspend fun getCodeFingerprint(rentalId: String, phase: HandoverPhase): Result<String?>

  /**
   * Sends the [code] the signed-in user scanned in [phase] of [rentalId]; the server checks it and
   * moves the rental on. [scannedAt] is when a scan made offline was made (by `ServerClock`), null
   * for a scan sent at once. Succeeds if it was already done. Fails with [WrongCodeException] if
   * the code is not this rental's, with [RentalChangedException] if the rental can no longer take
   * this scan (e.g. cancelled), with [ScanRefusedException] if the server refuses it for another
   * reason that sending it again would not fix, or with the cause if nobody is signed in or it
   * cannot be sent (e.g. the connection dropped, or no answer came within 30 seconds: a
   * TimeoutException).
   */
  suspend fun confirmScan(
      rentalId: String,
      phase: HandoverPhase,
      code: String,
      scannedAt: Instant? = null,
  ): Result<Unit>

  /**
   * Puts the [code] the signed-in user scanned offline in [phase] of [rentalId], at [scannedAt] (by
   * `ServerClock`), into the local upload queue, which sends it through [confirmScan] once the
   * phone is online, even after the app was closed. Queuing it again while one waits changes
   * nothing. Fails if nobody is signed in or [rentalId] is invalid.
   */
  suspend fun queueScan(
      rentalId: String,
      phase: HandoverPhase,
      code: String,
      scannedAt: Instant,
  ): Result<Unit>

  /**
   * The scan of [phase] of [rentalId] waiting in the upload queue, or the last one the server
   * refused lately (about a day), with its refusal; null when there is none, e.g. once it is sent.
   */
  fun observeQueuedScan(rentalId: String, phase: HandoverPhase): Flow<QueuedScan?>

  /**
   * Tells, as the signed-in lender, that the item of the returned rental [rentalId] came back fine:
   * the deposit, less any late fees, goes back to the borrower and the rental closes. Succeeds if
   * the rental was already closed by this answer. Fails with an [IllegalArgumentException] if
   * [rentalId] is invalid, with an [IllegalStateException] if nobody is signed in, or with the
   * cause if the user is not the lender, the rental is no longer Returned (e.g. damage was flagged
   * first), or it cannot be sent.
   */
  suspend fun confirmReturnOk(rentalId: String): Result<Unit>
}

/** Thrown when the scanned code is not the one of this rental and phase. */
class WrongCodeException : IllegalArgumentException("This is not the code of this handover")

/** Thrown when the rental can no longer take the scan, e.g. because it was cancelled meanwhile. */
class RentalChangedException : IllegalStateException("The rental changed")

/**
 * Thrown when the server refuses the scan for a reason it does not name, e.g. it is not the pickup
 * day by the server's clock or the borrower cannot pay the rental price: sending it again as it is
 * would be refused again.
 */
class ScanRefusedException : IllegalStateException("The server refused the scan")
