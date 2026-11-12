// Made with Claude.

package com.swent.polyloop.e2e

import android.util.Log
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.swent.polyloop.PolyLoopApp
import com.swent.polyloop.model.RepositoryProvider
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.listing.BookedRange
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingFields
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.model.listing.listingToDocument
import com.swent.polyloop.model.profile.ProfileFields
import com.swent.polyloop.model.rental.InMemoryServerClockStore
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalFields
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.model.rental.ServerClock
import com.swent.polyloop.model.wallet.WalletFields
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.navigation.Screen
import com.swent.polyloop.utils.FirebaseEmulator
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule

/**
 * What every end-to-end test shares: the whole app as merged on main, with the real repositories
 * pointed at the Firebase emulators (Auth, Firestore, Storage). Only the network status is faked
 * ([connectivity]), so a test can go offline without losing the emulators, and the server clock
 * keeps its time in memory. Needs `firebase emulators:start`.
 *
 * The CI starts no Functions emulator, so what a Cloud Function would write (another user's data,
 * an offer, a dispute, a wallet) is written by the test as an admin, and the steps that call a
 * Function (sending an offer, accepting, proposing a split...) are not covered yet.
 *
 * Each test signs up a new account ([email]), and the cleanup deletes it with everything the test
 * wrote, so runs never get in each other's way.
 */
abstract class EndToEndTestBase {

  @get:Rule val composeTestRule = createComposeRule()

  // Set to false before the cleanup, to take the app off the screen.
  private var showApp by mutableStateOf(true)

  protected lateinit var navController: NavHostController

  private lateinit var backDispatcher: OnBackPressedDispatcher

  /** The network status the app sees; Firestore itself stays online unless [goOffline] is used. */
  protected val connectivity = FakeConnectivityRepository(online = true)

  protected val context = InstrumentationRegistry.getInstrumentation().targetContext!!

  // A new account for each run, so accounts left by earlier runs never get in the way.
  protected val email = "e2e-${UUID.randomUUID().toString().take(8)}@epfl.ch"

  // Other users: only their profiles and data exist, written as an admin, never an account.
  protected val lenderId = "lender-${UUID.randomUUID()}"
  protected val borrowerId = "borrower-${UUID.randomUUID()}"

  // A made-up word in the titles, so a search finds only this test's listings.
  protected val titleWord = List(8) { ('a'..'z').random() }.joinToString("")

  // The first day a listing is available. It is tomorrow at the earliest: today is a pickup day,
  // and a run that goes past midnight still finds it selectable. The test's days, up to
  // LAST_TEST_DAY days later, fall in the same month, so the date picker shows them all at once.
  protected val firstDay: LocalDate =
      LocalDate.now().plusDays(1).let {
        if (it.plusDays(LAST_TEST_DAY).month == it.month) it else it.plusMonths(1).withDayOfMonth(1)
      }

  // Paths of the documents and collections written by the test or by the app, deleted after it.
  private val seededDocs = mutableListOf<String>()
  private val seededCollections = mutableListOf<String>()

  @Before
  fun setUpEndToEnd() {
    FirebaseEmulator.connect()
    // A session kept by an earlier test would skip the Sign-up / login screen.
    Firebase.auth.signOut()
    RepositoryProvider.reset()
    RepositoryProvider.connectivityRepository = connectivity
    // Saves in memory, so no test writes a server time to the app's real preferences.
    RepositoryProvider.serverClock =
        ServerClock(
            RepositoryProvider.rentalRepository,
            InMemoryServerClockStore(),
            bootCount = { 0 },
        )
  }

  @After
  fun tearDownEndToEnd() {
    try {
      // Takes the app off the screen first, so its screens and auth navigation do not react to
      // the deletes and the sign-out below.
      composeTestRule.runOnIdle { showApp = false }
      composeTestRule.waitForIdle()
    } finally {
      // Only on the emulators: if connect() failed, Firebase still points at the real project.
      if (FirebaseEmulator.connected) deleteTestData()
      RepositoryProvider.reset()
    }
  }

  /** Deletes what the test wrote, then signs out. */
  private fun deleteTestData() {
    // Best effort, but bounded: a delete waits for the server, so a lost emulator would hang here.
    runBlocking {
      withTimeoutOrNull(CLEANUP_TIMEOUT_MS) {
        // A test that went offline leaves Firestore's network off for the next tests otherwise.
        bestEffort { Firebase.firestore.enableNetwork().await() }
        // The requests the app sent for the user: their ids are only known once their chat opens,
        // so a test that failed before would leave them behind.
        bestEffort {
          val uid = Firebase.auth.currentUser?.uid ?: return@bestEffort
          Firebase.firestore
              .collection(RentalFields.COLLECTION)
              .whereEqualTo(RentalFields.BORROWER_ID, uid)
              .get(Source.SERVER)
              .await()
              .documents
              .forEach { trackRental(it.id) }
        }
        // As an admin: the rules forbid the user to delete other people's data or a rental.
        seededCollections.forEach { path ->
          bestEffort {
            runInterruptible(Dispatchers.IO) { FirebaseEmulator.deleteCollectionAsAdmin(path) }
          }
        }
        seededDocs.forEach { path ->
          bestEffort {
            runInterruptible(Dispatchers.IO) { FirebaseEmulator.deleteDocumentAsAdmin(path) }
          }
        }
        deleteAccount(email)
      }
    }
    Firebase.auth.signOut()
  }

  /** Shows the whole app, as MainActivity does. */
  protected fun setApp() {
    composeTestRule.setContent {
      if (showApp) {
        backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
        navController = rememberNavController()
        PolyLoopApp(navController = navController)
      }
    }
  }

  /**
   * Creates the verified account [email] with its profile, without the UI, and leaves it signed in:
   * [setApp] then opens on Browse. For the tests whose subject is not signing up. Returns its uid.
   */
  protected fun signInWithoutTheUi(): String {
    val uid = runBlocking { FirebaseEmulator.signUpVerified(email, PASSWORD) }
    // The profile the first verified log-in would have created; deleted with the account.
    FirebaseEmulator.createDocumentAsAdmin(
        "${ProfileFields.COLLECTION}/$uid",
        profileFields(USER_NAME, email),
    )
    return uid
  }

  /** Writes the profile of another user [uid], as the app would have saved it. */
  protected fun seedProfile(uid: String, name: String, rating: Double = 0.0, reviews: Int = 0) {
    val path = "${ProfileFields.COLLECTION}/$uid"
    FirebaseEmulator.createDocumentAsAdmin(
        path,
        profileFields(name, "$uid@epfl.ch", rating, reviews),
    )
    seededDocs += path
  }

  private fun profileFields(name: String, email: String, rating: Double = 0.0, reviews: Int = 0) =
      mapOf(
          ProfileFields.NAME to name,
          ProfileFields.EMAIL to email,
          ProfileFields.PHOTO_URL to "",
          ProfileFields.AVERAGE_RATING to rating,
          ProfileFields.REVIEW_COUNT to reviews,
      )

  /**
   * A listing of [ownerId], as the app and the Cloud Functions would have saved it, available from
   * [from] to [to]. Called before [setApp] when Browse must show it: Browse loads the listings when
   * it opens.
   */
  protected fun seedListing(
      ownerId: String = lenderId,
      title: String = "Camping tent $titleWord",
      status: ListingStatus = ListingStatus.PUBLISHED,
      from: LocalDate = firstDay,
      to: LocalDate = firstDay.plusDays(3),
      bookedRanges: List<BookedRange> = emptyList(),
  ): Listing {
    val listing =
        Listing(
            id = UUID.randomUUID().toString(),
            ownerId = ownerId,
            title = title,
            description = "Light 2-person tent.",
            category = ListingCategory.SPORTS_OUTDOOR,
            photoUrls = emptyList(),
            pricePerDay = PRICE_PER_DAY,
            itemValue = ITEM_VALUE,
            availableFrom = from,
            availableTo = to,
            status = status,
            pickupArea = "Ecublens",
            dropOffArea = "Renens",
            bookedRanges = bookedRanges,
        )
    val path = "${ListingFields.COLLECTION}/${listing.id}"
    FirebaseEmulator.createDocumentAsAdmin(
        path,
        listingToDocument(listing) +
            (ListingFields.BOOKED_RANGES to bookedRangesFields(bookedRanges)),
    )
    seededDocs += path
    return listing
  }

  /** The Firestore value of [ranges], as the Cloud Function that accepts rentals writes it. */
  protected fun bookedRangesFields(ranges: List<BookedRange>) = ranges.map {
    mapOf(
        ListingFields.RANGE_START to it.start.toString(),
        ListingFields.RANGE_END to it.end.toString(),
    )
  }

  /**
   * A rental of [listing] by [borrower] from [start] to [end], as the app and the Cloud Functions
   * would have saved it at [status]. [createdAt] orders the rentals on My rentals; [extra] adds or
   * replaces fields, e.g. an offer or a deposit. Returns its id.
   */
  protected fun seedRental(
      listing: Listing,
      borrower: String,
      borrowerName: String,
      ownerName: String,
      start: LocalDate,
      end: LocalDate,
      status: RentalStatus,
      createdAt: Instant = Instant.now(),
      extra: Map<String, Any?> = emptyMap(),
  ): String {
    val id = UUID.randomUUID().toString()
    val created = timestamp(createdAt)
    val fields =
        mapOf(
            RentalFields.LISTING_ID to listing.id,
            RentalFields.LISTING_TITLE to listing.title,
            RentalFields.LISTING_PHOTO_URL to "",
            RentalFields.OWNER_ID to listing.ownerId,
            RentalFields.OWNER_NAME to ownerName,
            RentalFields.BORROWER_ID to borrower,
            RentalFields.BORROWER_NAME to borrowerName,
            RentalFields.PARTICIPANTS to listOf(listing.ownerId, borrower),
            RentalFields.START_DATE to start.toString(),
            RentalFields.END_DATE to end.toString(),
            RentalFields.STATUS to status.name,
            RentalFields.PRICE_PER_DAY to listing.pricePerDay,
            RentalFields.TOTAL_PRICE to Rental.totalPrice(listing.pricePerDay, start, end),
            RentalFields.PICKUP_AREA to listing.pickupArea,
            RentalFields.DROP_OFF_AREA to listing.dropOffArea,
            RentalFields.PICKUP_ADDRESS to "",
            RentalFields.RETURN_ADDRESS to "",
            RentalFields.CREATED_AT to created,
            RentalFields.LAST_MESSAGE to "",
            RentalFields.LAST_MESSAGE_AT to created,
        ) + extra
    FirebaseEmulator.createDocumentAsAdmin("${RentalFields.COLLECTION}/$id", fields)
    trackRental(id)
    return id
  }

  /** Deletes the rental [id] and its messages after the test, including one the app created. */
  protected fun trackRental(id: String) {
    seededDocs += "${RentalFields.COLLECTION}/$id"
    seededCollections += "${RentalFields.COLLECTION}/$id/messages"
  }

  /**
   * The wallet of [uid], in PP, as the Cloud Functions write it. Returns its path, under which the
   * history goes. It must not exist yet: a Function that creates wallets (`createWalletOnProfile`)
   * would make this fail once the CI runs the Functions emulator.
   */
  protected fun seedWallet(
      uid: String,
      available: Int,
      heldAsDeposits: Int = 0,
      heldForRentals: Int = 0,
  ): String {
    val path = "${WalletFields.COLLECTION}/$uid"
    FirebaseEmulator.createDocumentAsAdmin(
        path,
        mapOf(
            WalletFields.AVAILABLE to available,
            WalletFields.HELD_AS_DEPOSITS to heldAsDeposits,
            WalletFields.HELD_FOR_RENTALS to heldForRentals,
        ),
    )
    trackDocument(path)
    return path
  }

  /** Deletes the document at [path], written by the test as an admin, after the test. */
  protected fun trackDocument(path: String) {
    seededDocs += path
  }

  /** Cuts Firestore off the network and tells the app it is offline, like a phone losing it. */
  protected fun goOffline() {
    runBlocking { Firebase.firestore.disableNetwork().await() }
    connectivity.setOnline(false)
    composeTestRule.waitForIdle()
  }

  /** Undoes [goOffline]. */
  protected fun goOnline() {
    runBlocking { Firebase.firestore.enableNetwork().await() }
    connectivity.setOnline(true)
    composeTestRule.waitForIdle()
  }

  protected fun timestamp(instant: Instant) = Timestamp(instant.epochSecond, instant.nano)

  protected fun openFromMenu(screen: Screen.TopLevel) {
    composeTestRule.onNodeWithTag(C.Tag.top_bar_menu_button).performClick()
    composeTestRule.onNodeWithTag(C.Tag.drawerItem(screen.route)).performScrollTo().performClick()
  }

  /** Presses the system Back button. */
  protected fun pressBack() {
    composeTestRule.runOnUiThread { backDispatcher.onBackPressed() }
    composeTestRule.waitForIdle()
  }

  protected fun assertRoute(screen: Screen) {
    composeTestRule.waitForIdle()
    assertEquals(screen.route, navController.currentDestination?.route)
  }

  protected fun fillField(tag: String, text: String) {
    composeTestRule.onNodeWithTag(tag).performScrollTo().performTextInput(text)
  }

  /** The node [tag] inside the node [parentTag], e.g. a text merged into its row. */
  protected fun inNode(parentTag: String, tag: String): SemanticsNodeInteraction =
      composeTestRule.onNode(
          hasTestTag(tag) and hasAnyAncestor(hasTestTag(parentTag)),
          useUnmergedTree = true,
      )

  /** Waits for [tag] to be shown; Firebase calls take longer than in-memory fakes. */
  protected fun waitForTag(tag: String) = waitFor(hasTestTag(tag))

  protected fun waitForText(tag: String, text: String) = waitFor(hasTestTag(tag) and hasText(text))

  protected fun waitUntilEnabled(tag: String) = waitFor(hasTestTag(tag) and isEnabled())

  /** Waits for the button [tag] to accept a press, e.g. after a request ends, then presses it. */
  protected fun pressWhenEnabled(tag: String) {
    waitUntilEnabled(tag)
    composeTestRule.onNodeWithTag(tag).performScrollTo().performClick()
    composeTestRule.waitForIdle()
  }

  protected fun waitFor(matcher: SemanticsMatcher) {
    composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
      composeTestRule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
  }

  /** Taps [start] then [end] in the open date picker. */
  protected fun pickDays(start: LocalDate, end: LocalDate) {
    pickDay(start)
    pickDay(end)
  }

  /**
   * Taps [day] in the open date picker, moving to its month with the arrows. Fails with a clear
   * message if the picker cannot show that month, i.e. [day] is outside the window.
   */
  protected fun pickDay(day: LocalDate) {
    showMonthOf(day)
    composeTestRule.onNodeWithTag(C.Tag.datePickerDay(day)).performScrollTo().performClick()
  }

  /** Moves the open date picker to the month of [day] with its arrows. */
  protected fun showMonthOf(day: LocalDate) {
    val target = YearMonth.from(day)
    var shown = shownMonth()
    var moves = 0
    while (shown != target) {
      // A click that does not change the month would otherwise loop forever.
      if (moves++ == MAX_MONTH_MOVES) fail("The date picker never reached the month of $day")
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

  /** What the end-to-end tests share, [AppStartEndToEndTest] included. */
  internal companion object {
    const val TAG = "EndToEndTest"
    // Roles, not first names: the signed-in user, and the other users of the test data.
    const val USER_NAME = "Signed-in user"
    const val LENDER_NAME = "Lender"
    const val BORROWER_NAME = "Borrower"
    const val PASSWORD = "Password123!"
    const val PRICE_PER_DAY = 5
    const val ITEM_VALUE = 120
    const val TIMEOUT_MS = 15_000L
    const val CLEANUP_TIMEOUT_MS = 30_000L

    /** How many days after [firstDay] the tests use at most. */
    const val LAST_TEST_DAY = 9L

    /** The test's days are at most two months away; a year leaves plenty of room. */
    const val MAX_MONTH_MOVES = 12

    /**
     * Deletes the account [email] and its profile, if the test got as far as signing up. Signs in
     * again only if [email] is not the signed-in user.
     */
    suspend fun deleteAccount(email: String) {
      bestEffort {
        val user =
            Firebase.auth.currentUser?.takeIf { it.email == email }
                ?: Firebase.auth.signInWithEmailAndPassword(email, PASSWORD).await().user
        if (user != null) {
          // Separate steps, so a failed profile delete still deletes the account.
          bestEffort {
            runInterruptible(Dispatchers.IO) {
              FirebaseEmulator.deleteDocumentAsAdmin("${ProfileFields.COLLECTION}/${user.uid}")
            }
          }
          user.delete().await()
        }
      }
    }

    /**
     * Runs [block] and ignores its failure, but not a cancellation: the cleanup timeout must still
     * stop the remaining steps.
     */
    suspend fun bestEffort(block: suspend () -> Unit) {
      try {
        block()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "Cleanup step failed", e)
      }
    }
  }
}
