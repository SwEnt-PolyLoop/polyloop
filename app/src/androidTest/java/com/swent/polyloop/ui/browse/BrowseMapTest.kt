// Made with Claude.

package com.swent.polyloop.ui.browse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.model.location.MapPoint
import com.swent.polyloop.resources.C
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowseMapTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

  private val ecublens = MapPoint(latitude = 46.52, longitude = 6.57)

  private fun listing(id: String, pricePerDay: Int) =
      Listing(
          id = id,
          ownerId = "owner",
          title = "Item $id",
          description = "Description of $id",
          category = ListingCategory.OTHER,
          photoUrls = emptyList(),
          pricePerDay = pricePerDay,
          itemValue = 100,
          availableFrom = LocalDate.of(2026, 10, 1),
          availableTo = LocalDate.of(2026, 12, 31),
          status = ListingStatus.PUBLISHED,
          pickupPoint = ecublens,
      )

  /** Shows the pin of [listings], all at Ecublens, as the map draws it. */
  private fun setPin(listings: List<Listing>) {
    composeTestRule.setContent { BrowsePinLabel(pinLabel(BrowsePin(ecublens, listings))) }
  }

  @Test
  fun pinOfOneListingShowsItsPricePerDay() {
    setPin(listOf(listing("tent", pricePerDay = 15)))

    composeTestRule
        .onNodeWithTag(C.Tag.browse_pin_label)
        .assertTextEquals(resources.getString(R.string.listing_card_price, 15))
  }

  @Test
  fun pinOfSeveralListingsShowsHowManyItHolds() {
    setPin(listOf(listing("tent", 15), listing("drill", 8), listing("bike", 20)))

    composeTestRule
        .onNodeWithTag(C.Tag.browse_pin_label)
        .assertTextEquals(resources.getQuantityString(R.plurals.browse_pin_items, 3, 3))
  }

  @Test
  fun mapWithPinsIsDisplayed() {
    composeTestRule.setContent {
      BrowseMap(pins = listOf(BrowsePin(ecublens, listOf(listing("tent", 15)))))
    }

    composeTestRule.onNodeWithTag(C.Tag.browse_map).assertIsDisplayed()
  }
}
