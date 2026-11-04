// Made with Claude.

package com.swent.polyloop.ui.reviewrequest

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingRepository
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.model.profile.FakeProfileRepository
import com.swent.polyloop.model.rental.FakeRentalRepository
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.createlisting.UnusedListingRepository
import com.swent.polyloop.ui.rentaldetail.OtherPerson
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewRequestScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private val form =
      ReviewRequestUiState(
          canAnswer = true,
          itemTitle = "Camping tent",
          startDate = LocalDate.of(2026, 10, 15),
          endDate = LocalDate.of(2026, 10, 17),
          rentalPrice = 45,
          borrower = OtherPerson("b", "Loca", 4.6, 5),
          suggestedDeposit = 180,
          deposit = "180",
      )

  private val complete = form.copy(pickupAddress = "Route Cantonale 1", returnAddress = "Rue 10")

  private var retries = 0
  private var sends = 0
  private var declines = 0
  private var chats = 0
  private val people = mutableListOf<String>()
  private var deposit: String? = null
  private var pickup: String? = null
  private var dropOff: String? = null

  private fun setContent(uiState: ReviewRequestUiState) {
    composeTestRule.setContent {
      ReviewRequestContent(
          uiState = uiState,
          onRetry = { retries++ },
          onDepositChange = { deposit = it },
          onPickupAddressChange = { pickup = it },
          onReturnAddressChange = { dropOff = it },
          onSendOffer = { sends++ },
          onDecline = { declines++ },
          onChatClick = { chats++ },
          onPersonClick = { people += it },
          today = LocalDate.of(2026, 10, 1),
      )
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  @Test
  fun showsTheRequestAndTheSuggestedDeposit() {
    setContent(form)

    node(C.Tag.review_request_summary).assertIsDisplayed()
    node(C.Tag.review_request_summary).assertTextContains("Camping tent", substring = true)
    node(C.Tag.review_request_summary).assertTextContains("3 days", substring = true)
    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_person_name, useUnmergedTree = true)
        .assertTextEquals("Loca")
    node(C.Tag.review_request_price)
        .assertTextEquals(context.getString(R.string.rental_detail_price, 45))
    node(C.Tag.review_request_suggested_deposit)
        .assertTextEquals(context.getString(R.string.listing_card_price, 180))
    node(C.Tag.review_request_deposit).assertTextContains("180")
  }

  @Test
  fun leavesOutTheSuggestedDepositWhenItIsUnknown() {
    setContent(form.copy(suggestedDeposit = null))

    node(C.Tag.review_request_suggested_deposit).assertDoesNotExist()
  }

  @Test
  fun aSingleDayIsWrittenInTheSingular() {
    setContent(form.copy(endDate = form.startDate))

    node(C.Tag.review_request_summary).assertTextContains("1 day", substring = true)
  }

  @Test
  fun theFieldsReportWhatIsTyped() {
    // The state follows what is typed, as with the ViewModel: a field whose value never changes
    // would report the old text again.
    composeTestRule.setContent {
      var state by remember { mutableStateOf(form) }
      ReviewRequestContent(
          uiState = state,
          onRetry = {},
          onDepositChange = {
            deposit = it
            state = state.copy(deposit = it)
          },
          onPickupAddressChange = {
            pickup = it
            state = state.copy(pickupAddress = it)
          },
          onReturnAddressChange = {
            dropOff = it
            state = state.copy(returnAddress = it)
          },
          onSendOffer = {},
          onDecline = {},
          onChatClick = {},
          onPersonClick = {},
      )
    }

    node(C.Tag.review_request_deposit).performTextReplacement("150")
    node(C.Tag.review_request_pickup).performScrollTo().performTextReplacement("Route 1")
    node(C.Tag.review_request_return).performScrollTo().performTextReplacement("Rue 2")

    assertEquals("150", deposit)
    assertEquals("Route 1", pickup)
    assertEquals("Rue 2", dropOff)
  }

  @Test
  fun sendOfferIsDisabledUntilTheFormIsCompleteButDeclineIsNot() {
    setContent(form)

    node(C.Tag.review_request_send).assertIsNotEnabled()
    node(C.Tag.review_request_decline).assertIsEnabled()
  }

  @Test
  fun bothButtonsCallTheirAction() {
    setContent(complete)

    node(C.Tag.review_request_send).performClick()
    node(C.Tag.review_request_decline).performClick()

    assertEquals(1, sends)
    assertEquals(1, declines)
  }

  @Test
  fun thePersonCardAndChatButtonCallTheirAction() {
    setContent(form)

    node(C.Tag.rental_detail_chat_button).performClick()
    node(C.Tag.rental_detail_person_card).performClick()

    assertEquals(1, chats)
    assertEquals(listOf("b"), people)
  }

  @Test
  fun bothButtonsAreDisabledOfflineWithTheBanner() {
    setContent(complete.copy(isOffline = true))

    node(C.Tag.review_request_send).assertIsNotEnabled()
    node(C.Tag.review_request_decline).assertIsNotEnabled()
  }

  @Test
  fun bothButtonsAndTheFieldsAreDisabledWhileSending() {
    setContent(complete.copy(isSubmitting = true))

    node(C.Tag.review_request_send).assertIsNotEnabled()
    node(C.Tag.review_request_decline).assertIsNotEnabled()
    node(C.Tag.review_request_deposit).assertIsNotEnabled()
  }

  @Test
  fun warnsAboutOverlappingRequestsOnlyWhenThereAreSome() {
    setContent(form.copy(overlappingRequests = 2))

    node(C.Tag.review_request_overlapping)
        .assertTextEquals(
            context.resources.getQuantityString(R.plurals.review_request_overlapping, 2, 2)
        )
  }

  @Test
  fun noWarningWithoutOverlappingRequests() {
    setContent(form)

    node(C.Tag.review_request_overlapping).assertDoesNotExist()
  }

  @Test
  fun showsTheErrorOfAFailedAnswer() {
    setContent(form.copy(actionError = "These dates are no longer free."))

    node(C.Tag.review_request_action_error).assertTextEquals("These dates are no longer free.")
  }

  @Test
  fun aBlankErrorShowsTheGenericText() {
    setContent(form.copy(actionError = ""))

    node(C.Tag.review_request_action_error)
        .assertTextEquals(context.getString(R.string.review_request_action_error))
  }

  @Test
  fun showsLoading() {
    setContent(ReviewRequestUiState(isLoading = true))

    node(C.Tag.review_request_loading).assertIsDisplayed()
  }

  @Test
  fun showsAReadErrorWithRetry() {
    setContent(ReviewRequestUiState(errorMsg = "down"))

    composeTestRule.onNodeWithText("down").assertIsDisplayed()
    node(C.Tag.rental_detail_retry_button).performClick()

    assertEquals(1, retries)
  }

  @Test
  fun showsNotFound() {
    setContent(ReviewRequestUiState(notFound = true))

    node(C.Tag.rental_detail_not_found).assertIsDisplayed()
  }

  @Test
  fun aRequestThatCannotBeAnsweredShowsANoteAndNoForm() {
    setContent(ReviewRequestUiState(itemTitle = "Camping tent"))

    node(C.Tag.review_request_not_answerable).assertIsDisplayed()
    node(C.Tag.review_request_send).assertDoesNotExist()
  }

  // ---------- With the ViewModel ----------

  private val rental =
      Rental(
          id = "r1",
          listingId = "l1",
          listingTitle = "Camping tent",
          ownerId = "owner",
          ownerName = "Mara",
          borrowerId = "borrower",
          borrowerName = "Loca",
          startDate = LocalDate.of(2099, 12, 1),
          endDate = LocalDate.of(2099, 12, 3),
          status = RentalStatus.REQUESTED,
          pricePerDay = 15,
          totalPrice = 45,
          pickupArea = "Ecublens",
          dropOffArea = "Renens",
          createdAt = Instant.parse("2026-11-04T10:00:00Z"),
      )

  private val listing =
      Listing(
          id = "l1",
          ownerId = "owner",
          title = "Camping tent",
          description = "Light 2-person tent.",
          category = ListingCategory.SPORTS_OUTDOOR,
          photoUrls = List(3) { "https://example.com/$it.jpg" },
          pricePerDay = 15,
          itemValue = 180,
          availableFrom = LocalDate.of(2099, 1, 1),
          availableTo = LocalDate.of(2099, 12, 31),
          status = ListingStatus.PUBLISHED,
      )

  private fun setScreen(onOfferSent: () -> Unit = {}, onDeclined: () -> Unit = {}) {
    val listings =
        object : ListingRepository by UnusedListingRepository() {
          override suspend fun getListing(id: String) = Result.success<Listing?>(listing)
        }
    val viewModel =
        ReviewRequestViewModel(
            rentalId = "r1",
            rentalRepository = FakeRentalRepository(listOf(rental)),
            listingRepository = listings,
            profileRepository = FakeProfileRepository(),
            authRepository =
                FakeAuthRepository(
                    signedInUser = AuthUser(uid = "owner", email = "m@epfl.ch", name = "Mara")
                ),
            connectivityRepository = FakeConnectivityRepository(),
        )
    composeTestRule.setContent {
      ReviewRequestScreen(
          viewModel = viewModel,
          onOfferSent = onOfferSent,
          onDeclined = onDeclined,
          onChatClick = {},
          onPersonClick = {},
      )
    }
  }

  @Test
  fun aCompleteFormSendsTheOfferAndCallsOnOfferSent() {
    var sent = 0
    setScreen(onOfferSent = { sent++ })

    node(C.Tag.review_request_pickup).performScrollTo().performTextReplacement("Route 1")
    node(C.Tag.review_request_return).performScrollTo().performTextReplacement("Rue 2")
    node(C.Tag.review_request_send).performClick()

    composeTestRule.waitForIdle()
    assertEquals(1, sent)
  }

  @Test
  fun declineCallsOnDeclined() {
    var declined = 0
    setScreen(onDeclined = { declined++ })

    node(C.Tag.review_request_decline).performClick()

    composeTestRule.waitForIdle()
    assertEquals(1, declined)
  }
}
