// Made with Claude.

package com.swent.polyloop.model.listing

import android.net.Uri

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
   * Uploads [photos] in order, then saves [listing] with their URLs and its exact [addresses], and
   * returns the new listing's id. The listing's id and photoUrls are ignored; its ownerId and
   * status are saved as given.
   *
   * Fails with an IllegalArgumentException, before anything is uploaded, if the listing cannot be
   * published (see [Listing.canPublish]). Fails with the cause if a photo cannot be uploaded or the
   * listing cannot be saved; nothing is saved then, but photos already uploaded stay in Storage.
   */
  suspend fun createListing(
      listing: Listing,
      addresses: ListingAddresses,
      photos: List<Uri>,
  ): Result<String>
}
