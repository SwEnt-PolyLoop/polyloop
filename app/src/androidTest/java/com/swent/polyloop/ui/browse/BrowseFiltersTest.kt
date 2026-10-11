// Made with Claude
package com.swent.polyloop.ui.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
 * UI tests of [BrowseFilters].
 *
 * Nodes are found by test tag, never by text, so the tests do not depend on the device language.
 * The chip row is lazy, so the tests use the first two categories, which are composed on any screen
 * size.
 */
@RunWith(AndroidJUnit4::class)
class BrowseFiltersTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val electronics = ListingCategory.ELECTRONICS
  private val sports = ListingCategory.SPORTS_OUTDOOR

  private fun setFilters(
      filter: ListingFilter = ListingFilter(),
      onQueryChange: (String) -> Unit = {},
      onCategoryClick: (ListingCategory) -> Unit = {},
      onAllClick: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      PolyLoopTheme {
        BrowseFilters(
            filter = filter,
            onQueryChange = onQueryChange,
            onCategoryClick = onCategoryClick,
            onAllClick = onAllClick,
        )
      }
    }
  }

  /** Keeps the filter in a state, the way the Browse screen does. */
  @Composable
  private fun StatefulFilters() {
    var filter by remember { mutableStateOf(ListingFilter()) }
    PolyLoopTheme {
      BrowseFilters(
          filter = filter,
          onQueryChange = { filter = filter.copy(query = it) },
          onCategoryClick = { category ->
            val categories =
                if (category in filter.categories) filter.categories - category
                else filter.categories + category
            filter = filter.copy(categories = categories)
          },
          onAllClick = { filter = filter.copy(categories = emptySet()) },
      )
    }
  }

  // --- What is displayed ---

  @Test
  fun searchFieldAndAllChipAreDisplayed() {
    setFilters()
    composeTestRule.onNodeWithTag(C.Tag.browse_search_bar).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.browse_category_all).assertIsDisplayed()
  }

  @Test
  fun oneChipExistsForEachCategory() {
    setFilters()
    ListingCategory.entries.forEach {
      composeTestRule
          .onNodeWithTag(C.Tag.browse_category_chips)
          .performScrollToNode(hasTestTag(C.Tag.browseCategoryChip(it)))
      composeTestRule.onNodeWithTag(C.Tag.browseCategoryChip(it)).assertIsDisplayed()
    }
  }

  @Test
  fun chipShowsTheCategoryLabel() {
    setFilters()
    composeTestRule.onNodeWithTag(C.Tag.browseCategoryChip(sports)).assertTextEquals(sports.label)
  }

  // --- Selection ---

  @Test
  fun allChipIsSelectedWhenNoCategoryIsSelected() {
    setFilters(filter = ListingFilter(categories = emptySet()))
    composeTestRule.onNodeWithTag(C.Tag.browse_category_all).assertIsSelected()
    listOf(electronics, sports).forEach {
      composeTestRule.onNodeWithTag(C.Tag.browseCategoryChip(it)).assertIsNotSelected()
    }
  }

  @Test
  fun allChipIsNotSelectedWhenACategoryIsSelected() {
    setFilters(filter = ListingFilter(categories = setOf(sports)))
    composeTestRule.onNodeWithTag(C.Tag.browse_category_all).assertIsNotSelected()
    composeTestRule.onNodeWithTag(C.Tag.browseCategoryChip(sports)).assertIsSelected()
    composeTestRule.onNodeWithTag(C.Tag.browseCategoryChip(electronics)).assertIsNotSelected()
  }

  @Test
  fun everySelectedCategoryChipIsSelected() {
    setFilters(filter = ListingFilter(categories = setOf(electronics, sports)))
    composeTestRule.onNodeWithTag(C.Tag.browseCategoryChip(electronics)).assertIsSelected()
    composeTestRule.onNodeWithTag(C.Tag.browseCategoryChip(sports)).assertIsSelected()
  }

  // --- Callbacks ---

  @Test
  fun clickingAllChipCallsOnAllClick() {
    var allClicks = 0
    setFilters(onAllClick = { allClicks++ })
    composeTestRule.onNodeWithTag(C.Tag.browse_category_all).performClick()
    assertEquals(1, allClicks)
  }

  @Test
  fun clickingCategoryChipReportsThatCategory() {
    val clicked = mutableListOf<ListingCategory>()
    setFilters(onCategoryClick = { clicked.add(it) })
    composeTestRule.onNodeWithTag(C.Tag.browseCategoryChip(sports)).performClick()
    assertEquals(listOf(sports), clicked)
  }

  @Test
  fun clickingSeveralCategoryChipsReportsEachOneInOrder() {
    val clicked = mutableListOf<ListingCategory>()
    setFilters(onCategoryClick = { clicked.add(it) })
    composeTestRule.onNodeWithTag(C.Tag.browseCategoryChip(sports)).performClick()
    composeTestRule.onNodeWithTag(C.Tag.browseCategoryChip(electronics)).performClick()
    assertEquals(listOf(sports, electronics), clicked)
  }

  @Test
  fun clickingAnAlreadySelectedChipStillReportsIt() {
    val clicked = mutableListOf<ListingCategory>()
    setFilters(
        filter = ListingFilter(categories = setOf(sports)),
        onCategoryClick = { clicked.add(it) },
    )
    composeTestRule.onNodeWithTag(C.Tag.browseCategoryChip(sports)).performClick()
    assertEquals(listOf(sports), clicked)
  }

  @Test
  fun clickingACategoryChipDoesNotCallOnAllClick() {
    var allClicks = 0
    setFilters(onAllClick = { allClicks++ })
    composeTestRule.onNodeWithTag(C.Tag.browseCategoryChip(sports)).performClick()
    assertEquals(0, allClicks)
  }

  // --- Search field ---

  @Test
  fun typingInTheSearchFieldReportsTheQuery() {
    var query = ""
    setFilters(onQueryChange = { query = it })
    composeTestRule.onNodeWithTag(C.Tag.browse_search_bar).performTextInput("tent")
    assertEquals("tent", query)
  }

  @Test
  fun searchFieldShowsTheQuery() {
    setFilters(filter = ListingFilter(query = "tent"))
    composeTestRule.onNodeWithTag(C.Tag.browse_search_bar).assertTextEquals("tent")
  }

  @Test
  fun clearButtonIsHiddenWhenTheQueryIsEmpty() {
    setFilters(filter = ListingFilter(query = ""))
    composeTestRule.onNodeWithTag(C.Tag.browse_clear_search).assertDoesNotExist()
  }

  @Test
  fun clearButtonIsDisplayedWhenTheQueryIsNotEmpty() {
    setFilters(filter = ListingFilter(query = "tent"))
    composeTestRule.onNodeWithTag(C.Tag.browse_clear_search).assertIsDisplayed()
  }

  @Test
  fun clearButtonReportsAnEmptyQuery() {
    val queries = mutableListOf<String>()
    setFilters(filter = ListingFilter(query = "tent"), onQueryChange = { queries.add(it) })
    composeTestRule.onNodeWithTag(C.Tag.browse_clear_search).performClick()
    assertEquals(listOf(""), queries)
  }

  // --- With a state, like on the Browse screen ---

  @Test
  fun typedTextStaysInTheSearchFieldAndShowsTheClearButton() {
    composeTestRule.setContent { StatefulFilters() }
    composeTestRule.onNodeWithTag(C.Tag.browse_clear_search).assertDoesNotExist()

    composeTestRule.onNodeWithTag(C.Tag.browse_search_bar).performTextInput("tent")

    composeTestRule.onNodeWithTag(C.Tag.browse_search_bar).assertTextEquals("tent")
    composeTestRule.onNodeWithTag(C.Tag.browse_clear_search).assertIsDisplayed()
  }

  @Test
  fun clearButtonEmptiesTheSearchFieldAndDisappears() {
    composeTestRule.setContent { StatefulFilters() }
    composeTestRule.onNodeWithTag(C.Tag.browse_search_bar).performTextInput("tent")

    composeTestRule.onNodeWithTag(C.Tag.browse_clear_search).performClick()

    composeTestRule.onNodeWithTag(C.Tag.browse_clear_search).assertDoesNotExist()
  }

  @Test
  fun clickingAChipSelectsItAndDeselectsAll() {
    composeTestRule.setContent { StatefulFilters() }
    composeTestRule.onNodeWithTag(C.Tag.browse_category_all).assertIsSelected()

    composeTestRule.onNodeWithTag(C.Tag.browseCategoryChip(sports)).performClick()

    composeTestRule.onNodeWithTag(C.Tag.browseCategoryChip(sports)).assertIsSelected()
    composeTestRule.onNodeWithTag(C.Tag.browse_category_all).assertIsNotSelected()
  }

  @Test
  fun severalChipsCanBeSelectedAtTheSameTime() {
    composeTestRule.setContent { StatefulFilters() }

    composeTestRule.onNodeWithTag(C.Tag.browseCategoryChip(electronics)).performClick()
    composeTestRule.onNodeWithTag(C.Tag.browseCategoryChip(sports)).performClick()

    composeTestRule.onNodeWithTag(C.Tag.browseCategoryChip(electronics)).assertIsSelected()
    composeTestRule.onNodeWithTag(C.Tag.browseCategoryChip(sports)).assertIsSelected()
  }

  @Test
  fun clickingASelectedChipDeselectsItAndSelectsAllAgainWhenNoneIsLeft() {
    composeTestRule.setContent { StatefulFilters() }
    composeTestRule.onNodeWithTag(C.Tag.browseCategoryChip(sports)).performClick()

    composeTestRule.onNodeWithTag(C.Tag.browseCategoryChip(sports)).performClick()

    composeTestRule.onNodeWithTag(C.Tag.browseCategoryChip(sports)).assertIsNotSelected()
    composeTestRule.onNodeWithTag(C.Tag.browse_category_all).assertIsSelected()
  }

  @Test
  fun allChipClearsEverySelectedChip() {
    composeTestRule.setContent { StatefulFilters() }
    composeTestRule.onNodeWithTag(C.Tag.browseCategoryChip(electronics)).performClick()
    composeTestRule.onNodeWithTag(C.Tag.browseCategoryChip(sports)).performClick()

    composeTestRule.onNodeWithTag(C.Tag.browse_category_all).performClick()

    composeTestRule.onNodeWithTag(C.Tag.browse_category_all).assertIsSelected()
    listOf(electronics, sports).forEach {
      composeTestRule.onNodeWithTag(C.Tag.browseCategoryChip(it)).assertIsNotSelected()
    }
  }
}
