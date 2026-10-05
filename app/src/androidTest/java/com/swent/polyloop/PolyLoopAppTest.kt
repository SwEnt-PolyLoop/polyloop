// Made with Claude.

package com.swent.polyloop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.navigation.NavigationActions
import com.swent.polyloop.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PolyLoopAppTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var navController: NavHostController

  private fun setApp(isAdmin: Boolean = false) {
    composeTestRule.setContent {
      navController = rememberNavController()
      PolyLoopApp(navController = navController, isAdmin = isAdmin)
    }
  }

  private fun setAppSignedIn(isAdmin: Boolean = false) {
    setApp(isAdmin)
    composeTestRule.onNodeWithTag(C.Tag.dummy_screen_continue_button).performClick()
  }

  private fun openFromMenu(screen: Screen.TopLevel) {
    composeTestRule.onNodeWithTag(C.Tag.top_bar_menu_button).performClick()
    composeTestRule.onNodeWithTag(C.Tag.topBarMenuItem(screen.route)).performClick()
  }

  private fun assertCurrentScreen(screen: Screen, shownRoute: String = screen.route) {
    composeTestRule.waitForIdle()
    assertEquals(screen.route, navController.currentDestination?.route)
    composeTestRule.onNodeWithTag(C.Tag.dummy_screen).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.dummy_screen_text).assertTextEquals(shownRoute)
  }

  @Test
  fun startsOnSignInWithoutTopBar() {
    setApp()

    assertCurrentScreen(Screen.Auth)
    composeTestRule.onNodeWithTag(C.Tag.top_bar).assertDoesNotExist()
  }

  @Test
  fun continueOpensBrowseAndRemovesSignInFromBackStack() {
    setAppSignedIn()

    assertCurrentScreen(Screen.Browse)
    assertNull(navController.previousBackStackEntry)
    composeTestRule.onNodeWithTag(C.Tag.top_bar).assertIsDisplayed()
  }

  @Test
  fun menuOpensEveryTopLevelDestination() {
    setAppSignedIn(isAdmin = true)

    Screen.topLevelDestinations(isAdmin = true).forEach { screen ->
      openFromMenu(screen)
      assertCurrentScreen(screen)
    }
  }

  @Test
  fun menuHidesAdminCourtRulingFromNonAdmins() {
    setAppSignedIn(isAdmin = false)

    composeTestRule.onNodeWithTag(C.Tag.top_bar_menu_button).performClick()

    composeTestRule.onNodeWithTag(C.Tag.topBarMenuItem(Screen.Wallet.route)).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(C.Tag.topBarMenuItem(Screen.AdminCourtRuling.route))
        .assertDoesNotExist()
  }

  @Test
  fun topLevelDestinationsReturnToBrowseOnBack() {
    setAppSignedIn()

    openFromMenu(Screen.Wallet)
    openFromMenu(Screen.ChatList)

    assertCurrentScreen(Screen.ChatList)
    assertEquals(Screen.Browse.route, navController.previousBackStackEntry?.destination?.route)
  }

  @Test
  fun reselectingCurrentTopLevelDestinationDoesNotStackIt() {
    setAppSignedIn()

    openFromMenu(Screen.Wallet)
    openFromMenu(Screen.Wallet)
    composeTestRule.runOnUiThread { navController.popBackStack() }

    assertCurrentScreen(Screen.Browse)
  }

  @Test
  fun profileButtonOpensOwnProfile() {
    setAppSignedIn()

    composeTestRule.onNodeWithTag(C.Tag.top_bar_profile_button).performClick()

    assertCurrentScreen(Screen.Profile)
  }

  @Test
  fun everyRouteWithAnArgumentIsRegistered() {
    setAppSignedIn()
    val routes =
        mapOf(
            Screen.ListingDetail to Screen.ListingDetail.createRoute("listing-1"),
            Screen.EditListing to Screen.EditListing.createRoute("listing-1"),
            Screen.RentalDetail to Screen.RentalDetail.createRoute("rental-1"),
            Screen.Chat to Screen.Chat.createRoute("rental-1"),
            Screen.Handover to Screen.Handover.createRoute("rental-1"),
            Screen.Dispute to Screen.Dispute.createRoute("rental-1"),
            Screen.UserProfile to Screen.UserProfile.createRoute("user-1"),
        )

    routes.forEach { (screen, route) ->
      composeTestRule.runOnUiThread { NavigationActions(navController).navigateTo(route) }
      assertCurrentScreen(screen, shownRoute = route)
    }
  }

  @Test
  fun routeArgumentIsPassedToTheDestination() {
    setAppSignedIn()

    composeTestRule.runOnUiThread {
      NavigationActions(navController).navigateTo(Screen.RentalDetail.createRoute("rental-1"))
    }
    composeTestRule.waitForIdle()

    assertEquals(
        "rental-1",
        navController.currentBackStackEntry?.arguments?.getString(Screen.ARG_RENTAL_ID),
    )
  }
}
