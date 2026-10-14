// Edited with Claude.

package com.swent.polyloop.ui.browse

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.auth.AuthResult
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingFilter
import com.swent.polyloop.model.listing.ListingRepository
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.resources.C
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowseScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  // Signed in as "me", who owns none of the listings below.
  private val authRepository = SignedInAuthRepository(AuthUser("me", "me@epfl.ch", "Me"))

  private val listings =
      listOf("tent", "projector", "drill", "helmet", "bike", "guitar").map { id ->
        Listing(
            id = id,
            ownerId = "owner",
            title = "Item $id",
            description = "Description of $id",
            category = ListingCategory.OTHER,
            photoUrls = emptyList(),
            pricePerDay = 10,
            itemValue = 100,
            availableFrom = LocalDate.of(2026, 10, 1),
            availableTo = LocalDate.of(2026, 12, 31),
            status = ListingStatus.PUBLISHED,
            pickupArea = "Ecublens",
        )
      }

  private val headerTags =
      listOf(
          C.Tag.browse_title,
          C.Tag.browse_search_bar,
          C.Tag.browse_category_chips,
          C.Tag.browse_category_all,
          C.Tag.browse_map_toggle,
          C.Tag.browse_list_toggle,
      )

  private fun setContent(
      uiState: BrowseUiState = BrowseUiState(listings = listings),
      onListingClick: (String) -> Unit = {},
  ) {
    composeTestRule.setContent {
      BrowseContent(uiState = uiState, onListingClick = onListingClick, onRetry = {})
    }
  }

  private fun setContentWithViewModel(
      listings: List<Listing>,
      onListingClick: (String) -> Unit = {},
  ) {
    val repository =
        object : ListingRepository {
          override suspend fun getListingsByOwner(ownerId: String): Result<List<Listing>> =
              Result.success(emptyList())

          override suspend fun getAllListings(): Result<List<Listing>> = Result.success(listings)

          override suspend fun getListing(id: String): Result<Listing?> = Result.success(null)
        }
    val viewModel = BrowseViewModel(repository, authRepository, FakeConnectivityRepository())
    composeTestRule.setContent {
      BrowseScreen(viewModel = viewModel, onListingClick = onListingClick)
    }
  }

  private fun waitForTag(tag: String) {
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
  }

  @Test
  fun headerPartsAreDisplayed() {
    setContent()

    composeTestRule.onNodeWithTag(C.Tag.browse_screen).assertIsDisplayed()
    headerTags.forEach { composeTestRule.onNodeWithTag(it).assertIsDisplayed() }
  }

  @Test
  fun loadingStateShowsProgressIndicator() {
    setContent(uiState = BrowseUiState(isLoading = true))

    composeTestRule.onNodeWithTag(C.Tag.browse_loading).assertIsDisplayed()
  }

  @Test
  fun blankErrorShowsGenericMessage() {
    setContent(uiState = BrowseUiState(errorMsg = ""))

    val fallback =
        InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(R.string.browse_error_fallback)
    composeTestRule.onNodeWithTag(C.Tag.browse_error).assertTextEquals(fallback)
  }

  @Test
  fun emptyListShowsEmptyState() {
    setContent(uiState = BrowseUiState(listings = emptyList()))

    composeTestRule
        .onNodeWithTag(C.Tag.browse_empty)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.browse_empty))
  }

  @Test
  fun filterWithoutMatchShowsNoResults() {
    setContent(uiState = BrowseUiState(listings = listings, filter = ListingFilter(query = "zzz")))

    composeTestRule
        .onNodeWithTag(C.Tag.browse_empty)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.browse_no_results))
  }

  @Test
  fun onlineShowsNoOfflineBanner() {
    setContent()

    composeTestRule.onNodeWithTag(C.Tag.offline_banner).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.browse_offline).assertDoesNotExist()
  }

  @Test
  fun offlineShowsBannerAndOfflineStateInsteadOfListings() {
    var myRentalsOpened = false
    composeTestRule.setContent {
      BrowseContent(
          uiState = BrowseUiState(listings = listings, isOffline = true),
          onListingClick = {},
          onRetry = {},
          onOpenMyRentals = { myRentalsOpened = true },
      )
    }

    composeTestRule
        .onNodeWithTag(C.Tag.offline_banner)
        .assertIsDisplayed()
        // The text is a child of the tagged banner row.
        .assert(hasAnyDescendant(hasText(string(R.string.offline_banner))))
    composeTestRule.onNodeWithTag(C.Tag.browse_title).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.browse_offline).assertIsDisplayed()
    // Browsing needs a connection: no filters and no listings, even the ones already loaded.
    (headerTags - C.Tag.browse_title + C.Tag.browse_list).forEach {
      composeTestRule.onNodeWithTag(it).assertDoesNotExist()
    }

    composeTestRule.onNodeWithTag(C.Tag.browse_offline_my_rentals_button).performClick()
    assertTrue(myRentalsOpened)
  }

  @Test
  fun browseScreenFollowsTheConnection() {
    val connectivity = FakeConnectivityRepository(online = true)
    val repository =
        object : ListingRepository {
          override suspend fun getListingsByOwner(ownerId: String): Result<List<Listing>> =
              Result.success(emptyList())

          override suspend fun getAllListings(): Result<List<Listing>> = Result.success(listings)

          override suspend fun getListing(id: String): Result<Listing?> = Result.success(null)
        }
    val viewModel = BrowseViewModel(repository, authRepository, connectivity)
    composeTestRule.setContent { BrowseScreen(viewModel = viewModel) }
    waitForTag(C.Tag.browse_list)

    connectivity.setOnline(false)
    waitForTag(C.Tag.browse_offline)
    composeTestRule.onNodeWithTag(C.Tag.offline_banner).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.browse_list).assertDoesNotExist()

    connectivity.setOnline(true)
    waitForTag(C.Tag.browse_list)
    composeTestRule.onNodeWithTag(C.Tag.offline_banner).assertDoesNotExist()
  }

  private fun string(id: Int) =
      InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

  @Test
  fun everyCardCanBeReachedByScrolling() {
    setContent()

    listings.forEach { listing ->
      val cardTag = C.Tag.listing_card_ + listing.id
      composeTestRule.onNodeWithTag(C.Tag.browse_list).performScrollToNode(hasTestTag(cardTag))
      composeTestRule.onNodeWithTag(cardTag).assertIsDisplayed()
    }
  }

  @Test
  fun pressingHeaderControlsDoesNothing() {
    var clickedId: String? = null
    setContent(onListingClick = { clickedId = it })

    listOf(C.Tag.browse_map_toggle, C.Tag.browse_list_toggle).forEach {
      composeTestRule.onNodeWithTag(it).performClick()
    }

    assertNull(clickedId)
  }

  @Test
  fun tappingCardCallsOnListingClickWithItsId() {
    var clickedId: String? = null
    setContent(onListingClick = { clickedId = it })

    composeTestRule.onNodeWithTag(C.Tag.listing_card_ + "projector").performClick()

    assertEquals("projector", clickedId)
  }

  @Test
  fun browseScreenShowsErrorThenListingsAfterRetry() {
    // Fails the first load, then returns the listings, so Retry must reach the ViewModel.
    var calls = 0
    val repository =
        object : ListingRepository {
          override suspend fun getListingsByOwner(ownerId: String): Result<List<Listing>> =
              Result.success(emptyList())

          override suspend fun getAllListings(): Result<List<Listing>> =
              if (calls++ == 0) Result.failure(IllegalStateException("Network down"))
              else Result.success(listings)

          override suspend fun getListing(id: String): Result<Listing?> = Result.success(null)
        }
    val viewModel = BrowseViewModel(repository, authRepository, FakeConnectivityRepository())
    var clickedId: String? = null
    composeTestRule.setContent {
      BrowseScreen(viewModel = viewModel, onListingClick = { clickedId = it })
    }

    waitForTag(C.Tag.browse_error)
    composeTestRule.onNodeWithTag(C.Tag.browse_error).assertTextEquals("Network down")
    composeTestRule.onNodeWithTag(C.Tag.browse_retry_button).performClick()

    waitForTag(C.Tag.browse_list)
    composeTestRule.onNodeWithTag(C.Tag.listing_card_ + "tent").performClick()
    assertEquals("tent", clickedId)
  }

  /**
   * Scrolls the lazy chip row to the chip with [tag] before clicking it, in case it is off screen.
   */
  private fun clickChip(tag: String) {
    composeTestRule.onNodeWithTag(C.Tag.browse_category_chips).performScrollToNode(hasTestTag(tag))
    composeTestRule.onNodeWithTag(tag).performClick()
  }

  /**
   * Checks that the list shows exactly the listings [ids]: each can be reached by scrolling, and
   * every other listing has no card.
   */
  private fun assertShownListings(vararg ids: String) {
    listings.forEach { listing ->
      val cardTag = C.Tag.listing_card_ + listing.id
      if (listing.id in ids) {
        composeTestRule.onNodeWithTag(C.Tag.browse_list).performScrollToNode(hasTestTag(cardTag))
        composeTestRule.onNodeWithTag(cardTag).assertIsDisplayed()
      } else {
        composeTestRule.onNodeWithTag(cardTag).assertDoesNotExist()
      }
    }
  }

  @Test
  fun ownListingIsNotDisplayed() {
    setContentWithViewModel(listings.map { if (it.id == "drill") it.copy(ownerId = "me") else it })
    waitForTag(C.Tag.browse_list)

    assertShownListings("tent", "projector", "helmet", "bike", "guitar")
  }

  @Test
  fun searchFiltersListingsAndClearingRestoresThem() {
    setContentWithViewModel(listings)
    waitForTag(C.Tag.browse_list)

    composeTestRule.onNodeWithTag(C.Tag.browse_search_bar).performTextInput("drill")
    assertShownListings("drill")

    composeTestRule.onNodeWithTag(C.Tag.browse_clear_search).performClick()
    assertShownListings(*listings.map { it.id }.toTypedArray())
  }

  @Test
  fun categoryChipsToggleTheirCategoryAndAllClearsThem() {
    val categories =
        mapOf("tent" to ListingCategory.SPORTS_OUTDOOR, "projector" to ListingCategory.ELECTRONICS)
    setContentWithViewModel(
        listings.map { it.copy(category = categories[it.id] ?: ListingCategory.OTHER) }
    )
    waitForTag(C.Tag.browse_list)
    val electronicsChip = C.Tag.browseCategoryChip(ListingCategory.ELECTRONICS)
    val sportsChip = C.Tag.browseCategoryChip(ListingCategory.SPORTS_OUTDOOR)

    clickChip(electronicsChip)
    assertShownListings("projector")

    clickChip(sportsChip)
    assertShownListings("projector", "tent")

    clickChip(electronicsChip)
    assertShownListings("tent")

    clickChip(electronicsChip)
    clickChip(C.Tag.browse_category_all)
    assertShownListings(*listings.map { it.id }.toTypedArray())
  }

  /** An [AuthRepository] where [user] is signed in, or nobody when null; Browse only reads it. */
  private class SignedInAuthRepository(var user: AuthUser?) : AuthRepository {
    override suspend fun signUp(name: String, email: String, password: String) =
        AuthResult.Success(Unit)

    override suspend fun signIn(email: String, password: String): AuthResult<AuthUser> =
        error("Not used by Browse")

    override suspend fun resendVerificationEmail(email: String, password: String) =
        AuthResult.Success(Unit)

    override fun getCurrentUser(): AuthUser? = user

    override fun signOut() {}
  }
}
