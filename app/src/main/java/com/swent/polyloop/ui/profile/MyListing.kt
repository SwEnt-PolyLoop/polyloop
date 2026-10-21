// Made with Claude.

package com.swent.polyloop.ui.profile

import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalStatus

/**
 * Where one of the user's listings stands right now, from its rentals. Declared from the least to
 * the most important: when several rentals apply, the most important one wins.
 */
enum class ListingRentalState {
  /** No rental waiting, booked or under way. */
  AVAILABLE,
  /** A request waits for the lender's answer. */
  REQUESTED,
  /** A request was accepted; the item has not been handed over yet. */
  BOOKED,
  /** The item is with a borrower. */
  ON_LOAN,
  /** The item is with a borrower and the return date has passed. */
  OVERDUE,
}

/** One of the signed-in user's listings, with where it stands right now. */
data class MyListing(val listing: Listing, val rentalState: ListingRentalState)

/**
 * The state of the listing [listingId], from the most important of its [rentals]. Rentals of other
 * listings, and finished or refused ones, are ignored.
 */
fun rentalStateOf(listingId: String, rentals: List<Rental>): ListingRentalState =
    rentals.filter { it.listingId == listingId }.mapNotNull { stateOf(it.status) }.maxOrNull()
        ?: ListingRentalState.AVAILABLE

private fun stateOf(status: RentalStatus): ListingRentalState? =
    when (status) {
      RentalStatus.REQUESTED -> ListingRentalState.REQUESTED
      RentalStatus.ACCEPTED -> ListingRentalState.BOOKED
      RentalStatus.ACTIVE -> ListingRentalState.ON_LOAN
      RentalStatus.OVERDUE -> ListingRentalState.OVERDUE
      else -> null
    }
