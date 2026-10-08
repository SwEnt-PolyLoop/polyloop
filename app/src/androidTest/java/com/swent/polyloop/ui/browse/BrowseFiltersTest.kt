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
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingFilter
import com.swent.polyloop.ui.theme.PolyLoopTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests of [BrowseFilters].
 *
 * Nodes are found by test tag, never by text, so the tests do not depend on the device language.
 * Only three short chips are shown at a time, so every chip is composed on any screen size.
 */
@RunWith(AndroidJUnit4::class)
class BrowseFiltersTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val electronics = ListingCategory.ELECTRONICS
  private val sports = ListingCategory.SPORTS_OUTDOOR
  private val tools = ListingCategory.TOOLS_DIY
  private val testCategories = listOf(electronics, sports, tools)

  /** Short labels, so the whole chip row fits on screen. */
  private val shortLabel: (ListingCategory) -> String = { it.name.take(4) }

  private fun setFilters(
      filter: ListingFilter = ListingFilter(),
      categories: List<ListingCategory> = testCategories,
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
            categories = categories,
            categoryLabel = shortLabel,
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
          categories = testCategories,
          categoryLabel = shortLabel,
      )
    }
  }

  // --- What is displayed ---

  @Test
  fun searchFieldAndAllChipAreDisplayed() {
    setFilters()
    composeTestRule.onNodeWithTag(BROWSE_SEARCH_FIELD_TAG).assertIsDisplayed()
    composeTestRule.onNodeWithTag(BROWSE_CHIP_ALL_TAG).assertIsDisplayed()
  }

  @Test
  fun oneChipIsDisplayedForEachGivenCategory() {
    setFilters()
    testCategories.forEach { composeTestRule.onNodeWithTag(browseChipTag(it)).assertIsDisplayed() }
  }

  @Test
  fun categoriesNotGivenHaveNoChip() {
    setFilters(categories = listOf(electronics))
    composeTestRule.onNodeWithTag(browseChipTag(electronics)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(browseChipTag(sports)).assertDoesNotExist()
    composeTestRule.onNodeWithTag(browseChipTag(tools)).assertDoesNotExist()
  }

  @Test
  fun defaultCategoriesShowTheFirstCategoryChip() {
    composeTestRule.setContent {
      PolyLoopTheme {
        BrowseFilters(
            filter = ListingFilter(),
            onQueryChange = {},
            onCategoryClick = {},
            onAllClick = {},
        )
      }
    }
    composeTestRule
        .onNodeWithTag(browseChipTag(ListingCategory.entries.first()))
        .assertIsDisplayed()
  }

  @Test
  fun chipShowsTheLabelFromCategoryLabel() {
    composeTestRule.setContent {
      PolyLoopTheme {
        BrowseFilters(
            filter = ListingFilter(),
            onQueryChange = {},
            onCategoryClick = {},
            onAllClick = {},
            categories = listOf(sports),
            categoryLabel = { "Camping gear" },
        )
      }
    }
    composeTestRule.onNodeWithTag(browseChipTag(sports)).assertTextEquals("Camping gear")
  }

  @Test
  fun defaultLabelIsTheReadableEnumName() {
    composeTestRule.setContent {
      PolyLoopTheme {
        BrowseFilters(
            filter = ListingFilter(),
            onQueryChange = {},
            onCategoryClick = {},
            onAllClick = {},
            categories = listOf(ListingCategory.SPORTS_OUTDOOR),
        )
      }
    }
    composeTestRule
        .onNodeWithTag(browseChipTag(ListingCategory.SPORTS_OUTDOOR))
        .assertTextEquals("Sports outdoor")
  }

  // --- Selection ---

  @Test
  fun allChipIsSelectedWhenNoCategoryIsSelected() {
    setFilters(filter = ListingFilter(categories = emptySet()))
    composeTestRule.onNodeWithTag(BROWSE_CHIP_ALL_TAG).assertIsSelected()
    testCategories.forEach {
      composeTestRule.onNodeWithTag(browseChipTag(it)).assertIsNotSelected()
    }
  }

  @Test
  fun allChipIsNotSelectedWhenACategoryIsSelected() {
    setFilters(filter = ListingFilter(categories = setOf(sports)))
    composeTestRule.onNodeWithTag(BROWSE_CHIP_ALL_TAG).assertIsNotSelected()
    composeTestRule.onNodeWithTag(browseChipTag(sports)).assertIsSelected()
  }

  @Test
  fun everySelectedCategoryChipIsSelected() {
    setFilters(filter = ListingFilter(categories = setOf(electronics, tools)))
    composeTestRule.onNodeWithTag(browseChipTag(electronics)).assertIsSelected()
    composeTestRule.onNodeWithTag(browseChipTag(tools)).assertIsSelected()
    composeTestRule.onNodeWithTag(browseChipTag(sports)).assertIsNotSelected()
  }

  // --- Callbacks ---

  @Test
  fun clickingAllChipCallsOnAllClick() {
    var allClicks = 0
    setFilters(onAllClick = { allClicks++ })
    composeTestRule.onNodeWithTag(BROWSE_CHIP_ALL_TAG).performClick()
    assertEquals(1, allClicks)
  }

  @Test
  fun clickingCategoryChipReportsThatCategory() {
    val clicked = mutableListOf<ListingCategory>()
    setFilters(onCategoryClick = { clicked.add(it) })
    composeTestRule.onNodeWithTag(browseChipTag(sports)).performClick()
    assertEquals(listOf(sports), clicked)
  }

  @Test
  fun clickingSeveralCategoryChipsReportsEachOneInOrder() {
    val clicked = mutableListOf<ListingCategory>()
    setFilters(onCategoryClick = { clicked.add(it) })
    composeTestRule.onNodeWithTag(browseChipTag(tools)).performClick()
    composeTestRule.onNodeWithTag(browseChipTag(electronics)).performClick()
    assertEquals(listOf(tools, electronics), clicked)
  }

  @Test
  fun clickingAnAlreadySelectedChipStillReportsIt() {
    val clicked = mutableListOf<ListingCategory>()
    setFilters(
        filter = ListingFilter(categories = setOf(sports)),
        onCategoryClick = { clicked.add(it) },
    )
    composeTestRule.onNodeWithTag(browseChipTag(sports)).performClick()
    assertEquals(listOf(sports), clicked)
  }

  @Test
  fun clickingACategoryChipDoesNotCallOnAllClick() {
    var allClicks = 0
    setFilters(onAllClick = { allClicks++ })
    composeTestRule.onNodeWithTag(browseChipTag(sports)).performClick()
    assertEquals(0, allClicks)
  }

  // --- Search field ---

  @Test
  fun typingInTheSearchFieldReportsTheQuery() {
    var query = ""
    setFilters(onQueryChange = { query = it })
    composeTestRule.onNodeWithTag(BROWSE_SEARCH_FIELD_TAG).performTextInput("tent")
    assertEquals("tent", query)
  }

  @Test
  fun searchFieldShowsTheQuery() {
    setFilters(filter = ListingFilter(query = "tent"))
    composeTestRule.onNodeWithTag(BROWSE_SEARCH_FIELD_TAG).assertTextEquals("tent")
  }

  @Test
  fun clearButtonIsHiddenWhenTheQueryIsEmpty() {
    setFilters(filter = ListingFilter(query = ""))
    composeTestRule.onNodeWithTag(BROWSE_CLEAR_SEARCH_TAG).assertDoesNotExist()
  }

  @Test
  fun clearButtonIsDisplayedWhenTheQueryIsNotEmpty() {
    setFilters(filter = ListingFilter(query = "tent"))
    composeTestRule.onNodeWithTag(BROWSE_CLEAR_SEARCH_TAG).assertIsDisplayed()
  }

  @Test
  fun clearButtonReportsAnEmptyQuery() {
    val queries = mutableListOf<String>()
    setFilters(filter = ListingFilter(query = "tent"), onQueryChange = { queries.add(it) })
    composeTestRule.onNodeWithTag(BROWSE_CLEAR_SEARCH_TAG).performClick()
    assertEquals(listOf(""), queries)
  }

  // --- With a state, like on the Browse screen ---

  @Test
  fun typedTextStaysInTheSearchFieldAndShowsTheClearButton() {
    composeTestRule.setContent { StatefulFilters() }
    composeTestRule.onNodeWithTag(BROWSE_CLEAR_SEARCH_TAG).assertDoesNotExist()

    composeTestRule.onNodeWithTag(BROWSE_SEARCH_FIELD_TAG).performTextInput("tent")

    composeTestRule.onNodeWithTag(BROWSE_SEARCH_FIELD_TAG).assertTextEquals("tent")
    composeTestRule.onNodeWithTag(BROWSE_CLEAR_SEARCH_TAG).assertIsDisplayed()
  }

  @Test
  fun clearButtonEmptiesTheSearchFieldAndDisappears() {
    composeTestRule.setContent { StatefulFilters() }
    composeTestRule.onNodeWithTag(BROWSE_SEARCH_FIELD_TAG).performTextInput("tent")

    composeTestRule.onNodeWithTag(BROWSE_CLEAR_SEARCH_TAG).performClick()

    composeTestRule.onNodeWithTag(BROWSE_CLEAR_SEARCH_TAG).assertDoesNotExist()
  }

  @Test
  fun clickingAChipSelectsItAndDeselectsAll() {
    composeTestRule.setContent { StatefulFilters() }
    composeTestRule.onNodeWithTag(BROWSE_CHIP_ALL_TAG).assertIsSelected()

    composeTestRule.onNodeWithTag(browseChipTag(sports)).performClick()

    composeTestRule.onNodeWithTag(browseChipTag(sports)).assertIsSelected()
    composeTestRule.onNodeWithTag(BROWSE_CHIP_ALL_TAG).assertIsNotSelected()
  }

  @Test
  fun severalChipsCanBeSelectedAtTheSameTime() {
    composeTestRule.setContent { StatefulFilters() }

    composeTestRule.onNodeWithTag(browseChipTag(electronics)).performClick()
    composeTestRule.onNodeWithTag(browseChipTag(tools)).performClick()

    composeTestRule.onNodeWithTag(browseChipTag(electronics)).assertIsSelected()
    composeTestRule.onNodeWithTag(browseChipTag(tools)).assertIsSelected()
    composeTestRule.onNodeWithTag(browseChipTag(sports)).assertIsNotSelected()
  }

  @Test
  fun clickingASelectedChipDeselectsItAndSelectsAllAgainWhenNoneIsLeft() {
    composeTestRule.setContent { StatefulFilters() }
    composeTestRule.onNodeWithTag(browseChipTag(sports)).performClick()

    composeTestRule.onNodeWithTag(browseChipTag(sports)).performClick()

    composeTestRule.onNodeWithTag(browseChipTag(sports)).assertIsNotSelected()
    composeTestRule.onNodeWithTag(BROWSE_CHIP_ALL_TAG).assertIsSelected()
  }

  @Test
  fun allChipClearsEverySelectedChip() {
    composeTestRule.setContent { StatefulFilters() }
    composeTestRule.onNodeWithTag(browseChipTag(electronics)).performClick()
    composeTestRule.onNodeWithTag(browseChipTag(tools)).performClick()

    composeTestRule.onNodeWithTag(BROWSE_CHIP_ALL_TAG).performClick()

    composeTestRule.onNodeWithTag(BROWSE_CHIP_ALL_TAG).assertIsSelected()
    testCategories.forEach {
      composeTestRule.onNodeWithTag(browseChipTag(it)).assertIsNotSelected()
    }
  }
}
