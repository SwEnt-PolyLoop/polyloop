// Made with Claude.

package com.swent.polyloop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.model.RepositoryProvider
import com.swent.polyloop.model.auth.AuthError
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.auth.AuthResult
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.chat.Conversation
import com.swent.polyloop.model.chat.FakeChatRepository
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingRepository
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.listingdetail.ListingDetailViewModel
import com.swent.polyloop.ui.navigation.NavigationActions
import com.swent.polyloop.ui.navigation.Screen
import java.time.Instant
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PolyLoopAppTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var navController: NavHostController

  private val listing =
      Listing(
          id = "listing-1",
          ownerId = "owner-1",
          title = "Camping tent",
          description = "Light tent.",
          category = ListingCategory.SPORTS_OUTDOOR,
          photoUrls = emptyList(),
          pricePerDay = 15,
          itemValue = 180,
          availableFrom = LocalDate.of(2026, 10, 6),
          availableTo = LocalDate.of(2026, 10, 20),
          status = ListingStatus.PUBLISHED,
          pickupArea = "Ecublens",
      )

  /** In-memory listings, so screens wired to RepositoryProvider never reach Firestore. */
  private val listingRepository =
      object : ListingRepository {
        override suspend fun getAllListings(): Result<List<Listing>> =
            Result.success(listOf(listing))

        override suspend fun getListing(id: String): Result<Listing?> =
            Result.success(listing.takeIf { it.id == id })
      }

  /** The signed-in user PolyLoopApp reads on start; null means nobody is signed in. */
  private var currentUser: AuthUser? = null

  private val authRepository =
      object : AuthRepository {
        override suspend fun signUp(name: String, email: String, password: String) =
            AuthResult.Success(Unit)

        override suspend fun signIn(email: String, password: String): AuthResult<AuthUser> =
            AuthResult.Failure(AuthError.UNKNOWN)

        override suspend fun resendVerificationEmail(email: String, password: String) =
            AuthResult.Success(Unit)

        override fun getCurrentUser(): AuthUser? = currentUser

        override fun signOut() {
          currentUser = null
        }
      }

  /** In-memory conversations, so the Chat list never reaches Firestore. */
  private val chatRepository = FakeChatRepository(currentUid = "me")

  @Before
  fun setUp() {
    RepositoryProvider.listingRepository = listingRepository
    RepositoryProvider.authRepository = authRepository
    RepositoryProvider.chatRepository = chatRepository
  }

  @After
  fun tearDown() {
    RepositoryProvider.reset()
  }

  private fun openListingDetail() {
    composeTestRule.runOnUiThread {
      NavigationActions(navController).navigateTo(Screen.ListingDetail.createRoute(listing.id))
    }
    waitForTag(C.Tag.listing_detail_screen)
  }

  // isSignedIn is left to its default, so the app reads the user from RepositoryProvider.
  private fun setApp(isAdmin: Boolean = false) {
    composeTestRule.setContent {
      navController = rememberNavController()
      PolyLoopApp(navController = navController, isAdmin = isAdmin)
    }
  }

  private fun waitForTag(tag: String) {
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
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
    // Browse, Chat list and Chat are wired to their real screens; the others still show DummyScreen
    // with their route.
    if (screen == Screen.Browse) {
      composeTestRule.onNodeWithTag(C.Tag.browse_screen).assertIsDisplayed()
      return
    }
    if (screen == Screen.ChatList) {
      composeTestRule.onNodeWithTag(C.Tag.chat_list_screen).assertIsDisplayed()
      return
    }
    if (screen == Screen.Chat) {
      composeTestRule.onNodeWithTag(C.Tag.chat_screen).assertIsDisplayed()
      return
    }
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
  fun tappingAConversationOpensItsChat() {
    chatRepository.conversations.value =
        listOf(
            Conversation(
                "rental-1",
                "Camping tent",
                "owner-1",
                "Ana",
                "me",
                "Me",
                "Hi",
                Instant.EPOCH,
                RentalStatus.REQUESTED,
                LocalDate.of(2026, 10, 20),
                LocalDate.of(2026, 10, 22),
            )
        )
    setAppSignedIn()
    // Set after the start: a signed-in user would skip the sign-in screen that setAppSignedIn uses.
    // The Chat list reads the user when it opens.
    currentUser = AuthUser("me", "me@epfl.ch", "Me")
    openFromMenu(Screen.ChatList)

    waitForTag(C.Tag.chat_list_row_ + "rental-1")
    composeTestRule.onNodeWithTag(C.Tag.chat_list_row_ + "rental-1").performClick()

    assertCurrentScreen(Screen.Chat)
    // The chat of the tapped rental is open: its header shows the item.
    waitForTag(C.Tag.chat_header_item)
    composeTestRule.onNodeWithTag(C.Tag.chat_header_item).assertTextEquals("Camping tent")
  }

  @Test
  fun rentalDetailsInTheChatOpensTheRental() {
    chatRepository.conversations.value =
        listOf(
            Conversation(
                "rental-1",
                "Camping tent",
                "owner-1",
                "Ana",
                "me",
                "Me",
                "Hi",
                Instant.EPOCH,
                RentalStatus.REQUESTED,
                LocalDate.of(2026, 10, 20),
                LocalDate.of(2026, 10, 22),
            )
        )
    setAppSignedIn()
    currentUser = AuthUser("me", "me@epfl.ch", "Me")
    composeTestRule.runOnUiThread {
      NavigationActions(navController).navigateTo(Screen.Chat.createRoute("rental-1"))
    }

    waitForTag(C.Tag.chat_rental_details)
    composeTestRule.onNodeWithTag(C.Tag.chat_rental_details).performClick()

    assertCurrentScreen(
        Screen.RentalDetail,
        shownRoute = Screen.RentalDetail.createRoute("rental-1"),
    )
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
  fun menuBrowseFromListingDetailReturnsToBrowse() {
    setAppSignedIn()
    openListingDetail()

    openFromMenu(Screen.Browse)

    assertCurrentScreen(Screen.Browse)
    assertNull(navController.previousBackStackEntry)
  }

  @Test
  fun returningToBrowseAfterListingDetailDoesNotRestoreIt() {
    setAppSignedIn()
    openListingDetail()

    openFromMenu(Screen.Wallet)
    assertEquals(Screen.Browse.route, navController.previousBackStackEntry?.destination?.route)
    openFromMenu(Screen.Browse)

    assertCurrentScreen(Screen.Browse)
    assertNull(navController.previousBackStackEntry)
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
  fun listingDetailRouteShowsTheListing() {
    setAppSignedIn()

    openListingDetail()

    assertEquals(Screen.ListingDetail.route, navController.currentDestination?.route)
    composeTestRule.onNodeWithTag(C.Tag.listing_detail_title).assertTextEquals("Camping tent")
  }

  @Test
  fun requestDatesOnListingDetailOpensDatePicker() {
    setAppSignedIn()
    openListingDetail()

    composeTestRule.onNodeWithTag(C.Tag.listing_detail_request_button).performClick()

    composeTestRule.waitForIdle()
    assertEquals(Screen.ListingDetail.route, navController.currentDestination?.route)
    // The ViewModel already exists in this entry's store, so the provider returns that instance.
    val viewModel =
        ViewModelProvider(navController.currentBackStackEntry!!)[ListingDetailViewModel::class.java]
    assertTrue(viewModel.uiState.value.isDatePickerOpen)
  }

  @Test
  fun listingDetailForUnknownListingShowsNotFound() {
    setAppSignedIn()

    composeTestRule.runOnUiThread {
      NavigationActions(navController).navigateTo(Screen.ListingDetail.createRoute("missing-id"))
    }

    waitForTag(C.Tag.listing_detail_not_found)
    assertEquals(Screen.ListingDetail.route, navController.currentDestination?.route)
    composeTestRule.onNodeWithTag(C.Tag.listing_detail_title).assertDoesNotExist()
  }

  @Test
  fun startsOnBrowseWhenAlreadySignedIn() {
    currentUser = AuthUser("uid", "john@epfl.ch", "John Doe")

    setApp()

    assertCurrentScreen(Screen.Browse)
    assertNull(navController.previousBackStackEntry)
  }

  @Test
  fun browseShowsListingsFromTheRepository() {
    setAppSignedIn()

    waitForTag(C.Tag.listing_card_ + listing.id)
    composeTestRule.onNodeWithTag(C.Tag.listing_card_ + listing.id).assertIsDisplayed()
  }

  @Test
  fun tappingBrowseCardOpensItsListingDetailAndBackReturnsToBrowse() {
    setAppSignedIn()
    waitForTag(C.Tag.listing_card_ + listing.id)

    composeTestRule.onNodeWithTag(C.Tag.listing_card_ + listing.id).performClick()

    waitForTag(C.Tag.listing_detail_screen)
    assertEquals(
        listing.id,
        navController.currentBackStackEntry?.arguments?.getString(Screen.ARG_LISTING_ID),
    )
    composeTestRule.onNodeWithTag(C.Tag.listing_detail_title).assertTextEquals("Camping tent")

    composeTestRule.runOnUiThread { navController.popBackStack() }
    assertCurrentScreen(Screen.Browse)
  }

  @Test
  fun lenderCardOnListingDetailOpensOwnerProfile() {
    setAppSignedIn()
    openListingDetail()

    composeTestRule.onNodeWithTag(C.Tag.listing_detail_lender_card).performScrollTo().performClick()

    assertCurrentScreen(Screen.UserProfile, shownRoute = Screen.UserProfile.createRoute("owner-1"))
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
