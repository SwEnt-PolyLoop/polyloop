// Made with Claude.

package com.swent.polyloop.ui.handover

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.dispute.FakeDisputeRepository
import com.swent.polyloop.model.handover.FakeHandoverRepository
import com.swent.polyloop.model.handover.FakeHandoverRepository.StoredPhoto
import com.swent.polyloop.model.handover.HandoverPhase
import com.swent.polyloop.model.handover.HandoverPhoto
import com.swent.polyloop.model.rental.FakeRentalRepository
import com.swent.polyloop.model.rental.InMemoryServerClockStore
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalRepository
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.model.rental.ServerClock
import com.swent.polyloop.resources.C
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The lender's check of the returned item on Handover, and the borrower waiting for it. */
@RunWith(AndroidJUnit4::class)
class HandoverReturnConfirmTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val returnDay = LocalDate.of(2026, 10, 8)

  private val check =
      HandoverUiState(
          isLoading = false,
          isLender = true,
          itemTitle = "Camping tent",
          otherPersonName = "Borrower",
          isReturned = true,
          deposit = 80,
          pickupPhotos = photos(3, HandoverPhase.PICKUP),
          photos = photos(4, HandoverPhase.RETURN),
      )

  private var damageFlagged = 0
  private var retries = 0

  private fun setContent(uiState: HandoverUiState) {
    composeTestRule.setContent {
      HandoverContent(
          uiState = uiState,
          onRetry = { retries++ },
          onAddPhoto = {},
          onRetryUpload = {},
          onFlagDamage = { damageFlagged++ },
          zone = ZoneOffset.UTC,
      )
    }
  }

  /** [count] photos of [phase], one minute apart from 16:31 UTC. */
  private fun photos(count: Int, phase: HandoverPhase) =
      List(count) {
        HandoverPhoto(
            "$phase$it",
            phase,
            Instant.parse("2026-10-08T16:31:00Z").plusSeconds(60L * it),
            "https://example.com/$phase$it.jpg",
        )
      }

  private fun text(tag: String, expected: String) =
      composeTestRule.onNodeWithTag(tag).assertTextEquals(expected)

  // ---------- the lender's check ----------

  @Test
  fun theLenderSeesTheScanTheirPhotosBeforeAndAfterAndTheDepositHeld() {
    setContent(check)

    text(C.Tag.handover_title, "Return")
    text(C.Tag.handover_other_person, "from Borrower")
    text(C.Tag.handover_return_scanned, "Return scanned")
    composeTestRule.onAllNodesWithTag(C.Tag.handover_before_photo).assertCountEquals(3)
    composeTestRule.onAllNodesWithTag(C.Tag.handover_after_photo).assertCountEquals(4)
    text(C.Tag.handover_deposit_label, "Deposit held")
    text(C.Tag.handover_deposit, "80 PP")
    composeTestRule
        .onNodeWithTag(C.Tag.handover_item_fine_button)
        .performScrollTo()
        .assert(hasText("Closes the rental, deposit is released"))
  }

  @Test
  fun aSectionWithoutPhotosSaysSo() {
    setContent(check.copy(pickupPhotos = emptyList()))

    composeTestRule.onNodeWithTag(C.Tag.handover_no_before_photos).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.handover_no_after_photos).assertDoesNotExist()
  }

  @Test
  fun photosStillLoadingShowASpinnerAndNotNoPhotos() {
    setContent(check.copy(photos = emptyList(), pickupPhotos = emptyList(), isLoadingPhotos = true))

    composeTestRule.onNodeWithTag(C.Tag.handover_check_photos_loading).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.handover_no_before_photos).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.handover_no_after_photos).assertDoesNotExist()
  }

  @Test
  fun photosThatCannotBeLoadedShowAnErrorWhoseRetryRetries() {
    setContent(check.copy(photosFailed = true))

    composeTestRule.onNodeWithTag(C.Tag.handover_photos_error).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.handover_before_photo).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.handover_photos_retry_button).performClick()
    assertEquals(1, retries)
  }

  @Test
  fun neitherAnswerSaysItIsNotAvailableYet() {
    setContent(check)
    val notAvailable =
        InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(R.string.not_available_yet)

    composeTestRule.onNodeWithTag(C.Tag.handover_item_fine_button).assertExists()
    composeTestRule.onAllNodesWithText(notAvailable).assertCountEquals(0)
  }

  @Test
  fun flagDamageSendsItsAnswer() {
    setContent(check)

    composeTestRule
        .onNodeWithTag(C.Tag.handover_flag_damage_button)
        .performScrollTo()
        .performClick()
    assertEquals(1, damageFlagged)
  }

  @Test
  fun whileAnAnswerIsSentItsButtonSpinsAndBothAreDisabled() {
    setContent(check.copy(sendingCheck = ReturnCheck.DAMAGE_FLAGGED))

    composeTestRule.onNodeWithTag(C.Tag.handover_item_fine_button).assertIsNotEnabled()
    composeTestRule
        .onNodeWithTag(C.Tag.handover_flag_damage_button)
        .performScrollTo()
        .assertIsNotEnabled()
    composeTestRule
        .onNodeWithTag(C.Tag.handover_check_progress, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertContentDescriptionEquals("Sending your answer")
  }

  @Test
  fun onceItemIsFineIsSentTheDepositReadsReleased() {
    setContent(check.copy(checkSent = ReturnCheck.ITEM_FINE))

    composeTestRule.onNodeWithText("The deposit was released to Borrower.").assertIsDisplayed()
    composeTestRule.onNodeWithText("The deposit stays held until you decide.").assertDoesNotExist()
    text(C.Tag.handover_deposit_label, "Deposit released")
    text(C.Tag.handover_deposit, "80 PP")
  }

  @Test
  fun onceItemIsFineIsSentWithoutTheBorrowersNameTheDepositIsReleasedToTheBorrower() {
    setContent(check.copy(otherPersonName = "", checkSent = ReturnCheck.ITEM_FINE))

    composeTestRule.onNodeWithText("The deposit was released to the borrower.").assertIsDisplayed()
  }

  @Test
  fun onceDamageIsFlaggedTheDepositStaysHeld() {
    setContent(check.copy(checkSent = ReturnCheck.DAMAGE_FLAGGED))

    composeTestRule.onNodeWithText("The deposit stays held until you decide.").assertIsDisplayed()
    text(C.Tag.handover_deposit_label, "Deposit held")
  }

  @Test
  fun offlineOrAfterAFailureTheButtonsShowWhyAndWhen() {
    var uiState by mutableStateOf(check.copy(isOffline = true))
    composeTestRule.setContent {
      HandoverContent(uiState = uiState, onRetry = {}, onAddPhoto = {}, onRetryUpload = {})
    }
    composeTestRule.onNodeWithTag(C.Tag.offline_banner).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.handover_item_fine_button).assertIsNotEnabled()

    uiState = check.copy(checkFailed = true)
    composeTestRule.onNodeWithTag(C.Tag.handover_item_fine_button).assertIsEnabled()
    composeTestRule.onNodeWithTag(C.Tag.handover_check_error).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun tappingABeforePhotoShowsItFullScreen() {
    setContent(check)

    composeTestRule.onAllNodesWithTag(C.Tag.handover_before_photo).onFirst().performClick()
    text(C.Tag.handover_photo_viewer_label, "Pickup · 16:31")
  }

  // ---------- the borrower waiting ----------

  @Test
  fun theBorrowerWaitsForTheLendersCheckWithTheirDepositHeld() {
    setContent(check.copy(isLender = false, otherPersonName = "Lender"))

    text(C.Tag.handover_other_person, "to Lender")
    text(
        C.Tag.handover_waiting_check,
        "The return was scanned. Lender now checks the item against the pickup photos.",
    )
    text(C.Tag.handover_deposit_label, "Your deposit, held")
    composeTestRule.onNodeWithTag(C.Tag.handover_return_check).assertDoesNotExist()
  }

  @Test
  fun aBorrowerWhoseLenderHasNoNameWaitsForTheLender() {
    setContent(check.copy(isLender = false, otherPersonName = ""))

    text(
        C.Tag.handover_waiting_check,
        "The return was scanned. The lender now checks the item against the pickup photos.",
    )
  }

  // ---------- the whole check, through the ViewModel ----------

  private val rental =
      Rental(
          id = "rental1",
          listingId = "listing1",
          listingTitle = "Camping tent",
          ownerId = "owner",
          ownerName = "Lender",
          borrowerId = "borrower",
          borrowerName = "Borrower",
          startDate = returnDay.minusDays(2),
          endDate = returnDay,
          status = RentalStatus.RETURNED,
          pricePerDay = 15,
          totalPrice = 45,
          pickupArea = "Ecublens",
          dropOffArea = "Renens",
          createdAt = Instant.parse("2026-10-01T09:30:00Z"),
      )

  /** The rentals, with [live] standing for [rental] as the server changes it. */
  private class LiveRental(initial: Rental, base: RentalRepository) : RentalRepository by base {
    val live = MutableStateFlow<Rental?>(initial)

    override fun observeRental(id: String): Flow<Rental?> = live
  }

  /** The ViewModel of the returned [rental], seen by its lender, with [handovers] and [rentals]. */
  private fun lenderViewModel(
      handovers: FakeHandoverRepository,
      rentals: RentalRepository = FakeRentalRepository(listOf(rental)),
  ): HandoverViewModel {
    return HandoverViewModel(
        rental.id,
        rentals,
        handovers,
        FakeDisputeRepository(currentUid = { "owner" }),
        FakeAuthRepository(signedInUser = AuthUser("owner", "lender@epfl.ch", "Lender")),
        FakeConnectivityRepository(),
        ServerClock(rentals, InMemoryServerClockStore(), bootCount = { 0 }),
    )
  }

  @Test
  fun aReturnedRentalOpensTheLendersCheckFromTheViewModel() {
    val handovers = FakeHandoverRepository(currentUid = "owner")
    handovers.photos =
        photos(3, HandoverPhase.PICKUP).map { StoredPhoto(rental.id, "owner", it) } +
            photos(2, HandoverPhase.RETURN).map { StoredPhoto(rental.id, "borrower", it) }
    val viewModel = lenderViewModel(handovers)
    composeTestRule.setContent { HandoverScreen(viewModel) }

    composeTestRule.onNodeWithTag(C.Tag.handover_return_check).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.handover_before_photo).assertCountEquals(3)
    composeTestRule.onNodeWithTag(C.Tag.handover_no_after_photos).assertExists()
  }

  @Test
  fun theLenderRetriesAnAnswerWhoseReplyWasLostThenSeesItSentAndTheDepositReleased() {
    val handovers =
        FakeHandoverRepository(currentUid = "owner", confirmFailure = RuntimeException("lost"))
    handovers.photos = photos(3, HandoverPhase.PICKUP).map { StoredPhoto(rental.id, "owner", it) }
    val rentals = LiveRental(rental, FakeRentalRepository(listOf(rental)))
    composeTestRule.setContent { HandoverScreen(lenderViewModel(handovers, rentals)) }
    composeTestRule.onAllNodesWithTag(C.Tag.handover_before_photo).assertCountEquals(3)

    composeTestRule.onNodeWithTag(C.Tag.handover_item_fine_button).performScrollTo().performClick()
    // The server closed the rental, but its reply was lost: the lender keeps the check and the
    // error.
    composeTestRule.runOnIdle { rentals.live.value = rental.copy(status = RentalStatus.CLOSED) }
    composeTestRule.onNodeWithTag(C.Tag.handover_check_error).performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.handover_check_sent).assertDoesNotExist()
    composeTestRule.runOnIdle { assertEquals(emptyList<String>(), handovers.confirmedReturns) }

    handovers.confirmFailure = null
    composeTestRule.onNodeWithTag(C.Tag.handover_item_fine_button).performScrollTo().performClick()
    composeTestRule.onNodeWithTag(C.Tag.handover_check_error).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.handover_check_sent).performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.handover_item_fine_button).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.handover_flag_damage_button).assertIsNotEnabled()
    composeTestRule.onNodeWithText("The deposit was released to Borrower.").assertExists()
    text(C.Tag.handover_deposit_label, "Deposit released")
    composeTestRule.runOnIdle { assertEquals(listOf(rental.id), handovers.confirmedReturns) }
  }
}
