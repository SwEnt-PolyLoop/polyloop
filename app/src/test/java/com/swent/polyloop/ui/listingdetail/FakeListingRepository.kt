// Made with Claude.

package com.swent.polyloop.ui.listingdetail

import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingRepository
import com.swent.polyloop.model.listing.ListingStatus
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory [ListingRepository] for tests. Holds [listings], or fails every call with [failure]
 * when it is set. When [gate] is set, calls wait for it to complete, so tests can see the loading
 * state.
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
}
