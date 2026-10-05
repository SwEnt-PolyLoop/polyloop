// Edited with Claude.

package com.swent.polyloop

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import com.swent.polyloop.screen.MainScreen
import io.github.kakaocup.compose.node.element.ComposeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest : TestCase() {

  @get:Rule val composeTestRule = createAndroidComposeRule<com.swent.polyloop.MainActivity>()

  @Test
  fun appStartsOnSignIn() = run {
    step("Start Main Activity") {
      ComposeScreen.onComposeScreen<com.swent.polyloop.screen.MainScreen>(composeTestRule) {
        continueButton {
          assertIsDisplayed()
          assertTextEquals(composeTestRule.activity.getString(R.string.dummy_screen_continue))
        }
      }
    }
  }
}
