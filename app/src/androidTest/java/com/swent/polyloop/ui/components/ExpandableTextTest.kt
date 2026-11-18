// Made with Claude.

package com.swent.polyloop.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpandableTextTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val seeMore = context.getString(R.string.expandable_text_see_more)
  private val seeLess = context.getString(R.string.expandable_text_see_less)
  private val longText = "A light tent for two, used three times. ".repeat(40)

  private fun setText(text: String) = composeTestRule.setContent {
    ExpandableText(
        text = text,
        modifier = Modifier.width(TEXT_WIDTH).testTag(TEXT),
        toggleTag = TOGGLE,
    )
  }

  private fun textHeight() = composeTestRule.onNodeWithTag(TEXT).getBoundsInRoot().height

  @Test
  fun shortTextShowsNoButton() {
    setText("A light tent.")

    composeTestRule.onNodeWithTag(TOGGLE).assertDoesNotExist()
  }

  @Test
  fun longTextIsCutWithSeeMore() {
    setText(longText)

    composeTestRule.onNodeWithTag(TOGGLE, useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithTag(TOGGLE).assertTextEquals(seeMore)
  }

  @Test
  fun seeMoreShowsTheWholeTextAndSeeLessCutsItAgain() {
    setText(longText)
    val collapsed = textHeight()

    composeTestRule.onNodeWithTag(TOGGLE).performClick()
    composeTestRule.onNodeWithTag(TOGGLE).assertTextEquals(seeLess)
    assertTrue(textHeight() > collapsed)

    composeTestRule.onNodeWithTag(TOGGLE).performClick()
    composeTestRule.onNodeWithTag(TOGGLE).assertTextEquals(seeMore)
    assertEquals(collapsed, textHeight())
  }

  @Test
  fun newTextStartsCollapsed() {
    var text by mutableStateOf(longText)
    composeTestRule.setContent {
      ExpandableText(
          text = text,
          modifier = Modifier.width(TEXT_WIDTH).testTag(TEXT),
          toggleTag = TOGGLE,
      )
    }
    composeTestRule.onNodeWithTag(TOGGLE).performClick()

    text = "Another tent. ".repeat(40)

    composeTestRule.onNodeWithTag(TOGGLE).assertTextEquals(seeMore)
  }

  private companion object {
    const val TEXT = "expandable_text"
    const val TOGGLE = "expandable_text_toggle"
    val TEXT_WIDTH = 200.dp
  }
}
