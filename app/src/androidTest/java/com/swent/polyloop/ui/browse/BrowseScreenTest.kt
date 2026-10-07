// Made with Claude.

package com.swent.polyloop.ui.browse

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.resources.C
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowseScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val listings =
      listOf("tent", "projector", "drill", "helmet", "bike", "guitar").map { id ->
        Listing(
            id = id,
            ownerId = "owner",
            title = "Item $id",
            description = "Description of $id",
            category = ListingCategory.OTHER,
            photoUrls = emptyList(),
            pricePerDay = 10,
            itemValue = 100,
            availableFrom = LocalDate.of(2026, 10, 1),
            availableTo = LocalDate.of(2026, 12, 31),
            status = ListingStatus.PUBLISHED,
            pickupArea = "Ecublens",
        )
      }

  private val headerTags =
      listOf(
          C.Tag.browse_menu_button,
          C.Tag.browse_profile_button,
          C.Tag.browse_search_bar,
          C.Tag.browse_category_chips,
          C.Tag.browse_map_toggle,
          C.Tag.browse_list_toggle,
      )

  /** Matches any listing card, whatever its listing id. */
  private val isListingCard =
      SemanticsMatcher("is a listing card") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(C.Tag.listing_card_) == true
      }

  private fun setContent(onListingClick: (String) -> Unit = {}) {
    composeTestRule.setContent { BrowseContent(listings = listings, onListingClick) }
  }

  @Test
  fun headerPartsAreDisplayed() {
    setContent()

    composeTestRule.onNodeWithTag(C.Tag.browse_screen).assertIsDisplayed()
    headerTags.forEach { composeTestRule.onNodeWithTag(it).assertIsDisplayed() }
    composeTestRule.onNodeWithText("Browse").assertIsDisplayed()
    composeTestRule.onNodeWithText("All").assertIsDisplayed()
  }

  @Test
  fun everyCardCanBeReachedByScrolling() {
    setContent()

    listings.forEach { listing ->
      val cardTag = C.Tag.listing_card_ + listing.id
      composeTestRule.onNodeWithTag(C.Tag.browse_list).performScrollToNode(hasTestTag(cardTag))
      composeTestRule.onNodeWithTag(cardTag).assertIsDisplayed()
    }
  }

  @Test
  fun pressingHeaderControlsDoesNothing() {
    var clickedId: String? = null
    setContent { clickedId = it }

    listOf(
            C.Tag.browse_menu_button,
            C.Tag.browse_profile_button,
            C.Tag.browse_map_toggle,
            C.Tag.browse_search_bar,
        )
        .forEach { composeTestRule.onNodeWithTag(it).performClick() }

    assertNull(clickedId)
  }

  @Test
  fun tappingCardCallsOnListingClickWithItsId() {
    var clickedId: String? = null
    setContent { clickedId = it }

    composeTestRule.onNodeWithTag(C.Tag.listing_card_ + "projector").performClick()

    assertEquals("projector", clickedId)
  }

  @Test
  fun browseScreenPassesCardClicksToOnListingClick() {
    var clickedId: String? = null
    composeTestRule.setContent { BrowseScreen(onListingClick = { clickedId = it }) }

    composeTestRule.onAllNodes(isListingCard).onFirst().performClick()

    assertNotNull(clickedId)
  }
}
