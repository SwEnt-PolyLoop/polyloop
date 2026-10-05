// Made with Claude.

package com.swent.polyloop.model.listing

/** Listings that borrowers can browse. */
interface ListingRepository {
  /**
   * All published listings, including the current user's own (the ViewModel hides those). Fails
   * with the cause if they cannot be loaded.
   */
  suspend fun getAllListings(): Result<List<Listing>>
}
