// Made with Claude.

package com.swent.polyloop.ui.navigation

import androidx.navigation.NavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationActionsTest {

  private val navigationActions =
      NavigationActions(
          NavHostController(InstrumentationRegistry.getInstrumentation().targetContext)
      )

  @Test
  fun navigateToRejectsScreenThatTakesAnArgument() {
    assertThrows(IllegalArgumentException::class.java) {
      navigationActions.navigateTo(Screen.ListingDetail)
    }
  }
}
