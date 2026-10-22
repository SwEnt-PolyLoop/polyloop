// Made with Claude.

package com.swent.polyloop.ui.profile

import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalStatus

/**
 * Where one of the user's listings stands right now, from its rentals. Declared from the least to
 * the most important: when several rentals apply, the most important one wins.
 *
 * @property rentalStatus the rental status that gives this state; null for [AVAILABLE].
 */
enum class ListingRentalState(val rentalStatus: RentalStatus?) {
  /** No rental waiting, booked or under way. */
  AVAILABLE(null),
  /** A request waits for the lender's answer. */
  REQUESTED(RentalStatus.REQUESTED),
  /** A request was accepted; the item has not been handed over yet. */
  BOOKED(RentalStatus.ACCEPTED),
  /** The item is with a borrower. */
  ON_LOAN(RentalStatus.ACTIVE),
  /** The item is with a borrower and the return date has passed. */
  OVERDUE(RentalStatus.OVERDUE),
  /** A dispute about a rental is open. */
  DISPUTED(RentalStatus.DISPUTED),
  /** A dispute went to court: the deposit is frozen until an admin applies the ruling. */
  FROZEN(RentalStatus.FROZEN),
  /** A borrower never gave the item back. */
  LOST(RentalStatus.LOST),
}

/** One of the signed-in user's listings, with where it stands right now. */
data class MyListing(val listing: Listing, val rentalState: ListingRentalState)

/**
 * The state of the listing [listingId], from the most important of its [rentals]. Rentals of other
 * listings, and finished or refused ones, are ignored.
 */
fun rentalStateOf(listingId: String, rentals: List<Rental>): ListingRentalState =
    rentals
        .filter { it.listingId == listingId }
        .mapNotNull { rental ->
          ListingRentalState.entries.find { it.rentalStatus == rental.status }
        }
        .maxOrNull() ?: ListingRentalState.AVAILABLE
