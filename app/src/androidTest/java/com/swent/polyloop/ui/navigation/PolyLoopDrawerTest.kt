// Made with Claude.

package com.swent.polyloop.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.DrawerDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.model.profile.UserProfile
import com.swent.polyloop.resources.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PolyLoopDrawerTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private val john =
      UserProfile(
          uid = "me",
          name = "john",
          email = "john@epfl.ch",
          averageRating = 4.5,
          reviewCount = 2,
      )

  private val clicked = mutableListOf<Screen.TopLevel>()

  private fun setDrawer(
      profile: UserProfile? = john,
      isAdmin: Boolean = false,
      currentRoute: String? = Screen.Browse.route,
  ) {
    composeTestRule.setContent {
      PolyLoopDrawerContent(
          profile = profile,
          destinations = Screen.topLevelDestinations(isAdmin),
          currentRoute = currentRoute,
          onDestinationClick = { clicked += it },
      )
    }
  }

  /**
   * Opens the drawer over a screen [screenWidth] wide, whatever the test device's width, or over
   * the device's whole screen when it is null: alone, the root would shrink to the drawer's width.
   */
  private fun setDrawerOnScreen(screenWidth: Dp? = null) {
    composeTestRule.setContent {
      Box(
          if (screenWidth == null) Modifier.fillMaxSize() else Modifier.requiredWidth(screenWidth)
      ) {
        PolyLoopDrawerContent(
            profile = john,
            destinations = Screen.topLevelDestinations(isAdmin = false),
            currentRoute = Screen.Browse.route,
            onDestinationClick = {},
        )
      }
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  private fun item(screen: Screen.TopLevel) = node(C.Tag.drawerItem(screen.route))

  // Inside the clickable item, whose semantics merge it away.
  private fun badge() =
      composeTestRule.onNodeWithTag(C.Tag.drawer_admin_badge, useUnmergedTree = true)

  // ---------- header ----------

  @Test
  fun headerShowsTheInitialNameAndRating() {
    setDrawer()

    node(C.Tag.drawer_avatar_initial).assertTextEquals("J")
    node(C.Tag.drawer_name).assertTextEquals("john")
    node(C.Tag.drawer_rating)
        .assertTextEquals(context.resources.getQuantityString(R.plurals.profile_rating, 2, 4.5, 2))
    node(C.Tag.drawer_avatar_photo).assertDoesNotExist()
  }

  @Test
  fun headerShowsThePhotoInsteadOfTheInitial() {
    // A local image, so the photo loads without the network and never falls back.
    setDrawer(
        profile =
            john.copy(
                photoUrl = "android.resource://${context.packageName}/${R.drawable.ic_wallet}"
            )
    )

    node(C.Tag.drawer_avatar_photo).assertExists()
    node(C.Tag.drawer_avatar_initial).assertDoesNotExist()
  }

  @Test
  fun headerShowsTheInitialWhenThePhotoCannotLoad() {
    // No image loader handles this scheme, so loading fails without touching the network.
    setDrawer(profile = john.copy(photoUrl = "broken://john.jpg"))

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule
          .onAllNodesWithTag(C.Tag.drawer_avatar_initial)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    node(C.Tag.drawer_avatar_initial).assertTextEquals("J")
    node(C.Tag.drawer_avatar_photo).assertDoesNotExist()
  }

  @Test
  fun headerWithoutReviewsSaysSo() {
    setDrawer(profile = john.copy(reviewCount = 0, averageRating = 0.0))

    node(C.Tag.drawer_rating).assertTextEquals(context.getString(R.string.profile_no_reviews))
  }

  @Test
  fun headerWithoutProfileShowsPlaceholders() {
    setDrawer(profile = null)

    node(C.Tag.drawer_avatar_initial).assertTextEquals("?")
    node(C.Tag.drawer_name).assertTextEquals(context.getString(R.string.profile_name_placeholder))
    node(C.Tag.drawer_rating).assertTextEquals(context.getString(R.string.profile_no_reviews))
  }

  // ---------- items ----------

  @Test
  fun drawerListsEveryDestinationAndTheFooter() {
    setDrawer()

    Screen.topLevelDestinations(isAdmin = false).forEach { item(it).assertIsDisplayed() }
    node(C.Tag.drawer_footer).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun currentScreenIsHighlighted() {
    setDrawer(currentRoute = Screen.Wallet.route)

    item(Screen.Wallet).assertIsSelected()
    item(Screen.Browse).assertIsNotSelected()
  }

  @Test
  fun noItemIsHighlightedOnAScreenOutsideTheDrawer() {
    setDrawer(currentRoute = Screen.UserProfile.createRoute("jane"))

    Screen.topLevelDestinations(isAdmin = false).forEach { item(it).assertIsNotSelected() }
  }

  @Test
  fun tappingAnItemReportsIt() {
    setDrawer()

    item(Screen.ChatList).performClick()

    assertEquals(listOf<Screen.TopLevel>(Screen.ChatList), clicked)
  }

  // ---------- admin ----------

  @Test
  fun courtRulingsIsHiddenFromNonAdmins() {
    setDrawer(isAdmin = false)

    item(Screen.AdminCourtRuling).assertDoesNotExist()
    badge().assertDoesNotExist()
  }

  @Test
  fun courtRulingsIsShownToAdminsWithTheAdminBadge() {
    setDrawer(isAdmin = true)

    item(Screen.AdminCourtRuling).performScrollTo().assertIsDisplayed()
    badge().assertIsDisplayed()
  }

  // ---------- width ----------

  @Test
  fun drawerLeavesAStripOfTheScreenVisible() {
    setDrawerOnScreen()

    val screenWidth = composeTestRule.onRoot().fetchSemanticsNode().size.width
    val drawerWidth = node(C.Tag.drawer).fetchSemanticsNode().size.width
    assertTrue(
        "The drawer is $drawerWidth px wide on a $screenWidth px screen",
        drawerWidth <= screenWidth * 0.8f,
    )
  }

  @Test
  fun drawerCovers80PercentOfASmallPhone() {
    setDrawerOnScreen(360.dp)

    node(C.Tag.drawer).assertWidthIsEqualTo(288.dp)
  }

  @Test
  fun drawerCovers80PercentOfALargePhone() {
    // Not 412 dp: 80% of 420 dp is a whole number of pixels at the usual densities, so the
    // drawer's width, rounded down to whole pixels, is exactly 336 dp on any test device.
    setDrawerOnScreen(420.dp)

    node(C.Tag.drawer).assertWidthIsEqualTo(336.dp)
  }

  @Test
  fun drawerStaysAtMaterialsMaximumInLandscape() {
    setDrawerOnScreen(800.dp)

    node(C.Tag.drawer).assertWidthIsEqualTo(DrawerDefaults.MaximumDrawerWidth)
  }

  @Test
  fun drawerKeepsMaterialsMinimumOnAVeryNarrowScreen() {
    setDrawerOnScreen(280.dp)

    node(C.Tag.drawer).assertWidthIsEqualTo(240.dp)
  }
}
