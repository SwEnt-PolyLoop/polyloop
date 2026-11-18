// Made with Claude.

package com.swent.polyloop.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swent.polyloop.R
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.components.ListingPhoto
import com.swent.polyloop.ui.components.PriceText

/** The most lines a title takes on a card in the Browse list; Listing detail shows it whole. */
const val CARD_TITLE_MAX_LINES = 2

/**
 * One listing in the Browse list: its first photo, the title, the price per day and the distance
 * with the pickup area. A long title is cut after [CARD_TITLE_MAX_LINES] lines with "…". With
 * [singleLineText], the title and the location are cut to one line, so every card has the same
 * height, as in the row of cards under the Browse map.
 *
 * @param listing the listing shown.
 * @param distanceText the distance from the user, already formatted.
 * @param modifier applied to the card.
 * @param singleLineText true to cut the title and the location to one line each.
 * @param onClick called when the card is tapped.
 */
@Composable
fun ListingCard(
    listing: Listing,
    distanceText: String,
    modifier: Modifier = Modifier,
    singleLineText: Boolean = false,
    onClick: () -> Unit = {},
) {
  val maxLines = if (singleLineText) 1 else Int.MAX_VALUE
  OutlinedCard(
      onClick = onClick,
      modifier = modifier.fillMaxWidth().testTag(C.Tag.listing_card_ + listing.id),
  ) {
    Row(
        modifier = Modifier.padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // No description: the title next to it already says what the item is.
      ListingPhoto(
          url = listing.photoUrls.firstOrNull(),
          contentDescription = null,
          modifier =
              Modifier.size(80.dp)
                  .clip(MaterialTheme.shapes.medium)
                  .testTag(C.Tag.listing_card_photo),
      )
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = listing.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = if (singleLineText) 1 else CARD_TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(C.Tag.listing_card_title),
        )
        PriceText(
            pricePerDay = listing.pricePerDay,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag(C.Tag.listing_card_price),
        )
        Text(
            text =
                if (listing.pickupArea.isBlank()) distanceText
                else
                    stringResource(
                        R.string.listing_card_location,
                        distanceText,
                        listing.pickupArea,
                    ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(C.Tag.listing_card_location),
        )
      }
    }
  }
}
