// Made with Claude.

package com.swent.polyloop.ui.profile

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
 * The "My listings" card of the own profile: the title with the number of listings, then a spinner
 * while they load, the error with "Retry", an empty text, or one row per listing.
 *
 * @param hasError true when the listings could not be loaded; the card then shows a generic text.
 */
@Composable
fun MyListingsCard(
    listings: List<MyListing>,
    isLoading: Boolean,
    hasError: Boolean,
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
            text = stringResource(R.string.profile_my_listings),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).testTag(C.Tag.profile_listings_title),
        )
        if (!isLoading && !hasError && listings.isNotEmpty()) {
          Text(
              text = listings.size.toString(),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.testTag(C.Tag.profile_listings_count),
          )
        }
      }
      when {
        isLoading ->
            CircularProgressIndicator(
                modifier =
                    Modifier.align(Alignment.CenterHorizontally)
                        .size(24.dp)
                        .testTag(C.Tag.profile_listings_loading)
            )
        hasError -> ListingsError(onRetry = onRetry)
        listings.isEmpty() ->
            Text(
                text = stringResource(R.string.profile_no_listings),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(C.Tag.profile_listings_empty),
            )
        else ->
            listings.forEach {
              key(it.listing.id) { MyListingRow(it, onClick = { onListingClick(it.listing.id) }) }
            }
      }
    }
  }
}

@Composable
private fun ListingsError(onRetry: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
        text = stringResource(R.string.profile_listings_error),
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

/** One listing: its first photo, title and price, and where it stands. Tapping it opens it. */
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
    // A rental still under way matters more than the draft, but "Available" would be wrong.
    if (
        listing.status == ListingStatus.DRAFT &&
            myListing.rentalState == ListingRentalState.AVAILABLE
    ) {
      DraftPill(listingId = listing.id)
    } else {
      StatusPill(state = myListing.rentalState, listingId = listing.id)
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
