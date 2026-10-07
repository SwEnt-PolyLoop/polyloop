// Made with Claude.

package com.swent.polyloop.ui.listingdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swent.polyloop.R
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.components.PriceText
import com.swent.polyloop.ui.navigation.PolyLoopTopBar
import com.swent.polyloop.ui.navigation.Screen
import com.swent.polyloop.ui.theme.PolyLoopTheme
import java.time.LocalDate

// Placeholder until the owner's profile comes from ProfileRepository.
internal const val FAKE_LENDER_RATING = 4.8
internal const val FAKE_LENDER_REVIEW_COUNT = 12

private const val STAR_COUNT = 5

/**
 * The Listing detail screen for a borrower: photos, title, price, description, the lender, the
 * suggested deposit and availability, the pickup area, and a pinned "Request dates" button. The
 * app's top bar is drawn by the navigation scaffold, not here.
 */
@Composable
fun ListingDetailScreen(
    listing: Listing,
    onRequestDates: () -> Unit,
    onLenderClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .testTag(C.Tag.listing_detail_screen)
  ) {
    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      PhotoPager(photoCount = listing.photoUrls.size)
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = listing.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(C.Tag.listing_detail_title),
        )
        PriceText(
            pricePerDay = listing.pricePerDay,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag(C.Tag.listing_detail_price),
        )
      }
      Text(
          text = listing.description,
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.testTag(C.Tag.listing_detail_description),
      )
      LenderCard(onClick = onLenderClick)
      InfoCard(listing = listing)
      PickupArea()
    }
    RequestBar(onRequestDates = onRequestDates)
  }
}

/** Swipeable grey photo placeholders with a "current / total" badge. */
@Composable
private fun PhotoPager(photoCount: Int) {
  // One grey page even without photos, so the area keeps its size.
  val pagerState = rememberPagerState { maxOf(photoCount, 1) }
  Box(modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(MaterialTheme.shapes.large)) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize().testTag(C.Tag.listing_detail_photos),
    ) {
      // Placeholder until listing photos can be loaded
      Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
    }
    if (photoCount > 0) {
      Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.surface,
          modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
      ) {
        Text(
            text =
                stringResource(
                    R.string.listing_detail_photo_count,
                    pagerState.currentPage + 1,
                    photoCount,
                ),
            style = MaterialTheme.typography.labelMedium,
            modifier =
                Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    .testTag(C.Tag.listing_detail_photo_badge),
        )
      }
    }
  }
}

/** The lender with a placeholder name and rating; the whole card opens their profile. */
@Composable
private fun LenderCard(onClick: () -> Unit) {
  val name = stringResource(R.string.listing_detail_lender_placeholder)
  OutlinedCard(
      onClick = onClick,
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.listing_detail_lender_card),
  ) {
    Row(
        modifier = Modifier.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
          modifier =
              Modifier.size(40.dp)
                  .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
          contentAlignment = Alignment.Center,
      ) {
        Text(
            text = name.firstOrNull()?.uppercase().orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(C.Tag.listing_detail_lender_avatar),
        )
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(C.Tag.listing_detail_lender_name),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          repeat(STAR_COUNT) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(14.dp),
            )
          }
          Spacer(Modifier.width(4.dp))
          Text(
              text =
                  pluralStringResource(
                      R.plurals.listing_detail_rating,
                      FAKE_LENDER_REVIEW_COUNT,
                      FAKE_LENDER_RATING,
                      FAKE_LENDER_REVIEW_COUNT,
                  ),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.testTag(C.Tag.listing_detail_lender_rating),
          )
        }
      }
      Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
  }
}

/** Suggested deposit, availability window, and a note that the deposit is agreed in chat. */
@Composable
private fun InfoCard(listing: Listing) {
  val locale = LocalConfiguration.current.locales[0]
  val (from, to) = formatRangeEnds(listing.availableFrom, listing.availableTo, locale)
  OutlinedCard(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      InfoRow(
          label = stringResource(R.string.listing_detail_suggested_deposit),
          value = stringResource(R.string.listing_card_price, listing.itemValue),
          valueTag = C.Tag.listing_detail_deposit,
      )
      InfoRow(
          label = stringResource(R.string.listing_detail_available),
          value = stringResource(R.string.listing_detail_date_range, from, to),
          valueTag = C.Tag.listing_detail_availability,
      )
      Text(
          text = stringResource(R.string.listing_detail_deposit_note),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag(C.Tag.listing_detail_deposit_note),
      )
    }
  }
}

@Composable
private fun InfoRow(label: String, value: String, valueTag: String) {
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
    )
    Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.testTag(valueTag),
    )
  }
}

/** Map placeholder: a translucent circle over the pickup area and a caption chip. */
@Composable
private fun PickupArea() {
  val circleColor = MaterialTheme.colorScheme.primary
  Box(
      modifier =
          Modifier.fillMaxWidth()
              .height(120.dp)
              .clip(MaterialTheme.shapes.large)
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .testTag(C.Tag.listing_detail_pickup_area)
  ) {
    Box(
        modifier =
            Modifier.align(Alignment.Center)
                .size(88.dp)
                .background(circleColor.copy(alpha = 0.2f), CircleShape)
                .border(1.dp, circleColor, CircleShape)
    )
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
    ) {
      Text(
          text = stringResource(R.string.listing_detail_pickup_caption),
          style = MaterialTheme.typography.labelSmall,
          modifier =
              Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                  .testTag(C.Tag.listing_detail_pickup_caption),
      )
    }
  }
}

/** The pinned bottom bar with the full-width "Request dates" button. */
@Composable
private fun RequestBar(onRequestDates: () -> Unit) {
  Surface(color = MaterialTheme.colorScheme.surface) {
    Column {
      HorizontalDivider()
      Button(
          onClick = onRequestDates,
          shape = MaterialTheme.shapes.medium,
          modifier =
              Modifier.fillMaxWidth()
                  .padding(16.dp)
                  .height(48.dp)
                  .testTag(C.Tag.listing_detail_request_button),
      ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(R.string.listing_detail_request_dates))
      }
    }
  }
}

private val previewListing =
    Listing(
        id = "sample-tent",
        ownerId = "sample-owner",
        title = "Camping tent, 2 people",
        description = "Light 2-person tent, used three times. Comes with pegs and a rain cover.",
        category = ListingCategory.SPORTS_OUTDOOR,
        photoUrls = List(5) { "" },
        pricePerDay = 15,
        itemValue = 180,
        availableFrom = LocalDate.of(2026, 10, 6),
        availableTo = LocalDate.of(2026, 10, 20),
        status = ListingStatus.PUBLISHED,
        pickupArea = "Ecublens",
    )

@Preview(showBackground = true)
@Composable
private fun ListingDetailScreenPreview() {
  PolyLoopTheme {
    Column {
      PolyLoopTopBar(
          destinations = Screen.topLevelDestinations(isAdmin = false),
          onDestinationClick = {},
          onProfileClick = {},
      )
      ListingDetailScreen(listing = previewListing, onRequestDates = {}, onLenderClick = {})
    }
  }
}
