// Made with Claude.

package com.swent.polyloop.model.handover

import kotlinx.coroutines.flow.Flow

/** The condition photos of rentals, taken with the in-app camera. */
interface HandoverRepository {

  /**
   * The photos the signed-in user took of the rental [rentalId] in [phase], oldest first. Emits
   * again when one is added. The flow fails with an [IllegalStateException] if nobody is signed in,
   * or with the cause if they cannot be loaded.
   */
  fun observeMyPhotos(rentalId: String, phase: HandoverPhase): Flow<List<HandoverPhoto>>

  /**
   * Uploads the photo file at [photoUri] as the signed-in user's photo of [rentalId] in [phase].
   * Fails with an [IllegalArgumentException] if [rentalId] is invalid, an [IllegalStateException]
   * if nobody is signed in, or the cause if it cannot be saved (e.g. offline, or the rental is not
   * in [phase]'s status: Accepted at pickup, Active or Overdue at return).
   */
  suspend fun addPhoto(rentalId: String, phase: HandoverPhase, photoUri: String): Result<Unit>

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
   * moves the rental on. Succeeds if it was already done. Fails with [WrongCodeException] if the
   * code is not this rental's, with [RentalChangedException] if the rental can no longer take this
   * scan (e.g. cancelled), with [ScanRefusedException] if the server refuses it for another reason
   * that sending it again would not fix, or with the cause if nobody is signed in or it cannot be
   * sent (e.g. the connection dropped, or no answer came within 30 seconds: a TimeoutException).
   */
  suspend fun confirmScan(rentalId: String, phase: HandoverPhase, code: String): Result<Unit>

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
