// Made with Claude.

package com.swent.polyloop.ui.dummy

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
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
class DummyScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun showsGivenText() {
    composeTestRule.setContent { DummyScreen("browse") }

    composeTestRule.onNodeWithTag(C.Tag.dummy_screen_text).assertTextEquals("browse")
  }

  @Test
  fun hidesContinueButtonByDefault() {
    composeTestRule.setContent { DummyScreen("browse") }

    composeTestRule.onNodeWithTag(C.Tag.dummy_screen).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.dummy_screen_continue_button).assertDoesNotExist()
  }

  @Test
  fun continueButtonRunsOnContinue() {
    var clicks = 0
    composeTestRule.setContent { DummyScreen("auth", onContinue = { clicks++ }) }

    composeTestRule.onNodeWithTag(C.Tag.dummy_screen_continue_button).performClick()

    assertEquals(1, clicks)
  }
}
