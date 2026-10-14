// Made with Claude.

package com.swent.polyloop.model.listing

/** Listings that borrowers can browse. */
interface ListingRepository {
  /**
   * All published listings, including the current user's own (the ViewModel hides those). Fails
   * with the cause if they cannot be loaded.
   */
  suspend fun getAllListings(): Result<List<Listing>>

  /**
   * The listing with this [id], whatever its status. Null if there is no listing with that id
   * (including a blank id or one containing "/") or its document cannot be mapped. Fails with the
   * cause if it cannot be loaded.
   */
  suspend fun getListing(id: String): Result<Listing?>

  /**
   * The listings put up by [ownerId], whatever their status (their owner also sees drafts), in no
   * particular order. A document that cannot be mapped is skipped. Empty for a blank id. Fails with
   * the cause if they cannot be loaded.
   */
  suspend fun getListingsByOwner(ownerId: String): Result<List<Listing>>
}
