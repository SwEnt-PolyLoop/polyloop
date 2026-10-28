// Made with Claude.

package com.swent.polyloop.ui.createlisting

import android.net.Uri
import com.swent.polyloop.model.listing.BookedRange
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingAddresses
import com.swent.polyloop.model.listing.ListingRepository

/**
 * A [ListingRepository] for Compose tests where any call fails the test. It exists because
 * `FakeListingRepository` lives in `app/src/test`, which `androidTest` cannot see. A test that does
 * publish overrides `createListing` on top of it, e.g. with `object : ListingRepository by
 * UnusedListingRepository()`.
 */
class UnusedListingRepository : ListingRepository {
  override suspend fun getAllListings(): Result<List<Listing>> = error("Not used in this test")

  override suspend fun getListing(id: String): Result<Listing?> = error("Not used in this test")

  override suspend fun createListing(
      listing: Listing,
      addresses: ListingAddresses,
      photos: List<Uri>,
  ): Result<String> = error("Not used in this test")

  override suspend fun getListingsByOwner(ownerId: String): Result<List<Listing>> =
      error("Not used in this test")

  override suspend fun getBookedRanges(id: String): Result<List<BookedRange>?> =
      error("Not used in this test")
}
