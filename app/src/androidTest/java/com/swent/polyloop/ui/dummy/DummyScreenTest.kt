// Made with Claude.

package com.swent.polyloop.ui.dummy

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.resources.C
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
}
