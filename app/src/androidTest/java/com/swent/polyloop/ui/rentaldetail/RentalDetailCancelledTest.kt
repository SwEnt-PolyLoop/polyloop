// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
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

/** The Cancelled state of Rental detail (Figma frame 13.07-cancelled), for both roles. */
@RunWith(AndroidJUnit4::class)
class RentalDetailCancelledTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  // The pickup area, its point and the addresses are set so that a meeting point would show if the
  // screen drew one.
  private val cancelled =
      RentalDetailUiState(
          itemTitle = "Ski helmet, size M",
          status = RentalStatus.CANCELLED,
          startDate = LocalDate.of(2026, 10, 1),
          endDate = LocalDate.of(2026, 10, 8),
          rentalPrice = 120,
          pickupArea = "Ecublens",
          pickupPoint = MapPoint(latitude = 46.52, longitude = 6.57),
          otherPerson = OtherPerson("lea", "Léa", averageRating = 4.6, reviewCount = 5),
          deposit = 60,
          pickupAddress = "Route Cantonale 1, Ecublens",
          returnAddress = "Rue de Lausanne 10, Renens",
          cancelledAt = Instant.parse("2026-09-30T10:00:00Z"),
      )

  private var chatClicks = 0

  private fun setContent(state: RentalDetailUiState = cancelled) {
    composeTestRule.setContent {
      RentalDetailContent(
          uiState = state,
          onRetry = {},
          onChatClick = { chatClicks++ },
          onPersonClick = {},
          onStartPickupClick = {},
          onStartReturnClick = {},
          onViewDisputeClick = {},
          onCancelClick = {},
          today = LocalDate.of(2026, 10, 2),
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
  fun thereIsNoStageStrip() {
    setContent()

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_stage_strip).assertDoesNotExist()
  }

  @Test
  fun theBadgeSaysCancelled() {
    setContent()

    assertShown(C.Tag.rental_detail_status, context.getString(R.string.rental_status_cancelled))
  }

  @Test
  fun theHeadlineGivesTheCancelDay() {
    setContent()

    assertShown(C.Tag.rental_detail_status_headline, "Cancelled 30 Sep")
  }

  @Test
  fun theHeadlineGivesTheYearWhenTheCancelDayIsInAnotherYear() {
    setContent(cancelled.copy(cancelledAt = Instant.parse("2025-12-30T10:00:00Z")))

    assertShown(C.Tag.rental_detail_status_headline, "Cancelled 30 Dec 2025")
  }

  @Test
  fun theHeadlineIsCancelledAloneWhenTheCancelDayIsUnknown() {
    setContent(cancelled.copy(cancelledAt = null))

    assertShown(
        C.Tag.rental_detail_status_headline,
        context.getString(R.string.rental_detail_cancelled_headline_no_date),
    )
  }

  @Test
  fun theCancelDayIsTheDayInZurich() {
    // 22:30 UTC on 30 Sep is already 00:30 on 1 Oct in Zurich (UTC+2 in summer).
    setContent(cancelled.copy(cancelledAt = Instant.parse("2026-09-30T22:30:00Z")))

    assertShown(C.Tag.rental_detail_status_headline, "Cancelled 1 Oct")
  }

  @Test
  fun theBodySaysThePriceAndTheDepositAreGivenBack() {
    setContent()

    assertShown(
        C.Tag.rental_detail_status_body,
        context.getString(R.string.rental_detail_cancelled_body),
    )
  }

  @Test
  fun withoutADepositTheBodySaysOnlyThePriceIsGivenBack() {
    setContent(cancelled.copy(deposit = 0))

    assertShown(
        C.Tag.rental_detail_status_body,
        context.getString(R.string.rental_detail_cancelled_body_no_deposit),
    )
  }

  @Test
  fun theDetailsGiveTheDatesTheReturnedPriceAndTheReleasedDeposit() {
    setContent()

    assertShown(C.Tag.rental_detail_dates, "1 – 8 Oct")
    assertShown(C.Tag.rental_detail_price, "120 PP · returned")
    assertShown(C.Tag.rental_detail_accepted_deposit, "60 PP · released")
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_return).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_late_fees).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_offer).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_deposit).assertDoesNotExist()
  }

  @Test
  fun withoutADepositThereIsNoDepositRow() {
    setContent(cancelled.copy(deposit = 0))

    assertShown(C.Tag.rental_detail_price, "120 PP · returned")
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_accepted_deposit).assertDoesNotExist()
  }

  @Test
  fun theBorrowerSeesTheCancelledTexts() {
    assertSameTextsFor(isLender = false)
  }

  @Test
  fun theLenderSeesTheSameTextsAsTheBorrower() {
    assertSameTextsFor(isLender = true)
  }

  /** Asserts the texts of the cancelled rental, which do not depend on the role. */
  private fun assertSameTextsFor(isLender: Boolean) {
    setContent(cancelled.copy(isLender = isLender))

    assertShown(C.Tag.rental_detail_status_headline, "Cancelled 30 Sep")
    assertShown(
        C.Tag.rental_detail_status_body,
        context.getString(R.string.rental_detail_cancelled_body),
    )
    assertShown(C.Tag.rental_detail_price, "120 PP · returned")
    assertShown(C.Tag.rental_detail_accepted_deposit, "60 PP · released")
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
  fun thereIsNoMeetingPointNoCancelRowAndNoBottomButton() {
    setContent()

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_map).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.pickup_map).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_pickup_area).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_pickup_point_label).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_return_point_label).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_cancel_button).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_cancel_until).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_action_button).assertDoesNotExist()
  }
}
