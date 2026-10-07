// Made with Claude.

package com.swent.polyloop.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swent.polyloop.R
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.theme.PolyLoopTheme
import java.time.LocalDate

// TODO: temporary until LocationRepository exists; every card shows the same distance.
private const val FAKE_DISTANCE_TEXT = "0.6 km"

// TODO: replaced by real data from ListingRepository in #37.
private val sampleListings =
    listOf(
        sampleListing(
            "sample-tent",
            "Camping tent, 2 people",
            ListingCategory.SPORTS_OUTDOOR,
            15,
            200,
            "Ecublens",
        ),
        sampleListing(
            "sample-projector",
            "Projector",
            ListingCategory.ELECTRONICS,
            12,
            300,
            "Renens",
        ),
        sampleListing(
            "sample-drill",
            "Electric drill",
            ListingCategory.TOOLS_DIY,
            8,
            120,
            "Ecublens",
        ),
        sampleListing(
            "sample-helmet",
            "Ski helmet, size M",
            ListingCategory.SPORTS_OUTDOOR,
            5,
            80,
            "Lausanne",
        ),
    )

private fun sampleListing(
    id: String,
    title: String,
    category: ListingCategory,
    pricePerDay: Int,
    itemValue: Int,
    pickupArea: String,
) =
    Listing(
        id = id,
        ownerId = "sample-owner",
        title = title,
        description = "$title in good condition.",
        category = category,
        photoUrls = emptyList(),
        pricePerDay = pricePerDay,
        itemValue = itemValue,
        availableFrom = LocalDate.of(2026, 10, 1),
        availableTo = LocalDate.of(2026, 12, 31),
        status = ListingStatus.PUBLISHED,
        pickupArea = pickupArea,
        dropOffArea = pickupArea,
    )

/** The Browse screen, showing hardcoded sample listings for now. */
@Composable
fun BrowseScreen(onListingClick: (String) -> Unit = {}) {
  BrowseContent(listings = sampleListings, onListingClick = onListingClick)
}

/** Stateless Browse content: the header and the list of [listings]. */
@Composable
fun BrowseContent(listings: List<Listing>, onListingClick: (String) -> Unit) {
  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .testTag(C.Tag.browse_screen)
  ) {
    BrowseTopBar()
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
          text = stringResource(R.string.browse_title),
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
      )
      BrowseSearchBar()
      BrowseCategoryChips()
      BrowseViewToggle()
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().testTag(C.Tag.browse_list),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      items(listings, key = { it.id }) { listing ->
        ListingCard(
            listing = listing,
            distanceText = FAKE_DISTANCE_TEXT,
            onClick = { onListingClick(listing.id) },
        )
      }
    }
  }
}

/** Placeholder top bar: menu, wordmark and profile. The buttons do nothing yet. */
@Composable
private fun BrowseTopBar() {
  Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    TextButton(
        onClick = {},
        modifier = Modifier.testTag(C.Tag.browse_menu_button),
    ) {
      Text(stringResource(R.string.browse_menu))
    }
    Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.weight(1f),
        textAlign = TextAlign.Center,
    )
    TextButton(
        onClick = {},
        modifier = Modifier.testTag(C.Tag.browse_profile_button),
    ) {
      Text(stringResource(R.string.browse_profile))
    }
  }
}

/** Non-functional search bar showing only its hint. */
@Composable
private fun BrowseSearchBar() {
  OutlinedCard(
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.browse_search_bar),
      shape = MaterialTheme.shapes.large,
  ) {
    Text(
        text = stringResource(R.string.browse_search_hint),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
    )
  }
}

/** Static row of category chips with "All" shown as selected. */
@Composable
private fun BrowseCategoryChips() {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .testTag(C.Tag.browse_category_chips),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    CategoryChip(label = stringResource(R.string.browse_category_all), selected = true)
    ListingCategory.entries.forEach { CategoryChip(label = it.label, selected = false) }
  }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean) {
  Surface(
      shape = CircleShape,
      color =
          if (selected) MaterialTheme.colorScheme.primaryContainer
          else MaterialTheme.colorScheme.surfaceVariant,
      contentColor =
          if (selected) MaterialTheme.colorScheme.onPrimaryContainer
          else MaterialTheme.colorScheme.onSurfaceVariant,
  ) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
  }
}

/** Map | List toggle with List selected. Pressing Map does nothing yet. */
@Composable
private fun BrowseViewToggle() {
  SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
    SegmentedButton(
        selected = false,
        onClick = {},
        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        icon = {},
        modifier = Modifier.testTag(C.Tag.browse_map_toggle),
    ) {
      Text(stringResource(R.string.browse_map))
    }
    SegmentedButton(
        selected = true,
        onClick = {},
        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        icon = {},
        modifier = Modifier.testTag(C.Tag.browse_list_toggle),
    ) {
      Text(stringResource(R.string.browse_list))
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun BrowseContentPreview() {
  PolyLoopTheme { BrowseContent(listings = sampleListings, onListingClick = {}) }
}
