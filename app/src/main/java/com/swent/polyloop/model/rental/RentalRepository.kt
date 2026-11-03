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
   * The lender accepts the requested rental [rentalId] with the agreed [deposit] (a whole number of
   * PP above 0) and the exact [pickupAddress] and [returnAddress]. The server books the rental's
   * dates on the listing and declines the other requests whose dates overlap (buffer day included).
   * Done by the `acceptRental` Cloud Function: the app writes nothing itself. Fails with the cause
   * when the server refuses (not requested, dates booked meanwhile, not the lender) or cannot be
   * reached.
   */
  suspend fun acceptRental(
      rentalId: String,
      deposit: Int,
      pickupAddress: String,
      returnAddress: String,
  ): Result<Unit>

  /**
   * The lender declines the requested rental [rentalId], through the `declineRental` Cloud
   * Function. Fails with the cause when the server refuses or cannot be reached.
   */
  suspend fun declineRental(rentalId: String): Result<Unit>
}
