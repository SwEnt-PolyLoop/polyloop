// Made with Claude.

package com.swent.polyloop

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
import com.google.firebase.firestore.snapshots
import com.swent.polyloop.model.RepositoryProvider
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingFields
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.model.listing.listingToDocument
import com.swent.polyloop.model.profile.ProfileFields
import com.swent.polyloop.model.rental.RentalFields
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.navigation.Screen
import com.swent.polyloop.utils.FirebaseEmulator
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
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

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private lateinit var navController: NavHostController

  private lateinit var backDispatcher: OnBackPressedDispatcher

  // A new account for each run, so accounts left by earlier runs never get in the way.
  private val email = "e2e-${UUID.randomUUID().toString().take(8)}@epfl.ch"

  // Another user's listing, with a made-up word in its title so a search finds only this one.
  private val lenderId = "lender-${UUID.randomUUID()}"
  private val titleWord = List(8) { ('a'..'z').random() }.joinToString("")

  // The listing is available four days in the same month, so the date picker shows them all. It
  // starts tomorrow at the earliest: today is a pickup day, and a run that goes past midnight
  // still finds the first day selectable.
  private val firstDay =
      LocalDate.now().plusDays(1).let {
        if (it.plusDays(3).month == it.month) it else it.plusMonths(1).withDayOfMonth(1)
      }
  private val lastDay = firstDay.plusDays(3)

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  // Paths of the documents written by the test itself, deleted after each test.
  private val seededDocs = mutableListOf<String>()

  @Before
  fun setUp() {
    FirebaseEmulator.connect()
    // A session kept by an earlier test would skip the Sign-up / login screen.
    Firebase.auth.signOut()
    RepositoryProvider.reset()
    RepositoryProvider.connectivityRepository = FakeConnectivityRepository(online = true)
  }

  @After
  fun tearDown() {
    // Closes the app first: its listeners and auth navigation would react to the deletes and the
    // sign-out below.
    composeTestRule.activityRule.scenario.close()
    // Best effort, but bounded: a delete waits for the server, so a lost emulator would hang here.
    runBlocking {
      withTimeoutOrNull(CLEANUP_TIMEOUT_MS) {
        // As an admin: the rules forbid the borrower to delete other people's data or a rental.
        seededDocs.forEach { path ->
          bestEffort {
            runInterruptible(Dispatchers.IO) { FirebaseEmulator.deleteDocumentAsAdmin(path) }
          }
        }
        deleteAccount()
      }
    }
    Firebase.auth.signOut()
    RepositoryProvider.reset()
  }

  /** Deletes the test's account and its profile, if the test got as far as signing up. */
  private suspend fun deleteAccount() {
    bestEffort {
      // Usually still signed in from the test; signs in again only if not.
      val user =
          Firebase.auth.currentUser?.takeIf { it.email == email }
              ?: Firebase.auth.signInWithEmailAndPassword(email, PASSWORD).await().user
      if (user != null) {
        runInterruptible(Dispatchers.IO) {
          FirebaseEmulator.deleteDocumentAsAdmin("${ProfileFields.COLLECTION}/${user.uid}")
        }
        user.delete().await()
      }
    }
  }

  /**
   * Runs [block] and ignores its failure, but not a cancellation: the cleanup timeout must still
   * stop the remaining steps.
   */
  private suspend fun bestEffort(block: suspend () -> Unit) {
    try {
      block()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Cleanup step failed", e)
    }
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
    // Waits for the log-in attempt to end: it asks to verify the email, and the screen stays.
    waitForText(C.Tag.auth_error, context.getString(R.string.auth_error_email_not_verified))
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
    waitForText(C.Tag.auth_error, context.getString(R.string.auth_error_wrong_credentials))

    composeTestRule.onNodeWithTag(C.Tag.auth_password_field).performTextReplacement(PASSWORD)
    pressWhenEnabled(C.Tag.auth_login_button)

    waitForTag(C.Tag.browse_screen)
    assertRoute(Screen.Browse)
    assertEquals(email, RepositoryProvider.authRepository.getCurrentUser()?.email)
  }

  @Test
  fun browseFindsAListingAndOpensItsDetail() {
    val listing = seedLenderAndListing()
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
    waitForTag(card)
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

    // Request: ending on the last available day leaves no free day after it, so it is refused.
    composeTestRule.onNodeWithTag(C.Tag.listing_detail_request_button).performClick()
    waitForTag(C.Tag.date_picker_sheet)
    pickDays(firstDay, lastDay)
    composeTestRule.onNodeWithTag(C.Tag.date_picker_error).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.date_picker_confirm_button).assertIsNotEnabled()

    // Two days instead: both picked, priced at two days; confirming closes the picker (sending
    // the request comes later).
    pickDay(firstDay.plusDays(1))
    composeTestRule.onNodeWithTag(C.Tag.date_picker_error).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.datePickerDay(firstDay)).assertIsSelected()
    composeTestRule.onNodeWithTag(C.Tag.datePickerDay(firstDay.plusDays(1))).assertIsSelected()
    composeTestRule.onNodeWithTag(C.Tag.datePickerDay(lastDay)).assertIsNotSelected()
    composeTestRule
        .onNodeWithTag(C.Tag.date_picker_summary_total)
        .assertTextEquals(context.getString(R.string.listing_card_price, 2 * PRICE_PER_DAY))
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
    val listing = seedLenderAndListing()
    setApp()
    signUpAndVerify()
    // Sent by the test: the Request button does not send it yet.
    val borrower = requireNotNull(RepositoryProvider.authRepository.getCurrentUser())
    val rental = runBlocking {
      RepositoryProvider.rentalRepository
          .requestRental(listing, LENDER_NAME, borrower, firstDay, firstDay.plusDays(1))
          .getOrThrow()
    }
    seededDocs += "${RentalFields.COLLECTION}/${rental.id}"
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
    assertOpenedFor(Screen.RentalDetail, Screen.RentalDetail.createRoute(rental.id))

    // Chat list: the rental's conversation opens its chat.
    openFromMenu(Screen.ChatList)
    val conversation = C.Tag.chat_list_row_ + rental.id
    waitForTag(conversation)
    composeTestRule.onNodeWithTag(conversation).performClick()
    assertOpenedFor(Screen.Chat, Screen.Chat.createRoute(rental.id))
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
    // Publish is refused without photos: the form stays and nothing reaches Firestore.
    composeTestRule.onNodeWithTag(C.Tag.create_listing_publish_button).performClick()
    assertRoute(Screen.CreateListing)
    val uid = requireNotNull(RepositoryProvider.authRepository.getCurrentUser()).uid
    // A wrongly accepted Publish would write later, from a coroutine: keep watching for a while.
    val ownListing = runBlocking {
      withTimeoutOrNull(NO_WRITE_WAIT_MS) {
        Firebase.firestore
            .collection(ListingFields.COLLECTION)
            .whereEqualTo(ListingFields.OWNER_ID, uid)
            .snapshots()
            .first { !it.isEmpty }
      }
    }
    assertNull(ownListing)
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

  /**
   * The lender's profile and a published listing of theirs, as the app would have saved them.
   * Written as an admin: the rules let only the lender write them. Called before [setApp]: Browse
   * loads the listings once, when it opens.
   */
  private fun seedLenderAndListing(): Listing {
    val profilePath = "${ProfileFields.COLLECTION}/$lenderId"
    FirebaseEmulator.createDocumentAsAdmin(
        profilePath,
        mapOf(
            ProfileFields.NAME to LENDER_NAME,
            ProfileFields.EMAIL to "$lenderId@epfl.ch",
            ProfileFields.PHOTO_URL to "",
        ),
    )
    seededDocs += profilePath
    val listing =
        Listing(
            id = UUID.randomUUID().toString(),
            ownerId = lenderId,
            title = "Camping tent $titleWord",
            description = "Light 2-person tent.",
            category = ListingCategory.SPORTS_OUTDOOR,
            photoUrls = emptyList(),
            pricePerDay = PRICE_PER_DAY,
            itemValue = 120,
            availableFrom = firstDay,
            availableTo = lastDay,
            status = ListingStatus.PUBLISHED,
            pickupArea = "Ecublens",
            dropOffArea = "Renens",
        )
    val listingPath = "${ListingFields.COLLECTION}/${listing.id}"
    FirebaseEmulator.createDocumentAsAdmin(listingPath, listingToDocument(listing))
    seededDocs += listingPath
    return listing
  }

  /** Taps [start] then [end] in the open date picker. */
  private fun pickDays(start: LocalDate, end: LocalDate) {
    pickDay(start)
    pickDay(end)
  }

  /**
   * Taps [day] in the open date picker, moving to its month with the arrows. Fails with a clear
   * message if the picker cannot show that month, i.e. [day] is outside the window.
   */
  private fun pickDay(day: LocalDate) {
    val target = YearMonth.from(day)
    var shown = shownMonth()
    while (shown != target) {
      val arrow = if (shown < target) C.Tag.date_picker_next_month else C.Tag.date_picker_prev_month
      if (
          composeTestRule
              .onAllNodes(hasTestTag(arrow) and isEnabled())
              .fetchSemanticsNodes()
              .isEmpty()
      ) {
        fail("The date picker shows $shown and cannot move towards $day")
      }
      composeTestRule.onNodeWithTag(arrow).performClick()
      shown = shownMonth()
    }
    composeTestRule.onNodeWithTag(C.Tag.datePickerDay(day)).performScrollTo().performClick()
  }

  /** The month the open date picker shows, read from the tag of one of its days. */
  private fun shownMonth(): YearMonth {
    val dayTag = C.Tag.datePickerDay(firstDay).removeSuffix(firstDay.toString())
    val tag =
        composeTestRule
            .onAllNodes(SemanticsMatcher("is a day") { it.testTag()?.startsWith(dayTag) == true })
            .fetchSemanticsNodes()
            .first()
            .testTag()!!
    return YearMonth.from(LocalDate.parse(tag.removePrefix(dayTag)))
  }

  private fun SemanticsNode.testTag(): String? = config.getOrNull(SemanticsProperties.TestTag)

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

  /**
   * Checks that [screen] is open for the rental of [route]. It is still a stand-in, which shows the
   * route it was opened with: a wrong rental id would show here.
   */
  private fun assertOpenedFor(screen: Screen, route: String) {
    assertRoute(screen)
    composeTestRule.onNodeWithTag(C.Tag.dummy_screen_text).assertTextEquals(route)
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
    const val TAG = "EndToEndTest"
    const val NAME = "Eve Student"
    const val LENDER_NAME = "Lena"
    const val PRICE_PER_DAY = 5

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
    const val CLEANUP_TIMEOUT_MS = 30_000L

    /** How long a refused Publish is watched, to be sure it writes nothing. */
    const val NO_WRITE_WAIT_MS = 3_000L
  }
}
