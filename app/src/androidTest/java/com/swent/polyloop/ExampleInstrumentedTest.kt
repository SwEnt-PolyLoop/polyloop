// Edited with Claude.

package com.swent.polyloop

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import com.swent.polyloop.model.RepositoryProvider
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.screen.MainScreen
import io.github.kakaocup.compose.node.element.ComposeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest : TestCase() {

  /**
   * Nobody signed in, whatever session the emulator kept. Applied before the activity starts, which
   * reads the signed-in user on launch: @Before would run too late.
   */
  @get:Rule(order = 0)
  val signedOut =
      object : ExternalResource() {
        override fun before() {
          RepositoryProvider.authRepository = FakeAuthRepository(signedInUser = null)
        }

        override fun after() {
          RepositoryProvider.reset()
        }
      }

  @get:Rule(order = 1)
  val composeTestRule = createAndroidComposeRule<com.swent.polyloop.MainActivity>()

  @Test
  fun appStartsOnSignIn() = run {
    step("Start Main Activity") {
      ComposeScreen.onComposeScreen<com.swent.polyloop.screen.MainScreen>(composeTestRule) {
        loginButton { assertIsDisplayed() }
      }
    }
  }
}
