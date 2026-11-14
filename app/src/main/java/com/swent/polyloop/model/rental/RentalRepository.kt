// Made with Claude.

package com.swent.polyloop.model.rental

import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.listing.Listing
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Rentals the signed-in user takes part in, as borrower or as lender. */
interface RentalRepository {

  /**
   * Sends a request from [borrower] to rent [listing] from [startDate] to [endDate], both days
   * included, and returns the new rental, with status [RentalStatus.REQUESTED]. The rental holds
   * the listing's title, price and public areas, the borrower's name, and the owner's name (a blank
   * name is stored as ""; the screens show a placeholder). The Firestore implementation sends only
   * the listing's id and the days to the `requestRental` Cloud Function, which copies the rest from
   * the listing and the profiles; [ownerName] and the borrower's name are used by fakes.
   *
   * No money is reserved yet. The caller checks the dates first (inside the availability window,
   * not on booked days). Fails with an [IllegalArgumentException] if [endDate] is before
   * [startDate], a [RentalDatesUnavailableException] if the days were booked meanwhile, a
   * [ListingUnavailableException] if the listing was deleted or unpublished meanwhile, or with the
   * cause if the request cannot be sent.
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
   * The rental with this [id], emitted again every time it changes, or null if there is none. A
   * read error is thrown to the collector, which handles it with `catch`.
   */
  fun observeRental(id: String): Flow<Rental?>

  /**
   * Sends the lender's offer on a request with no offer yet. The borrower sees the addresses only
   * once the rental is accepted.
   *
   * @param rentalId the request to answer.
   * @param deposit the deposit offered, in PP.
   * @param pickupAddress the exact pickup address.
   * @param returnAddress the exact drop-off address.
   * @return a failure with the cause if the server refuses or cannot be reached.
   */
  suspend fun makeOffer(
      rentalId: String,
      deposit: Int,
      pickupAddress: String,
      returnAddress: String,
  ): Result<Unit>

  /**
   * Accepts the offer waiting on a rental, which the other participant wrote. The rental becomes
   * accepted, and the other requests whose dates overlap are declined.
   *
   * @param rentalId the rental whose offer is accepted.
   * @return a failure with the cause if the server refuses or cannot be reached.
   */
  suspend fun acceptOffer(rentalId: String): Result<Unit>

  /**
   * Declines the offer waiting on a rental, which the other participant wrote. The rental becomes
   * declined.
   *
   * @param rentalId the rental whose offer is declined.
   * @return a failure with the cause if the server refuses or cannot be reached.
   */
  suspend fun declineOffer(rentalId: String): Result<Unit>

  /**
   * Answers the lender's offer with the borrower's single counteroffer.
   *
   * @param rentalId the rental whose offer is countered.
   * @param deposit the deposit proposed instead, in PP.
   * @return a failure with the cause if the server refuses or cannot be reached.
   */
  suspend fun counterOffer(rentalId: String, deposit: Int): Result<Unit>

  /**
   * Declines a request with no offer yet.
   *
   * @param rentalId the request to decline.
   * @return a failure with the cause if the server refuses or cannot be reached.
   */
  suspend fun declineRequest(rentalId: String): Result<Unit>

  /**
   * The server's current time, from the `serverToday` Cloud Function. Start Pickup opens from it
   * rather than from the phone's clock, which the user can change in the phone's settings; the
   * server's cannot be changed from the phone.
   *
   * @return a failure with the cause if the server refuses, cannot be reached, or gives no time.
   */
  suspend fun serverNow(): Result<Instant>
}

/** Thrown when a request asks for days that were booked since the borrower picked them. */
class RentalDatesUnavailableException : IllegalStateException("These dates are no longer available")

/** Thrown when a request is sent for a listing that was deleted or unpublished meanwhile. */
class ListingUnavailableException : IllegalStateException("This listing is no longer available")
