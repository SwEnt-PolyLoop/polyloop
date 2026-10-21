// Made with Claude.

package com.swent.polyloop.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swent.polyloop.R
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.components.ListingPhoto
import com.swent.polyloop.ui.theme.BlushRed
import com.swent.polyloop.ui.theme.BookedBlue
import com.swent.polyloop.ui.theme.BookedBlueLight
import com.swent.polyloop.ui.theme.ErrorRed
import com.swent.polyloop.ui.theme.LoanGreen
import com.swent.polyloop.ui.theme.LoanGreenLight
import com.swent.polyloop.ui.theme.RequestAmber
import com.swent.polyloop.ui.theme.RequestAmberLight
import com.swent.polyloop.ui.theme.Sand
import com.swent.polyloop.ui.theme.Stone

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
      Text(
          text = stringResource(R.string.profile_listing_price, listing.pricePerDay),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    StatusPill(state = myListing.rentalState, listingId = listing.id)
  }
}

/** Where a listing stands, as a small coloured label. */
@Composable
private fun StatusPill(state: ListingRentalState, listingId: String) {
  val (label, text, background) =
      when (state) {
        ListingRentalState.AVAILABLE ->
            Triple(R.string.profile_listing_status_available, Stone, Sand)
        ListingRentalState.REQUESTED ->
            Triple(R.string.profile_listing_status_requested, RequestAmber, RequestAmberLight)
        ListingRentalState.BOOKED ->
            Triple(R.string.profile_listing_status_booked, BookedBlue, BookedBlueLight)
        ListingRentalState.ON_LOAN ->
            Triple(R.string.profile_listing_status_on_loan, LoanGreen, LoanGreenLight)
        ListingRentalState.OVERDUE ->
            Triple(R.string.profile_listing_status_overdue, ErrorRed, BlushRed)
      }
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
