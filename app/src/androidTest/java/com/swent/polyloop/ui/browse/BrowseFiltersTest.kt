// Made with Claude
package com.swent.polyloop.ui.browse

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingFilter
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.theme.PolyLoopTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests of [BrowseFilters]: what it shows for a given filter and which callback each action
 * calls. What the actions do to the filter is tested in `BrowseViewModelTest`.
 *
 * Nodes are found by test tag, so the tests do not depend on the device language. The chip row is
 * lazy, so the tests use the first two categories, which are composed on any screen size.
 */
@RunWith(AndroidJUnit4::class)
class BrowseFiltersTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val electronics = ListingCategory.ELECTRONICS
  private val sports = ListingCategory.SPORTS_OUTDOOR

  /** The filter drawn by [BrowseFilters]; tests change it to check that the screen follows. */
  private var filter by mutableStateOf(ListingFilter())
  private val queries = mutableListOf<String>()
  private val clickedCategories = mutableListOf<ListingCategory>()
  private var allClicks = 0

  private fun setFilters(initial: ListingFilter = ListingFilter()) {
    filter = initial
    composeTestRule.setContent {
      PolyLoopTheme {
        BrowseFilters(
            filter = filter,
            onQueryChange = { queries.add(it) },
            onCategoryClick = { clickedCategories.add(it) },
            onAllClick = { allClicks++ },
        )
      }
    }
  }

  private fun chip(category: ListingCategory) =
      composeTestRule.onNodeWithTag(C.Tag.browseCategoryChip(category))

  // --- Search field ---

  @Test
  fun searchFieldShowsTheQuery() {
    setFilters(ListingFilter(query = "tent"))

    composeTestRule.onNodeWithTag(C.Tag.browse_search_bar).assertTextEquals("tent")
  }

  @Test
  fun typingInTheSearchFieldReportsTheQuery() {
    setFilters()

    composeTestRule.onNodeWithTag(C.Tag.browse_search_bar).performTextInput("tent")

    assertEquals(listOf("tent"), queries)
  }

  @Test
  fun searchKeyClosesTheKeyboard() {
    setFilters()
    val searchBar = composeTestRule.onNodeWithTag(C.Tag.browse_search_bar)
    searchBar.performClick()
    searchBar.assertIsFocused()

    searchBar.performImeAction()

    searchBar.assertIsNotFocused()
  }

  @Test
  fun clearButtonIsShownOnlyWhileTheQueryIsNotEmpty() {
    setFilters()
    composeTestRule.onNodeWithTag(C.Tag.browse_clear_search).assertDoesNotExist()

    filter = ListingFilter(query = "tent")
    composeTestRule.onNodeWithTag(C.Tag.browse_clear_search).assertIsDisplayed()
  }

  @Test
  fun clearButtonReportsAnEmptyQuery() {
    setFilters(ListingFilter(query = "tent"))

    composeTestRule.onNodeWithTag(C.Tag.browse_clear_search).performClick()

    assertEquals(listOf(""), queries)
  }

  // --- Chips ---

  @Test
  fun everyCategoryHasAChipShowingItsLabel() {
    setFilters()

    ListingCategory.entries.forEach {
      composeTestRule
          .onNodeWithTag(C.Tag.browse_category_chips)
          .performScrollToNode(hasTestTag(C.Tag.browseCategoryChip(it)))
      chip(it).assertTextEquals(it.label)
    }
  }

  @Test
  fun allChipIsSelectedOnlyWhenNoCategoryIsSelected() {
    setFilters()
    composeTestRule.onNodeWithTag(C.Tag.browse_category_all).assertIsSelected()

    filter = ListingFilter(categories = setOf(sports))
    composeTestRule.onNodeWithTag(C.Tag.browse_category_all).assertIsNotSelected()
  }

  @Test
  fun exactlyTheChipsOfTheSelectedCategoriesAreSelected() {
    setFilters(ListingFilter(categories = setOf(sports)))
    chip(electronics).assertIsNotSelected()
    chip(sports).assertIsSelected()

    filter = ListingFilter(categories = setOf(electronics, sports))
    chip(electronics).assertIsSelected()
    chip(sports).assertIsSelected()
  }

  @Test
  fun clickingCategoryChipsReportsEachCategoryInOrder() {
    // Sports is already selected: clicking it is still reported, the caller deselects it.
    setFilters(ListingFilter(categories = setOf(sports)))

    chip(sports).performClick()
    chip(electronics).performClick()

    assertEquals(listOf(sports, electronics), clickedCategories)
    assertEquals(0, allClicks)
  }

  @Test
  fun clickingAllChipCallsOnlyOnAllClick() {
    setFilters(ListingFilter(categories = setOf(sports)))

    composeTestRule.onNodeWithTag(C.Tag.browse_category_all).performClick()

    assertEquals(1, allClicks)
    assertEquals(emptyList<ListingCategory>(), clickedCategories)
  }
}
