// Made with Claude.

package com.swent.polyloop.ui.listingdetail

import android.net.Uri
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingAddresses
import com.swent.polyloop.model.listing.ListingRepository
import com.swent.polyloop.model.listing.ListingStatus
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory [ListingRepository] for tests. Holds [listings], including the ones created through it,
 * or fails every call with [failure] when it is set. When [gate] is set, calls wait for it to
 * complete, so tests can see the loading state.
 */
class FakeListingRepository(
    // The listings the fake "stores".
    var listings: List<Listing> = emptyList(),
    // When set, every call fails with this error.
    var failure: Throwable? = null,
    // When set, calls wait until it is completed.
    var gate: CompletableDeferred<Unit>? = null,
) : ListingRepository {

  // How many times getListing was called, e.g. to check that retry loads again.
  var getListingCalls = 0
    private set

  override suspend fun getAllListings(): Result<List<Listing>> {
    gate?.await()
    return failure?.let { Result.failure(it) }
        ?: Result.success(listings.filter { it.status == ListingStatus.PUBLISHED })
  }

  override suspend fun getListing(id: String): Result<Listing?> {
    getListingCalls++
    gate?.await()
    return failure?.let { Result.failure(it) } ?: Result.success(listings.find { it.id == id })
  }

  // The addresses of each created listing, by listing id.
  val createdAddresses = mutableMapOf<String, ListingAddresses>()

  /** Stores the listing with the next "created-N" id and the photo URIs as its URLs. */
  override suspend fun createListing(
      listing: Listing,
      addresses: ListingAddresses,
      photos: List<Uri>,
  ): Result<String> {
    gate?.await()
    failure?.let {
      return Result.failure(it)
    }
    val saved =
        listing.copy(id = "created-${createdAddresses.size}", photoUrls = photos.map(Uri::toString))
    if (!saved.canPublish(addresses)) {
      return Result.failure(IllegalArgumentException("This listing cannot be published"))
    }
    listings = listings + saved
    createdAddresses[saved.id] = addresses
    return Result.success(saved.id)
  }

  override suspend fun getListingsByOwner(ownerId: String): Result<List<Listing>> {
    gate?.await()
    return failure?.let { Result.failure(it) }
        ?: Result.success(listings.filter { it.ownerId == ownerId })
  }
}
