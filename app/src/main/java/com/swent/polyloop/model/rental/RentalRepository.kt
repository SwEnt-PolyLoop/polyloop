// Made with Claude.

package com.swent.polyloop.model.rental

import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.listing.Listing
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Rentals the signed-in user takes part in, as borrower or as lender. */
interface RentalRepository {

  /**
   * Sends a request from [borrower] to rent [listing] from [startDate] to [endDate], both days
   * included, and returns the new rental, with status [RentalStatus.REQUESTED]. Copies the
   * listing's title, price and public areas, the borrower's name, and [ownerName], which the caller
   * has from the owner's profile (a blank name is stored as ""; the screens show a placeholder).
   *
   * Temporary: the app writes the rental itself, without reserving any money, until the
   * `requestRental` Cloud Function exists. The caller checks the dates first (inside the
   * availability window, not on booked days). Fails with an [IllegalArgumentException] if [endDate]
   * is before [startDate], or with the cause if the rental cannot be written.
   */
  suspend fun requestRental(
      listing: Listing,
      ownerName: String,
      borrower: AuthUser,
      startDate: LocalDate,
      endDate: LocalDate,
  ): Result<Rental>

  /**
   * The rentals in which [uid] borrows, newest first, emitted again every time one changes. A read
   * error is thrown to the collector, which handles it with `catch`.
   */
  fun observeRentalsAsBorrower(uid: String): Flow<List<Rental>>

  /**
   * The rentals in which [uid] lends, newest first, emitted again every time one changes. A read
   * error is thrown to the collector, which handles it with `catch`.
   */
  fun observeRentalsAsLender(uid: String): Flow<List<Rental>>

  /**
   * The rental with this [id], read once, or null if there is none. Fails with the cause if it
   * cannot be read.
   */
  suspend fun getRental(id: String): Result<Rental?>

  /**
   * The lender accepts the [RentalStatus.REQUESTED] rental [id], with the exact [pickupAddress] and
   * [returnAddress], and returns it with status [RentalStatus.ACCEPTED]. Fails if the rental is not
   * requested any more, or with the cause if it cannot be written.
   *
   * Temporary: the app writes the status itself, like [requestRental]. Declining the other
   * overlapping requests and booking the dates on the listing are not done here yet.
   */
  suspend fun acceptRental(id: String, pickupAddress: String, returnAddress: String): Result<Rental>

  /**
   * The lender declines the [RentalStatus.REQUESTED] rental [id] and returns it with status
   * [RentalStatus.DECLINED]. Fails if the rental is not requested any more, or with the cause if it
   * cannot be written.
   */
  suspend fun declineRental(id: String): Result<Rental>
}
