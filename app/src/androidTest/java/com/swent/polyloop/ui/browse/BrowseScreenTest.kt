// Made with Claude.

package com.swent.polyloop.ui.browse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingRepository
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.resources.C
import java.time.LocalDate
import org.junit.Assert.assertEquals
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
          C.Tag.browse_title,
          C.Tag.browse_search_bar,
          C.Tag.browse_category_chips,
          C.Tag.browse_category_all,
          C.Tag.browse_map_toggle,
          C.Tag.browse_list_toggle,
      )

  private fun setContent(
      uiState: BrowseUiState = BrowseUiState(listings = listings),
      onListingClick: (String) -> Unit = {},
  ) {
    composeTestRule.setContent {
      BrowseContent(uiState = uiState, onListingClick = onListingClick, onRetry = {})
    }
  }

  private fun waitForTag(tag: String) {
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
  }

  @Test
  fun headerPartsAreDisplayed() {
    setContent()

    composeTestRule.onNodeWithTag(C.Tag.browse_screen).assertIsDisplayed()
    headerTags.forEach { composeTestRule.onNodeWithTag(it).assertIsDisplayed() }
  }

  @Test
  fun loadingStateShowsProgressIndicator() {
    setContent(uiState = BrowseUiState(isLoading = true))

    composeTestRule.onNodeWithTag(C.Tag.browse_loading).assertIsDisplayed()
  }

  @Test
  fun blankErrorShowsGenericMessage() {
    setContent(uiState = BrowseUiState(errorMsg = ""))

    val fallback =
        InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(R.string.browse_error_fallback)
    composeTestRule.onNodeWithTag(C.Tag.browse_error).assertTextEquals(fallback)
  }

  @Test
  fun emptyListShowsEmptyState() {
    setContent(uiState = BrowseUiState(listings = emptyList()))

    composeTestRule.onNodeWithTag(C.Tag.browse_empty).assertIsDisplayed()
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
    setContent(onListingClick = { clickedId = it })

    listOf(
            C.Tag.browse_map_toggle,
            C.Tag.browse_list_toggle,
            C.Tag.browse_search_bar,
        )
        .forEach { composeTestRule.onNodeWithTag(it).performClick() }

    assertNull(clickedId)
  }

  @Test
  fun tappingCardCallsOnListingClickWithItsId() {
    var clickedId: String? = null
    setContent(onListingClick = { clickedId = it })

    composeTestRule.onNodeWithTag(C.Tag.listing_card_ + "projector").performClick()

    assertEquals("projector", clickedId)
  }

  @Test
  fun browseScreenShowsErrorThenListingsAfterRetry() {
    // Fails the first load, then returns the listings, so Retry must reach the ViewModel.
    var calls = 0
    val repository =
        object : ListingRepository {
          override suspend fun getAllListings(): Result<List<Listing>> =
              if (calls++ == 0) Result.failure(IllegalStateException("Network down"))
              else Result.success(listings)

          override suspend fun getListing(id: String): Result<Listing?> = Result.success(null)
        }
    val viewModel = BrowseViewModel(repository)
    var clickedId: String? = null
    composeTestRule.setContent {
      BrowseScreen(viewModel = viewModel, onListingClick = { clickedId = it })
    }

    waitForTag(C.Tag.browse_error)
    composeTestRule.onNodeWithTag(C.Tag.browse_error).assertTextEquals("Network down")
    composeTestRule.onNodeWithTag(C.Tag.browse_retry_button).performClick()

    waitForTag(C.Tag.browse_list)
    composeTestRule.onNodeWithTag(C.Tag.listing_card_ + "tent").performClick()
    assertEquals("tent", clickedId)
  }
}
