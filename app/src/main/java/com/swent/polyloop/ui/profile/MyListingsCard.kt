// Made with Claude.

package com.swent.polyloop.ui.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swent.polyloop.R
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.components.ListingPhoto
import com.swent.polyloop.ui.components.PriceText
import com.swent.polyloop.ui.components.badgeColors

/**
 * The listings card of a profile, "My listings" on the own profile and "Listings" on someone
 * else's: the title with the number of listings, then a spinner while they load, the error with
 * "Retry", an empty text, or one row per listing; after a failed refresh, a note under the empty
 * text or the rows says so.
 *
 * @param isOwnProfile true on the signed-in user's own profile, false on someone else's; it picks
 *   the title and the texts.
 * @param ownerName the name of the profile's user, in the empty text of someone else's profile.
 * @param hasError true when the listings could not be loaded; the card then shows a generic text.
 * @param isStale true when the last refresh failed; a note under the listings, or under the empty
 *   text, then says they could not be refreshed.
 * @param isOffline true when the listings are unavailable because the phone is offline; the card
 *   then shows only a text saying so.
 */
@Composable
fun MyListingsCard(
    listings: List<MyListing>,
    isOwnProfile: Boolean,
    ownerName: String,
    isLoading: Boolean,
    hasError: Boolean,
    isStale: Boolean,
    isOffline: Boolean,
    onListingClick: (listingId: String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
  OutlinedCard(modifier = modifier.fillMaxWidth().testTag(C.Tag.profile_listings_card)) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text =
                stringResource(
                    if (isOwnProfile) R.string.profile_my_listings
                    else R.string.profile_other_listings
                ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).testTag(C.Tag.profile_listings_title),
        )
        if (!isOffline && !isLoading && !hasError && listings.isNotEmpty()) {
          Text(
              text = listings.size.toString(),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.testTag(C.Tag.profile_listings_count),
          )
        }
      }
      when {
        isOffline ->
            Text(
                text = stringResource(R.string.profile_other_listings_offline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(C.Tag.profile_listings_offline),
            )
        isLoading ->
            CircularProgressIndicator(
                modifier =
                    Modifier.align(Alignment.CenterHorizontally)
                        .size(24.dp)
                        .testTag(C.Tag.profile_listings_loading)
            )
        hasError ->
            ListingsError(
                message =
                    stringResource(
                        if (isOwnProfile) R.string.profile_listings_error
                        else R.string.profile_other_listings_error
                    ),
                onRetry = onRetry,
            )
        listings.isEmpty() ->
            Text(
                text =
                    when {
                      isOwnProfile -> stringResource(R.string.profile_no_listings)
                      ownerName.isBlank() ->
                          stringResource(R.string.profile_other_no_listings_unnamed)
                      else -> stringResource(R.string.profile_other_no_listings, ownerName)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(C.Tag.profile_listings_empty),
            )
        else ->
            listings.forEach {
              key(it.listing.id) { MyListingRow(it, onClick = { onListingClick(it.listing.id) }) }
            }
      }
      if (isStale) {
        Text(
            text = stringResource(staleNote(isOwnProfile, hasListings = listings.isNotEmpty())),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(C.Tag.profile_listings_stale),
        )
      }
    }
  }
}

/**
 * The note after a failed refresh. It mentions the listings loaded before only when there are some:
 * under an empty text, "the last ones loaded" would contradict it.
 */
@StringRes
private fun staleNote(isOwnProfile: Boolean, hasListings: Boolean): Int =
    when {
      isOwnProfile && hasListings -> R.string.profile_listings_stale
      isOwnProfile -> R.string.profile_listings_stale_empty
      hasListings -> R.string.profile_other_listings_stale
      else -> R.string.profile_other_listings_stale_empty
    }

@Composable
private fun ListingsError(message: String, onRetry: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.weight(1f).testTag(C.Tag.profile_listings_error),
    )
    TextButton(
        onClick = onRetry,
        modifier = Modifier.testTag(C.Tag.profile_listings_retry_button),
    ) {
      Text(stringResource(R.string.profile_retry))
    }
  }
}

/**
 * One listing: its first photo, title and price, and where it stands unless that is not known (on
 * someone else's profile). Tapping it opens it.
 */
@Composable
private fun MyListingRow(myListing: MyListing, onClick: () -> Unit) {
  val listing = myListing.listing
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .clickable(onClick = onClick)
              .padding(vertical = 6.dp)
              .testTag(C.Tag.profile_listing_ + listing.id),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    ListingPhoto(
        url = listing.photoUrls.firstOrNull(),
        contentDescription = null,
        modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.medium),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
          text = listing.title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
      PriceText(pricePerDay = listing.pricePerDay, style = MaterialTheme.typography.bodyMedium)
    }
    val state = myListing.rentalState
    when {
      // Someone else's listing: its rentals are not read, so no pill.
      state == null -> Unit
      // A rental still under way matters more than the draft, but "Available" would be wrong.
      listing.status == ListingStatus.DRAFT && state == ListingRentalState.AVAILABLE ->
          DraftPill(listingId = listing.id)
      else -> StatusPill(state = state, listingId = listing.id)
    }
  }
}

/** Marks a listing its owner unpublished: plain, without a colour, as it is out of Browse. */
@Composable
private fun DraftPill(listingId: String) {
  Text(
      text = stringResource(R.string.profile_listing_status_draft),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier =
          Modifier.background(MaterialTheme.colorScheme.surface, CircleShape)
              .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
              .padding(horizontal = 10.dp, vertical = 4.dp)
              .testTag(C.Tag.profile_listing_draft_ + listingId),
  )
}

/**
 * Where a listing stands, as a small coloured label, in the colours of My rentals; "Available"
 * looks like a finished rental.
 */
@Composable
private fun StatusPill(state: ListingRentalState, listingId: String) {
  val label =
      when (state) {
        ListingRentalState.AVAILABLE -> R.string.profile_listing_status_available
        ListingRentalState.REQUESTED -> R.string.rental_status_requested
        ListingRentalState.BOOKED -> R.string.profile_listing_status_booked
        ListingRentalState.ON_LOAN -> R.string.profile_listing_status_on_loan
        ListingRentalState.OVERDUE -> R.string.rental_status_overdue
        ListingRentalState.DISPUTED -> R.string.rental_status_disputed
        ListingRentalState.FROZEN -> R.string.rental_status_frozen
        ListingRentalState.LOST -> R.string.rental_status_lost
      }
  val (background, text) = (state.rentalStatus ?: RentalStatus.CLOSED).badgeColors()
  Text(
      text = stringResource(label),
      style = MaterialTheme.typography.labelMedium,
      color = text,
      modifier =
          Modifier.background(background, CircleShape)
              .padding(horizontal = 10.dp, vertical = 4.dp)
              .testTag(C.Tag.profile_listing_status_ + listingId),
  )
}
