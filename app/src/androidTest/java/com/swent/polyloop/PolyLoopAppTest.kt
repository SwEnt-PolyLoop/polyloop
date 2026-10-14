// Edited with Claude.

package com.swent.polyloop

import android.net.Uri
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingAddresses
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingRepository
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.model.profile.FakeProfileRepository
import com.swent.polyloop.model.profile.UserProfile
import com.swent.polyloop.model.rental.FakeRentalRepository
import com.swent.polyloop.model.rental.Rental
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

  private lateinit var backDispatcher: OnBackPressedDispatcher

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
        override suspend fun getListingsByOwner(ownerId: String): Result<List<Listing>> =
            Result.success(emptyList())

        override suspend fun getAllListings(): Result<List<Listing>> =
            Result.success(listOf(listing))

        override suspend fun getListing(id: String): Result<Listing?> =
            Result.success(listing.takeIf { it.id == id })

        override suspend fun createListing(
            listing: Listing,
            addresses: ListingAddresses,
            photos: List<Uri>,
        ): Result<String> = error("Not used in this test")
      }

  /** One rental borrowed by "uid", so My rentals shows a row without reaching Firestore. */
  private val rental =
      Rental(
          id = "rental-1",
          listingId = listing.id,
          listingTitle = "Camping tent",
          ownerId = listing.ownerId,
          ownerName = "Vida",
          borrowerId = "uid",
          borrowerName = "John Doe",
          startDate = LocalDate.of(2026, 10, 8),
          endDate = LocalDate.of(2026, 10, 10),
          status = RentalStatus.REQUESTED,
          pricePerDay = 15,
          totalPrice = 45,
          pickupArea = "Ecublens",
          dropOffArea = "Ecublens",
          createdAt = Instant.parse("2026-10-07T10:00:00Z"),
      )

  /** Online by default, so the tests never depend on the emulator's network. */
  private val connectivity = FakeConnectivityRepository(online = true)

  /** The signed-in user PolyLoopApp reads on start; null means nobody is signed in. */
  private var currentUser: AuthUser? = null

  private val me = AuthUser("me", "me@epfl.ch", "Me")

  /** What logging in on the Sign-up / login screen returns. */
  private var signInResult: AuthResult<AuthUser> = AuthResult.Failure(AuthError.UNKNOWN)

  private val authRepository =
      object : AuthRepository {
        override suspend fun signUp(name: String, email: String, password: String) =
            AuthResult.Success(Unit)

        override suspend fun signIn(email: String, password: String): AuthResult<AuthUser> =
            signInResult

        override suspend fun resendVerificationEmail(email: String, password: String) =
            AuthResult.Success(Unit)

        override fun getCurrentUser(): AuthUser? = currentUser

        override fun signOut() {
          currentUser = null
        }
      }

  /** In-memory profiles, so the Profile screens never reach Firestore. */
  private val profileRepository =
      FakeProfileRepository(
          listOf(
              UserProfile(uid = "me", name = "Me", email = "me@epfl.ch"),
              UserProfile(uid = "user-1", name = "User", email = "user@epfl.ch"),
              UserProfile(uid = "owner-1", name = "Owner", email = "owner@epfl.ch"),
          )
      )

  /** In-memory conversations, so the Chat list never reaches Firestore. */
  private val chatRepository = FakeChatRepository(currentUid = "me")

  @Before
  fun setUp() {
    RepositoryProvider.listingRepository = listingRepository
    RepositoryProvider.authRepository = authRepository
    RepositoryProvider.profileRepository = profileRepository
    RepositoryProvider.chatRepository = chatRepository
    RepositoryProvider.rentalRepository = FakeRentalRepository(rentals = listOf(rental))
    RepositoryProvider.connectivityRepository = connectivity
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
      backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
      navController = rememberNavController()
      PolyLoopApp(navController = navController, isAdmin = isAdmin)
    }
  }

  private fun waitForTag(tag: String) {
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
  }

  /** Starts the app with [me] already signed in, so it opens on Browse. */
  private fun setAppSignedIn(isAdmin: Boolean = false) {
    currentUser = me
    setApp(isAdmin)
  }

  /** Fills the log-in form of the Sign-up / login screen and presses "Log in". */
  private fun logIn() {
    composeTestRule
        .onNodeWithTag(C.Tag.auth_email_field)
        .performScrollTo()
        .performTextInput(me.email)
    composeTestRule
        .onNodeWithTag(C.Tag.auth_password_field)
        .performScrollTo()
        .performTextInput("pw")
    composeTestRule.onNodeWithTag(C.Tag.auth_login_button).performScrollTo().performClick()
  }

  private fun openFromMenu(screen: Screen.TopLevel) {
    composeTestRule.onNodeWithTag(C.Tag.top_bar_menu_button).performClick()
    composeTestRule.onNodeWithTag(C.Tag.topBarMenuItem(screen.route)).performClick()
  }

  private fun assertCurrentScreen(screen: Screen, shownRoute: String = screen.route) {
    composeTestRule.waitForIdle()
    assertEquals(screen.route, navController.currentDestination?.route)
    // These are wired to their real screens; the others still show DummyScreen with their route.
    val realScreenTag =
        when (screen) {
          Screen.Auth -> C.Tag.auth_login_button
          Screen.Browse -> C.Tag.browse_screen
          Screen.CreateListing -> C.Tag.create_listing_screen
          Screen.MyRentals -> C.Tag.my_rentals_screen
          Screen.ChatList -> C.Tag.chat_list_screen
          Screen.Profile,
          Screen.UserProfile -> C.Tag.profile_screen
          else -> null
        }
    if (realScreenTag != null) {
      composeTestRule.onNodeWithTag(realScreenTag).assertIsDisplayed()
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
  fun logInOpensBrowseAndRemovesSignInFromBackStack() {
    signInResult = AuthResult.Success(me)
    setApp()

    logIn()

    waitForTag(C.Tag.browse_screen)
    assertCurrentScreen(Screen.Browse)
    assertNull(navController.previousBackStackEntry)
    composeTestRule.onNodeWithTag(C.Tag.top_bar).assertIsDisplayed()
  }

  @Test
  fun logInNavigatesToBrowseOnce() {
    signInResult = AuthResult.Success(me)
    setApp()

    logIn()
    waitForTag(C.Tag.browse_screen)
    // Lets the sign-in route finish fading out: it must not report the sign-in a second time.
    composeTestRule.waitForIdle()

    composeTestRule.runOnIdle {
      assertEquals(
          listOf(Screen.Browse.route),
          navController.currentBackStack.value.mapNotNull { it.destination.route },
      )
    }
  }

  @Test
  fun verifiedOnCheckInboxNeverShowsTheFormWhileLeaving() {
    signInResult = AuthResult.Failure(AuthError.EMAIL_NOT_VERIFIED)
    setApp()
    logIn()
    waitForTag(C.Tag.auth_check_inbox_screen)

    signInResult = AuthResult.Success(me)
    composeTestRule.mainClock.autoAdvance = false
    composeTestRule.onNodeWithTag(C.Tag.auth_verified_button).performScrollTo().performClick()

    // Frame by frame through the fade to Browse: the sign-in route stays composed meanwhile.
    repeat(FADE_FRAMES) {
      composeTestRule.mainClock.advanceTimeByFrame()
      composeTestRule.onNodeWithTag(C.Tag.auth_login_button).assertDoesNotExist()
    }
    composeTestRule.mainClock.autoAdvance = true
    waitForTag(C.Tag.browse_screen)
  }

  @Test
  fun unverifiedLogInShowsCheckInboxOnTheSignInRoute() {
    signInResult = AuthResult.Failure(AuthError.EMAIL_NOT_VERIFIED)
    setApp()

    logIn()

    waitForTag(C.Tag.auth_check_inbox_screen)
    assertEquals(Screen.Auth.route, navController.currentDestination?.route)
    composeTestRule.onNodeWithTag(C.Tag.top_bar).assertDoesNotExist()
  }

  @Test
  fun backOnCheckInboxReturnsToTheForm() {
    signInResult = AuthResult.Failure(AuthError.EMAIL_NOT_VERIFIED)
    setApp()
    logIn()
    waitForTag(C.Tag.auth_check_inbox_screen)

    composeTestRule.onNodeWithTag(C.Tag.auth_back_to_form_button).performClick()

    assertCurrentScreen(Screen.Auth)
    composeTestRule.onNodeWithTag(C.Tag.auth_check_inbox_screen).assertDoesNotExist()
  }

  @Test
  fun verifiedOnCheckInboxOpensBrowse() {
    signInResult = AuthResult.Failure(AuthError.EMAIL_NOT_VERIFIED)
    setApp()
    logIn()
    waitForTag(C.Tag.auth_check_inbox_screen)

    signInResult = AuthResult.Success(me)
    composeTestRule.onNodeWithTag(C.Tag.auth_verified_button).performScrollTo().performClick()

    waitForTag(C.Tag.browse_screen)
    assertCurrentScreen(Screen.Browse)
    assertNull(navController.previousBackStackEntry)
  }

  @Test
  fun systemBackOnCheckInboxReturnsToTheForm() {
    signInResult = AuthResult.Failure(AuthError.EMAIL_NOT_VERIFIED)
    setApp()
    logIn()
    waitForTag(C.Tag.auth_check_inbox_screen)

    // Through the dispatcher, as Espresso.pressBack() waits for window focus and timed out on CI.
    composeTestRule.runOnUiThread { backDispatcher.onBackPressed() }

    assertCurrentScreen(Screen.Auth)
    composeTestRule.onNodeWithTag(C.Tag.auth_check_inbox_screen).assertDoesNotExist()
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
            )
        )
    setAppSignedIn()
    openFromMenu(Screen.ChatList)

    waitForTag(C.Tag.chat_list_row_ + "rental-1")
    composeTestRule.onNodeWithTag(C.Tag.chat_list_row_ + "rental-1").performClick()

    assertCurrentScreen(Screen.Chat, shownRoute = Screen.Chat.createRoute("rental-1"))
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
  fun openMyRentalsOnOfflineBrowseOpensMyRentals() {
    setAppSignedIn()
    connectivity.setOnline(false)
    waitForTag(C.Tag.browse_offline)

    composeTestRule.onNodeWithTag(C.Tag.browse_offline_my_rentals_button).performClick()

    assertCurrentScreen(Screen.MyRentals)
    // A top-level destination: Back returns to Browse.
    composeTestRule.runOnUiThread { navController.popBackStack() }
    assertCurrentScreen(Screen.Browse)
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

  @Test
  fun myRentalsShowsTheSignedInUsersRentals() {
    currentUser = AuthUser("uid", "john@epfl.ch", "John Doe")
    setApp()

    openFromMenu(Screen.MyRentals)

    waitForTag(C.Tag.my_rentals_row_ + rental.id)
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_row_ + rental.id).assertIsDisplayed()
  }

  @Test
  fun tappingARentalOpensItsRentalDetail() {
    currentUser = AuthUser("uid", "john@epfl.ch", "John Doe")
    setApp()
    openFromMenu(Screen.MyRentals)
    waitForTag(C.Tag.my_rentals_row_ + rental.id)

    composeTestRule.onNodeWithTag(C.Tag.my_rentals_row_ + rental.id).performClick()

    assertCurrentScreen(Screen.RentalDetail, Screen.RentalDetail.createRoute(rental.id))
  }

  @Test
  fun signOutFromProfileReturnsToSignInWithAnEmptyBackStack() {
    currentUser = AuthUser(uid = "me", email = "me@epfl.ch", name = "Me")
    setApp()
    composeTestRule.onNodeWithTag(C.Tag.top_bar_profile_button).performClick()
    waitForTag(C.Tag.profile_sign_out_button)

    composeTestRule.onNodeWithTag(C.Tag.profile_sign_out_button).performScrollTo().performClick()
    composeTestRule.waitForIdle()

    assertEquals(Screen.Auth.route, navController.currentDestination?.route)
    assertNull(navController.previousBackStackEntry)
    assertNull(currentUser)
    composeTestRule.onNodeWithTag(C.Tag.top_bar).assertDoesNotExist()
  }

  private companion object {
    // NavHost fades between routes in 700 ms; 60 frames of 16 ms cover it.
    const val FADE_FRAMES = 60
  }
}
