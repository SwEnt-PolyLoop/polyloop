// Made with Claude.

package com.swent.polyloop.ui.browse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.resources.C
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListingCardTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val listing =
      Listing(
          id = "tent",
          ownerId = "owner",
          title = "Camping tent, 2 people",
          description = "A tent",
          category = ListingCategory.SPORTS_OUTDOOR,
          photoUrls = emptyList(),
          pricePerDay = 15,
          itemValue = 200,
          availableFrom = LocalDate.of(2026, 10, 1),
          availableTo = LocalDate.of(2026, 12, 31),
          status = ListingStatus.PUBLISHED,
          pickupArea = "Ecublens",
      )

  @Test
  fun displaysTitlePriceAndLocation() {
    composeTestRule.setContent { ListingCard(listing = listing, distanceText = "0.6 km") }

    composeTestRule.onNodeWithTag(C.Tag.listing_card_ + listing.id).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(C.Tag.listing_card_title, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertTextEquals("Camping tent, 2 people")
    composeTestRule
        .onNodeWithTag(C.Tag.listing_card_price, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertTextEquals("15 PP / day")
    composeTestRule
        .onNodeWithTag(C.Tag.listing_card_location, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertTextEquals("0.6 km · Ecublens")
  }

  @Test
  fun blankPickupAreaShowsOnlyDistance() {
    composeTestRule.setContent {
      ListingCard(listing = listing.copy(pickupArea = ""), distanceText = "0.6 km")
    }

    composeTestRule
        .onNodeWithTag(C.Tag.listing_card_location, useUnmergedTree = true)
        .assertTextEquals("0.6 km")
  }

  @Test
  fun clickingCardCallsOnClick() {
    var clicks = 0
    composeTestRule.setContent {
      ListingCard(listing = listing, distanceText = "0.6 km", onClick = { clicks++ })
    }

    composeTestRule.onNodeWithTag(C.Tag.listing_card_ + listing.id).performClick()

    assertEquals(1, clicks)
  }
}
