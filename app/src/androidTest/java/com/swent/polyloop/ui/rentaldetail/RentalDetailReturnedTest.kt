// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.model.location.MapPoint
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Returned state of Rental detail (Figma frame 13.04-returned). */
@RunWith(AndroidJUnit4::class)
class RentalDetailReturnedTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private val returned =
      RentalDetailUiState(
          itemTitle = "Ski helmet, size M",
          status = RentalStatus.RETURNED,
          startDate = LocalDate.of(2026, 10, 1),
          endDate = LocalDate.of(2026, 10, 8),
          rentalPrice = 120,
          // Set so that a meeting point or the request's pickup area would show if drawn.
          pickupArea = "Ecublens",
          pickupPoint = MapPoint(latitude = 46.52, longitude = 6.57),
          pickupAddress = "Route Cantonale 1, Ecublens",
          returnAddress = "Rue de Lausanne 10, Renens",
          otherPerson = OtherPerson("lea", "Léa", averageRating = 4.6, reviewCount = 5),
          deposit = 60,
          lateFees = 17,
          // 16:40 in Zurich on 8 Oct.
          returnedAt = Instant.parse("2026-10-08T14:40:00Z"),
      )

  private var chatClicks = 0
  private var checkItemClicks = 0
  // Every callback but Chat and "Check the item", which must not be called by the bottom button.
  private val otherClicks = mutableListOf<String>()

  private fun setContent(state: RentalDetailUiState = returned) {
    composeTestRule.setContent {
      RentalDetailContent(
          uiState = state,
          onRetry = { otherClicks += "retry" },
          onChatClick = { chatClicks++ },
          onPersonClick = { otherClicks += "person" },
          onStartPickupClick = { otherClicks += "startPickup" },
          onStartReturnClick = { otherClicks += "startReturn" },
          onViewDisputeClick = { otherClicks += "viewDispute" },
          onCheckItemClick = { checkItemClicks++ },
          onCancelClick = { otherClicks += "cancel" },
          onReviewRequestClick = { otherClicks += "reviewRequest" },
          today = LocalDate.of(2026, 10, 9),
          zone = ZoneId.of("Europe/Zurich"),
          // English is fixed so the expected dates pass on an emulator in any language.
          locale = Locale.ENGLISH,
      )
    }
  }

  private fun assertShown(tag: String, text: String) {
    composeTestRule
        .onNodeWithTag(tag, useUnmergedTree = true)
        .performScrollTo()
        .assertIsDisplayed()
        .assertTextEquals(text)
  }

  @Test
  fun theStripReachesReturnedAsTheCurrentStage() {
    setContent()

    composeTestRule
        .onAllNodesWithTag(C.Tag.rental_detail_stage_reached, useUnmergedTree = true)
        .assertCountEquals(3)
    assertShown(
        C.Tag.rental_detail_stage_current,
        context.getString(R.string.rental_detail_stage_returned),
    )
  }

  @Test
  fun theStatusCardGivesTheReturnDayAndTellsTheBorrowerTheItemIsBeingChecked() {
    setContent()

    assertShown(C.Tag.rental_detail_status, context.getString(R.string.rental_status_returned))
    assertShown(C.Tag.rental_detail_status_headline, "Returned on 8 Oct")
    assertShown(
        C.Tag.rental_detail_status_body,
        "The item is being checked against the pickup photos. The deposit is released once " +
            "everything is confirmed fine.",
    )
  }

  @Test
  fun theStatusCardAsksTheLenderToCheckTheItem() {
    setContent(returned.copy(isLender = true))

    assertShown(
        C.Tag.rental_detail_status_body,
        "Check the item against your pickup photos. The deposit stays held until a decision.",
    )
  }

  @Test
  fun aReturnDayInAnotherYearShowsItsYear() {
    setContent(returned.copy(returnedAt = Instant.parse("2025-12-30T10:00:00Z")))

    assertShown(C.Tag.rental_detail_status_headline, "Returned on 30 Dec 2025")
  }

  @Test
  fun anUnknownReturnDayShowsReturnedAlone() {
    setContent(returned.copy(returnedAt = null))

    assertShown(C.Tag.rental_detail_status_headline, "Returned")
  }

  @Test
  fun aReturnLateInTheEveningUtcShowsTheDayInZurich() {
    // 23:30 UTC on 8 Oct is already 01:30 on 9 Oct in Zurich.
    setContent(returned.copy(returnedAt = Instant.parse("2026-10-08T23:30:00Z")))

    assertShown(C.Tag.rental_detail_status_headline, "Returned on 9 Oct")
  }

  @Test
  fun theDetailsGiveTheDatesThePaidPriceTheHeldDepositAndTheLateFees() {
    setContent()

    assertShown(C.Tag.rental_detail_dates, "1 – 8 Oct")
    assertShown(C.Tag.rental_detail_price, "120 PP · paid")
    assertShown(C.Tag.rental_detail_accepted_deposit, "60 PP · held")
    assertShown(C.Tag.rental_detail_late_fees, "17 PP")
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_return).assertDoesNotExist()
  }

  @Test
  fun lateFeesOfZeroStillShowTheRow() {
    setContent(returned.copy(lateFees = 0))

    assertShown(C.Tag.rental_detail_late_fees, "0 PP")
  }

  @Test
  fun anUnknownDepositKeepsTheRowWithADash() {
    setContent(returned.copy(deposit = 0))

    assertShown(C.Tag.rental_detail_accepted_deposit, "—")
  }

  @Test
  fun theLenderSeesTheSameHeadlineAndDetails() {
    setContent(returned.copy(isLender = true))

    assertShown(C.Tag.rental_detail_status_headline, "Returned on 8 Oct")
    assertShown(C.Tag.rental_detail_dates, "1 – 8 Oct")
    assertShown(C.Tag.rental_detail_price, "120 PP · paid")
    assertShown(C.Tag.rental_detail_accepted_deposit, "60 PP · held")
    assertShown(C.Tag.rental_detail_late_fees, "17 PP")
  }

  @Test
  fun chatIsEnabledAndOpensTheChat() {
    setContent()

    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_chat_button)
        .performScrollTo()
        .assertIsEnabled()
        .performClick()

    assertEquals(1, chatClicks)
  }

  @Test
  fun thereIsNoMeetingPointNoMapAndNoCancelRow() {
    setContent()

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_map).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.pickup_map).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_pickup_point_label).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_return_point_label).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_pickup_area).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_cancel_button).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_cancel_until).assertDoesNotExist()
  }

  @Test
  fun checkTheItemIsEnabledForTheLenderAndOpensTheReturnCheck() {
    assertCheckTheItemOpensTheReturnCheck(returned.copy(isLender = true))
  }

  @Test
  fun checkTheItemStaysEnabledOffline() {
    assertCheckTheItemOpensTheReturnCheck(returned.copy(isLender = true, isOffline = true))
  }

  private fun assertCheckTheItemOpensTheReturnCheck(state: RentalDetailUiState) {
    setContent(state)

    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_action_button)
        .assertIsDisplayed()
        .assertIsEnabled()
        .assertTextEquals(context.getString(R.string.rental_detail_check_item))
        .performClick()

    assertEquals(1, checkItemClicks)
    assertEquals(emptyList<String>(), otherClicks)
    assertEquals(0, chatClicks)
  }

  @Test
  fun theBorrowerHasNoBottomButton() {
    setContent()

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_action_button).assertDoesNotExist()
  }
}
