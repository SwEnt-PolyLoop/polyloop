// Made with Claude.

package com.swent.polyloop.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.resources.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PolyLoopTopBarTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val destinations = Screen.topLevelDestinations(isAdmin = true)
  private val clickedDestinations = mutableListOf<Screen.TopLevel>()
  private var profileClicks = 0

  private fun setTopBar() {
    composeTestRule.setContent {
      PolyLoopTopBar(
          destinations = destinations,
          onDestinationClick = { clickedDestinations += it },
          onProfileClick = { profileClicks++ },
      )
    }
  }

  @Test
  fun menuIsCollapsedInitially() {
    setTopBar()

    composeTestRule.onNodeWithTag(C.Tag.top_bar).assertIsDisplayed()
    destinations.forEach {
      composeTestRule.onNodeWithTag(C.Tag.topBarMenuItem(it.route)).assertDoesNotExist()
    }
  }

  @Test
  fun menuButtonListsEveryDestination() {
    setTopBar()

    composeTestRule.onNodeWithTag(C.Tag.top_bar_menu_button).performClick()

    destinations.forEach {
      composeTestRule.onNodeWithTag(C.Tag.topBarMenuItem(it.route)).assertIsDisplayed()
    }
  }

  @Test
  fun selectingDestinationReportsItAndClosesMenu() {
    setTopBar()

    composeTestRule.onNodeWithTag(C.Tag.top_bar_menu_button).performClick()
    composeTestRule.onNodeWithTag(C.Tag.topBarMenuItem(Screen.Wallet.route)).performClick()

    assertEquals(listOf<Screen.TopLevel>(Screen.Wallet), clickedDestinations)
    composeTestRule.onNodeWithTag(C.Tag.topBarMenuItem(Screen.Wallet.route)).assertDoesNotExist()
  }

  @Test
  fun profileButtonReportsClick() {
    setTopBar()

    composeTestRule.onNodeWithTag(C.Tag.top_bar_profile_button).performClick()

    assertEquals(1, profileClicks)
    assertTrue(clickedDestinations.isEmpty())
  }
}
