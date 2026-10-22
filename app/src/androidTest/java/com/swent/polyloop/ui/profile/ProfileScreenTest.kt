// Made with Claude.

package com.swent.polyloop.ui.profile

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.auth.AuthResult
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingAddresses
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingRepository
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.model.profile.FakeProfileRepository
import com.swent.polyloop.model.profile.UserProfile
import com.swent.polyloop.model.rental.FakeRentalRepository
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.theme.PolyLoopTheme
import com.swent.polyloop.utils.FakePhotoServerRule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  // Keeps the example.com listing photos off the network.
  @get:Rule val fakePhotoServer = FakePhotoServerRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private val john =
      UserProfile(
          uid = "me",
          name = "John",
          email = "john@epfl.ch",
          averageRating = 4.5,
          reviewCount = 2,
      )

  private fun listing(id: String) =
      Listing(
          id = id,
          ownerId = "me",
          title = "Item $id",
          description = "Description of $id",
          category = ListingCategory.OTHER,
          photoUrls = List(3) { "https://example.com/$id-$it.jpg" },
          pricePerDay = 10,
          itemValue = 100,
          availableFrom = LocalDate.of(2026, 10, 1),
          availableTo = LocalDate.of(2026, 10, 31),
          status = ListingStatus.PUBLISHED,
          pickupArea = "Ecublens",
      )

  private val tent = listing("tent")

  private val ownProfile =
      ProfileUiState(
          isOwnProfile = true,
          profile = john,
          listings = listOf(MyListing(tent, ListingRentalState.AVAILABLE)),
          isLoading = false,
          listingsLoading = false,
      )

  private var openedListing: String? = null
  private var signOutClicks = 0
  private var retryClicks = 0
  private var retryListingsClicks = 0

  private fun setContent(state: ProfileUiState) {
    composeTestRule.setContent {
      PolyLoopTheme {
        ProfileContent(
            uiState = state,
            onListingClick = { openedListing = it },
            onSignOut = { signOutClicks++ },
            onRetry = { retryClicks++ },
            onRetryListings = { retryListingsClicks++ },
        )
      }
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  // ---------- own profile ----------

  @Test
  fun ownProfileShowsTheHeaderMyListingsAndSignOut() {
    setContent(ownProfile)

    node(C.Tag.profile_avatar).assertTextEquals("J")
    node(C.Tag.profile_name).assertTextEquals("John")
    node(C.Tag.profile_rating)
        .assertTextEquals(context.resources.getQuantityString(R.plurals.profile_rating, 2, 4.5, 2))
    node(C.Tag.profile_listings_title).assertIsDisplayed()
    node(C.Tag.profile_listing_ + "tent").assertIsDisplayed()
    node(C.Tag.profile_sign_out_button).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun listingsShowTheirCountAndWhereEachStands() {
    setContent(
        ownProfile.copy(
            listings =
                listOf(
                    MyListing(tent, ListingRentalState.ON_LOAN),
                    MyListing(listing("bike"), ListingRentalState.REQUESTED),
                )
        )
    )

    // The clickable row merges its texts, so the pill is found in the unmerged tree.
    fun pillOf(id: String) =
        composeTestRule.onNodeWithTag(C.Tag.profile_listing_status_ + id, useUnmergedTree = true)

    node(C.Tag.profile_listings_count).assertTextEquals("2")
    pillOf("tent").assertTextEquals(context.getString(R.string.profile_listing_status_on_loan))
    pillOf("bike").assertTextEquals(context.getString(R.string.profile_listing_status_requested))
  }

  @Test
  fun loadingListingsShowsASpinnerUnderTheProfile() {
    setContent(ownProfile.copy(listings = emptyList(), listingsLoading = true))

    node(C.Tag.profile_name).assertIsDisplayed()
    node(C.Tag.profile_listings_loading).assertIsDisplayed()
    node(C.Tag.profile_listings_count).assertDoesNotExist()
    node(C.Tag.profile_listings_empty).assertDoesNotExist()
  }

  @Test
  fun listingsErrorShowsAGenericMessageUnderTheProfileAndRetries() {
    setContent(ownProfile.copy(listings = emptyList(), listingsError = true))

    node(C.Tag.profile_name).assertIsDisplayed()
    node(C.Tag.profile_listings_error)
        .assertTextEquals(context.getString(R.string.profile_listings_error))
    node(C.Tag.profile_listings_retry_button).performClick()

    assertEquals(1, retryListingsClicks)
    assertEquals(0, retryClicks)
  }

  @Test
  fun tappingAListingOpensIt() {
    setContent(ownProfile)

    node(C.Tag.profile_listing_ + "tent").performClick()

    assertEquals("tent", openedListing)
  }

  @Test
  fun signOutButtonSignsOut() {
    setContent(ownProfile)

    node(C.Tag.profile_sign_out_button).performScrollTo().performClick()

    assertEquals(1, signOutClicks)
  }

  @Test
  fun ownProfileWithoutListingsSaysSo() {
    setContent(ownProfile.copy(listings = emptyList()))

    node(C.Tag.profile_listings_empty).assertIsDisplayed()
  }

  // ---------- someone else's profile ----------

  @Test
  fun otherProfileShowsTheHeaderWithoutListingsOrSignOut() {
    setContent(ownProfile.copy(isOwnProfile = false, listings = emptyList()))

    node(C.Tag.profile_name).assertTextEquals("John")
    node(C.Tag.profile_listings_title).assertDoesNotExist()
    node(C.Tag.profile_listings_empty).assertDoesNotExist()
    node(C.Tag.profile_sign_out_button).assertDoesNotExist()
  }

  @Test
  fun profileWithoutNameOrReviewsShowsPlaceholders() {
    setContent(ownProfile.copy(profile = UserProfile(uid = "me", name = " ", email = "")))

    node(C.Tag.profile_avatar).assertTextEquals("?")
    node(C.Tag.profile_name).assertTextEquals(context.getString(R.string.profile_name_placeholder))
    node(C.Tag.profile_rating).assertTextEquals(context.getString(R.string.profile_no_reviews))
  }

  // ---------- loading, errors, not found ----------

  @Test
  fun loadingShowsASpinner() {
    setContent(ProfileUiState(isLoading = true))

    node(C.Tag.profile_loading).assertIsDisplayed()
    node(C.Tag.profile_name).assertDoesNotExist()
  }

  @Test
  fun errorShowsTheMessageAndRetries() {
    setContent(ProfileUiState(isLoading = false, errorMsg = "No connection"))

    node(C.Tag.profile_error).assertTextEquals("No connection")
    node(C.Tag.profile_retry_button).performClick()

    assertEquals(1, retryClicks)
  }

  @Test
  fun blankErrorShowsAGenericMessage() {
    setContent(ProfileUiState(isLoading = false, errorMsg = ""))

    node(C.Tag.profile_error).assertTextEquals(context.getString(R.string.profile_error_fallback))
  }

  @Test
  fun missingProfileSaysItDoesNotExist() {
    setContent(ProfileUiState(isLoading = false, profileNotFound = true))

    node(C.Tag.profile_not_found).assertIsDisplayed()
  }

  // ---------- with the ViewModel ----------

  @Test
  fun signingOutFromTheScreenCallsOnSignedOut() {
    var signedOut = false
    val auth =
        object : AuthRepository {
          var user: AuthUser? = AuthUser(uid = "me", email = "john@epfl.ch", name = "John")

          override suspend fun signUp(name: String, email: String, password: String) =
              AuthResult.Success(Unit)

          override suspend fun signIn(email: String, password: String): AuthResult<AuthUser> =
              AuthResult.Success(user!!)

          override suspend fun resendVerificationEmail(email: String, password: String) =
              AuthResult.Success(Unit)

          override fun getCurrentUser(): AuthUser? = user

          override fun signOut() {
            user = null
          }
        }
    val listings =
        object : ListingRepository {
          override suspend fun getAllListings() = Result.success(emptyList<Listing>())

          override suspend fun getListing(id: String) = Result.success<Listing?>(null)

          override suspend fun getListingsByOwner(ownerId: String) = Result.success(listOf(tent))

          override suspend fun createListing(
              listing: Listing,
              addresses: ListingAddresses,
              photos: List<Uri>,
          ): Result<String> = error("Not used in this test")
        }
    val viewModel =
        ProfileViewModel(
            null,
            FakeProfileRepository(listOf(john)),
            listings,
            FakeRentalRepository(),
            auth,
        )
    composeTestRule.setContent {
      PolyLoopTheme {
        ProfileScreen(
            viewModel = viewModel,
            onListingClick = {},
            onSignedOut = { signedOut = true },
        )
      }
    }

    node(C.Tag.profile_sign_out_button).performScrollTo().performClick()
    composeTestRule.waitForIdle()

    assertTrue(signedOut)
    assertEquals(null, auth.getCurrentUser())
  }
}
