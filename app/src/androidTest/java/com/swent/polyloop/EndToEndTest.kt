// Made with Claude.

package com.swent.polyloop

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.swent.polyloop.model.RepositoryProvider
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingFields
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.model.listing.listingToDocument
import com.swent.polyloop.model.profile.ProfileRepositoryFirestore
import com.swent.polyloop.model.rental.RentalRepositoryFirestore
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.navigation.Screen
import com.swent.polyloop.utils.FirebaseEmulator
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Goes through the whole app as merged on main, with the real repositories pointed at the Firebase
 * emulators (Auth, Firestore, Storage). Only the network status is faked, so the test never depends
 * on the emulator's connection. Needs `firebase emulators:start`.
 *
 * Steps still done by the test instead of the UI, to move to the UI once the features exist:
 * publishing a listing (no camera yet) and sending a rental request (Request does not send it yet).
 */
@RunWith(AndroidJUnit4::class)
class EndToEndTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var navController: NavHostController

  private lateinit var backDispatcher: OnBackPressedDispatcher

  // A new account for each run, so accounts left by earlier runs never get in the way.
  private val email = "e2e-${UUID.randomUUID().toString().take(8)}@epfl.ch"

  // Another user's listing, with a made-up word in its title so a search finds only this one.
  private val lenderId = "lender-${UUID.randomUUID()}"
  private val titleWord = List(8) { ('a'..'z').random() }.joinToString("")

  // Three days in the same month, so the date picker shows the picked range and its buffer day.
  private val today = LocalDate.now()
  private val firstDay =
      if (today.plusDays(2).month == today.month) today else today.plusMonths(1).withDayOfMonth(1)

  private lateinit var listing: Listing

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  @Before
  fun setUp() {
    FirebaseEmulator.connect()
    // A session kept by an earlier test would skip the Sign-up / login screen.
    Firebase.auth.signOut()
    RepositoryProvider.reset()
    RepositoryProvider.connectivityRepository = FakeConnectivityRepository(online = true)
    // Written before the app starts: Browse loads the listings once, when it opens.
    listing = runBlocking { seedLenderAndListing() }
  }

  @After
  fun tearDown() {
    Firebase.auth.signOut()
    RepositoryProvider.reset()
  }

  @Test
  fun signUpSignOutAndLogInAgain() {
    setApp()
    assertRoute(Screen.Auth)
    composeTestRule.onNodeWithTag(C.Tag.top_bar).assertDoesNotExist()

    // Sign up: a non-EPFL email is refused on the form.
    composeTestRule.onNodeWithTag(C.Tag.auth_signup_tab).performClick()
    composeTestRule.onNodeWithTag(C.Tag.auth_name_field).performScrollTo().performTextInput(NAME)
    composeTestRule
        .onNodeWithTag(C.Tag.auth_email_field)
        .performScrollTo()
        .performTextInput("student@gmail.com")
    composeTestRule
        .onNodeWithTag(C.Tag.auth_password_field)
        .performScrollTo()
        .performTextInput(PASSWORD)
    composeTestRule.onNodeWithTag(C.Tag.auth_signup_button).performScrollTo().performClick()
    composeTestRule.onNodeWithTag(C.Tag.auth_email_error).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.auth_check_inbox_screen).assertDoesNotExist()

    composeTestRule.onNodeWithTag(C.Tag.auth_email_field).performTextReplacement(email)
    composeTestRule.onNodeWithTag(C.Tag.auth_email_error).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.auth_signup_button).performScrollTo().performClick()

    // Check your inbox: Resend waits, and "I've verified" before verifying stays here.
    waitForTag(C.Tag.auth_check_inbox_screen)
    composeTestRule.onNodeWithTag(C.Tag.auth_resend_button).assertIsNotEnabled()
    pressWhenEnabled(C.Tag.auth_verified_button)
    waitUntilEnabled(C.Tag.auth_verified_button)
    composeTestRule.onNodeWithTag(C.Tag.auth_check_inbox_screen).assertIsDisplayed()

    runBlocking { FirebaseEmulator.verifyEmail(email) }
    pressWhenEnabled(C.Tag.auth_verified_button)

    waitForTag(C.Tag.browse_screen)
    assertRoute(Screen.Browse)
    assertNull(navController.previousBackStackEntry)

    // The first verified log-in created the profile, with the name typed at sign-up.
    composeTestRule.onNodeWithTag(C.Tag.top_bar_profile_button).performClick()
    waitForText(C.Tag.profile_name, NAME)
    composeTestRule
        .onNodeWithTag(C.Tag.profile_listings_empty)
        .performScrollTo()
        .assertIsDisplayed()

    // Sign out: back on the form, with nothing to go back to.
    composeTestRule.onNodeWithTag(C.Tag.profile_sign_out_button).performScrollTo().performClick()
    waitForTag(C.Tag.auth_login_button)
    assertRoute(Screen.Auth)
    assertNull(navController.previousBackStackEntry)
    composeTestRule.onNodeWithTag(C.Tag.top_bar).assertDoesNotExist()
    assertNull(RepositoryProvider.authRepository.getCurrentUser())

    // Log in: a wrong password is refused, the right one opens Browse.
    composeTestRule.onNodeWithTag(C.Tag.auth_login_tab).performClick()
    composeTestRule.onNodeWithTag(C.Tag.auth_email_field).performScrollTo().performTextInput(email)
    composeTestRule
        .onNodeWithTag(C.Tag.auth_password_field)
        .performScrollTo()
        .performTextInput("wrong-password")
    composeTestRule.onNodeWithTag(C.Tag.auth_login_button).performScrollTo().performClick()
    waitForTag(C.Tag.auth_error)

    composeTestRule.onNodeWithTag(C.Tag.auth_password_field).performTextReplacement(PASSWORD)
    pressWhenEnabled(C.Tag.auth_login_button)

    waitForTag(C.Tag.browse_screen)
    assertRoute(Screen.Browse)
    assertEquals(email, RepositoryProvider.authRepository.getCurrentUser()?.email)
  }

  @Test
  fun browseFindsAListingAndOpensItsDetail() {
    setApp()
    signUpAndVerify()
    val card = C.Tag.listing_card_ + listing.id

    // Search: the made-up word finds the listing, an unknown word finds nothing.
    composeTestRule.onNodeWithTag(C.Tag.browse_search_bar).performTextInput(titleWord)
    waitForTag(card)
    composeTestRule.onNodeWithTag(C.Tag.browse_clear_search).performClick()
    composeTestRule.onNodeWithTag(C.Tag.browse_search_bar).performTextInput("zzqqxxnotaword")
    composeTestRule.onNodeWithTag(C.Tag.browse_empty).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.browse_clear_search).performClick()

    // Categories: another category hides it, "All" brings it back.
    composeTestRule.onNodeWithTag(C.Tag.browse_search_bar).performTextInput(titleWord)
    composeTestRule
        .onNodeWithTag(C.Tag.browseCategoryChip(ListingCategory.ELECTRONICS))
        .performScrollTo()
        .performClick()
    composeTestRule.onNodeWithTag(card).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.browse_category_all).performScrollTo().performClick()
    composeTestRule.onNodeWithTag(card).assertIsDisplayed()

    // The map toggle does nothing yet: the list stays.
    composeTestRule.onNodeWithTag(C.Tag.browse_map_toggle).performClick()
    composeTestRule.onNodeWithTag(C.Tag.browse_list_toggle).performClick()
    composeTestRule.onNodeWithTag(card).assertIsDisplayed()

    // Listing detail.
    composeTestRule.onNodeWithTag(card).performClick()
    waitForText(C.Tag.listing_detail_title, listing.title)
    assertRoute(Screen.ListingDetail)
    composeTestRule.onNodeWithTag(C.Tag.listing_detail_price).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(C.Tag.listing_detail_deposit)
        .performScrollTo()
        .assertIsDisplayed()

    // Request: pick two days; confirming closes the picker (sending the request comes later).
    composeTestRule.onNodeWithTag(C.Tag.listing_detail_request_button).performClick()
    waitForTag(C.Tag.date_picker_sheet)
    pickDays(firstDay, firstDay.plusDays(1))
    composeTestRule.onNodeWithTag(C.Tag.date_picker_summary_total).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.date_picker_confirm_button).performScrollTo().performClick()
    composeTestRule.onNodeWithTag(C.Tag.date_picker_sheet).assertDoesNotExist()
    assertRoute(Screen.ListingDetail)

    // The lender card opens the lender's profile; Back returns to the listing.
    composeTestRule.onNodeWithTag(C.Tag.listing_detail_lender_card).performScrollTo().performClick()
    waitForText(C.Tag.profile_name, LENDER_NAME)
    assertRoute(Screen.UserProfile)
    composeTestRule.runOnUiThread { backDispatcher.onBackPressed() }
    assertRoute(Screen.ListingDetail)
  }

  @Test
  fun rentalRequestShowsInMyRentalsAndChatList() {
    setApp()
    signUpAndVerify()
    // Sent by the test: the Request button does not send it yet.
    val borrower = requireNotNull(RepositoryProvider.authRepository.getCurrentUser())
    val rental = runBlocking {
      RentalRepositoryFirestore()
          .requestRental(listing, LENDER_NAME, borrower, firstDay, firstDay.plusDays(1))
          .getOrThrow()
    }
    val row = C.Tag.my_rentals_row_ + rental.id

    // My rentals: the request is under Borrowing, Lending is empty.
    openFromMenu(Screen.MyRentals)
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_tab_borrowing).performClick()
    waitForTag(row)
    inRow(row, C.Tag.my_rentals_row_title).assertTextEquals(listing.title)
    inRow(row, C.Tag.my_rentals_row_person)
        .assertTextEquals(context.getString(R.string.my_rentals_lender, LENDER_NAME))
    inRow(row, C.Tag.my_rentals_row_status)
        .assertTextEquals(context.getString(R.string.rental_status_requested))
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_tab_lending).performClick()
    waitForTag(C.Tag.my_rentals_empty)
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_tab_borrowing).performClick()
    waitForTag(row)
    composeTestRule.onNodeWithTag(row).performClick()
    assertRoute(Screen.RentalDetail)

    // Chat list: the rental's conversation opens its chat.
    openFromMenu(Screen.ChatList)
    val conversation = C.Tag.chat_list_row_ + rental.id
    waitForTag(conversation)
    composeTestRule.onNodeWithTag(conversation).performClick()
    assertRoute(Screen.Chat)
  }

  @Test
  fun createListingShowsEveryErrorUntilTheFormIsFilled() {
    setApp()
    signUpAndVerify()
    openFromMenu(Screen.CreateListing)
    waitForTag(C.Tag.create_listing_screen)

    composeTestRule.onNodeWithTag(C.Tag.create_listing_publish_button).performClick()
    FIELD_ERRORS.forEach { error ->
      composeTestRule.onNodeWithTag(error).performScrollTo().assertIsDisplayed()
    }

    fillField(C.Tag.create_listing_title_field, "Electric drill")
    fillField(C.Tag.create_listing_description_field, "Cordless, two batteries.")
    composeTestRule
        .onNodeWithTag(C.Tag.create_listing_category_field)
        .performScrollTo()
        .performClick()
    composeTestRule
        .onNodeWithTag(C.Tag.create_listing_category_option_ + ListingCategory.TOOLS_DIY.name)
        .performClick()
    fillField(C.Tag.create_listing_price_field, "4")
    fillField(C.Tag.create_listing_item_value_field, "90")
    composeTestRule
        .onNodeWithTag(C.Tag.create_listing_availability_field)
        .performScrollTo()
        .performClick()
    waitForTag(C.Tag.date_picker_sheet)
    pickDays(firstDay, firstDay.plusDays(1))
    composeTestRule.onNodeWithTag(C.Tag.date_picker_confirm_button).performScrollTo().performClick()
    composeTestRule.onNodeWithTag(C.Tag.date_picker_sheet).assertDoesNotExist()
    fillField(C.Tag.create_listing_pickup_field, "Route Cantonale 1, Ecublens")
    fillField(C.Tag.create_listing_drop_off_field, "Avenue Piccard 2, Lausanne")

    FIELD_ERRORS.forEach { error -> composeTestRule.onNodeWithTag(error).assertDoesNotExist() }
    // Only the photos are missing: "Add photo" does not open the camera yet.
    composeTestRule.onNodeWithTag(C.Tag.create_listing_add_photo).performScrollTo().performClick()
    // Checked by its text: on a phone the keyboard left open by the last field can cover it.
    composeTestRule
        .onNodeWithTag(C.Tag.create_listing_photos_hint)
        .assertTextEquals(
            context.resources.getQuantityString(
                R.plurals.create_listing_missing_photos,
                Listing.MIN_PHOTOS,
                Listing.MIN_PHOTOS,
            )
        )
    composeTestRule.onNodeWithTag(C.Tag.create_listing_publish_button).performClick()
    assertRoute(Screen.CreateListing)
  }

  @Test
  fun menuOpensEveryScreenAndBackReturnsToBrowse() {
    setApp()
    signUpAndVerify()

    Screen.topLevelDestinations(isAdmin = false)
        .filter { it != Screen.Browse }
        .forEach { screen ->
          openFromMenu(screen)
          assertRoute(screen)
          composeTestRule.runOnUiThread { backDispatcher.onBackPressed() }
          assertRoute(Screen.Browse)
        }
    composeTestRule.onNodeWithTag(C.Tag.top_bar_menu_button).performClick()
    composeTestRule
        .onNodeWithTag(C.Tag.topBarMenuItem(Screen.AdminCourtRuling.route))
        .assertDoesNotExist()
  }

  private fun openFromMenu(screen: Screen.TopLevel) {
    composeTestRule.onNodeWithTag(C.Tag.top_bar_menu_button).performClick()
    composeTestRule.onNodeWithTag(C.Tag.topBarMenuItem(screen.route)).performClick()
  }

  private fun fillField(tag: String, text: String) {
    composeTestRule.onNodeWithTag(tag).performScrollTo().performTextInput(text)
  }

  /** The node [tag] inside the row [rowTag], whose texts are merged into the row. */
  private fun inRow(rowTag: String, tag: String) =
      composeTestRule.onNode(
          hasTestTag(tag) and hasAnyAncestor(hasTestTag(rowTag)),
          useUnmergedTree = true,
      )

  /** Signs up [email] through the UI, verifies it in the emulator and logs in, ending on Browse. */
  private fun signUpAndVerify() {
    composeTestRule.onNodeWithTag(C.Tag.auth_signup_tab).performClick()
    composeTestRule.onNodeWithTag(C.Tag.auth_name_field).performScrollTo().performTextInput(NAME)
    composeTestRule.onNodeWithTag(C.Tag.auth_email_field).performScrollTo().performTextInput(email)
    composeTestRule
        .onNodeWithTag(C.Tag.auth_password_field)
        .performScrollTo()
        .performTextInput(PASSWORD)
    composeTestRule.onNodeWithTag(C.Tag.auth_signup_button).performScrollTo().performClick()
    waitForTag(C.Tag.auth_check_inbox_screen)
    runBlocking { FirebaseEmulator.verifyEmail(email) }
    pressWhenEnabled(C.Tag.auth_verified_button)
    waitForTag(C.Tag.browse_screen)
  }

  /** The lender's profile and a published listing of theirs, as the app would have saved them. */
  private suspend fun seedLenderAndListing(): Listing {
    ProfileRepositoryFirestore()
        .createProfileIfMissing(lenderId, "$lenderId@epfl.ch", LENDER_NAME)
        .getOrThrow()
    val listing =
        Listing(
            id = "",
            ownerId = lenderId,
            title = "Camping tent $titleWord",
            description = "Light 2-person tent.",
            category = ListingCategory.SPORTS_OUTDOOR,
            photoUrls = emptyList(),
            pricePerDay = 5,
            itemValue = 120,
            availableFrom = firstDay,
            availableTo = firstDay.plusDays(20),
            status = ListingStatus.PUBLISHED,
            pickupArea = "Ecublens",
            dropOffArea = "Renens",
        )
    val id =
        Firebase.firestore
            .collection(ListingFields.COLLECTION)
            .add(listingToDocument(listing))
            .await()
            .id
    return listing.copy(id = id)
  }

  /**
   * Taps [start] then [end] in the open date picker, moving up to [MAX_MONTHS_AHEAD] months later
   * as needed. A day never shown fails the tap instead of looping forever.
   */
  private fun pickDays(start: LocalDate, end: LocalDate) {
    listOf(start, end).forEach { day ->
      val tag = C.Tag.datePickerDay(day)
      repeat(MAX_MONTHS_AHEAD) {
        if (composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) {
          composeTestRule.onNodeWithTag(C.Tag.date_picker_next_month).performClick()
        }
      }
      composeTestRule.onNodeWithTag(tag).performScrollTo().performClick()
    }
  }

  private fun setApp() {
    composeTestRule.setContent {
      backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
      navController = rememberNavController()
      PolyLoopApp(navController = navController)
    }
  }

  private fun assertRoute(screen: Screen) {
    composeTestRule.waitForIdle()
    assertEquals(screen.route, navController.currentDestination?.route)
  }

  /** Waits for [tag] to be shown; Firebase calls take longer than in-memory fakes. */
  private fun waitForTag(tag: String) = waitFor(hasTestTag(tag))

  private fun waitForText(tag: String, text: String) {
    waitFor(hasTestTag(tag) and hasText(text))
  }

  private fun waitUntilEnabled(tag: String) = waitFor(hasTestTag(tag) and isEnabled())

  /** Waits for the button [tag] to accept a press, e.g. after a request ends, then presses it. */
  private fun pressWhenEnabled(tag: String) {
    waitUntilEnabled(tag)
    composeTestRule.onNodeWithTag(tag).performScrollTo().performClick()
    composeTestRule.waitForIdle()
  }

  private fun waitFor(matcher: SemanticsMatcher) {
    composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
      composeTestRule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private companion object {
    const val NAME = "Eve Student"
    const val LENDER_NAME = "Lena"

    /** The error of each required field of Create listing, shown while the field is empty. */
    val FIELD_ERRORS =
        listOf(
            C.Tag.create_listing_title_error,
            C.Tag.create_listing_description_error,
            C.Tag.create_listing_category_error,
            C.Tag.create_listing_price_error,
            C.Tag.create_listing_item_value_error,
            C.Tag.create_listing_availability_error,
            C.Tag.create_listing_pickup_error,
            C.Tag.create_listing_drop_off_error,
        )
    const val PASSWORD = "Password123!"
    const val TIMEOUT_MS = 15_000L

    /** The test's days are at most one month ahead; a few more months leaves room. */
    const val MAX_MONTHS_AHEAD = 3
  }
}
