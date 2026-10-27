// Made with Claude.

package com.swent.polyloop.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.resources.C
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PolyLoopTopBarTest {

  @get:Rule val composeTestRule = createComposeRule()

  private var menuClicks = 0
  private var profileClicks = 0

  private fun setTopBar() {
    composeTestRule.setContent {
      PolyLoopTopBar(onMenuClick = { menuClicks++ }, onProfileClick = { profileClicks++ })
    }
  }

  @Test
  fun topBarShowsTheMenuAndProfileButtons() {
    setTopBar()

    composeTestRule.onNodeWithTag(C.Tag.top_bar).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.top_bar_menu_button).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.top_bar_profile_button).assertIsDisplayed()
  }

  @Test
  fun menuButtonReportsClick() {
    setTopBar()

    composeTestRule.onNodeWithTag(C.Tag.top_bar_menu_button).performClick()

    assertEquals(1, menuClicks)
    assertEquals(0, profileClicks)
  }

  @Test
  fun profileButtonReportsClick() {
    setTopBar()

    composeTestRule.onNodeWithTag(C.Tag.top_bar_profile_button).performClick()

    assertEquals(1, profileClicks)
    assertEquals(0, menuClicks)
  }
}
