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

/** The Declined state of Rental detail (Figma frame 13.06-declined), for both roles. */
@RunWith(AndroidJUnit4::class)
class RentalDetailDeclinedTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  // The pickup area, its point and the suggested deposit are set, as the listing is still read for a
  // declined rental, so that a meeting point or a suggested deposit would show if the screen drew
  // one. A declined rental never had its deposit held, so its deposit is 0.
  private val declined =
      RentalDetailUiState(
          itemTitle = "Ski helmet, size M",
          status = RentalStatus.DECLINED,
          sentAt = Instant.parse("2026-09-25T10:00:00Z"),
          startDate = LocalDate.of(2026, 10, 1),
          endDate = LocalDate.of(2026, 10, 8),
          rentalPrice = 120,
          suggestedDeposit = 150,
          pickupArea = "Ecublens",
          pickupPoint = MapPoint(latitude = 46.52, longitude = 6.57),
          otherPerson = OtherPerson("lea", "Léa", averageRating = 4.6, reviewCount = 5),
      )

  private var chatClicks = 0

  private fun setContent(state: RentalDetailUiState = declined) {
    composeTestRule.setContent {
      RentalDetailContent(
          uiState = state,
          onRetry = {},
          onChatClick = { chatClicks++ },
          onPersonClick = {},
          onStartPickupClick = {},
          onStartReturnClick = {},
          onViewDisputeClick = {},
          onCheckItemClick = {},
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
  fun theBadgeSaysDeclined() {
    setContent()

    assertShown(C.Tag.rental_detail_status, context.getString(R.string.rental_status_declined))
  }

  @Test
  fun theHeadlineSaysTheRequestWasDeclined() {
    setContent()

    assertShown(C.Tag.rental_detail_status_headline, "Request declined")
  }

  @Test
  fun theBodySaysThePriceAndTheDepositAreGivenBack() {
    setContent()

    assertShown(C.Tag.rental_detail_status_body, "The price and deposit are given back.")
  }

  @Test
  fun theDetailsGiveTheDatesAndTheReturnedPrice() {
    setContent()

    assertShown(C.Tag.rental_detail_dates, "1 – 8 Oct")
    assertShown(C.Tag.rental_detail_price, "120 PP · returned")
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_return).assertDoesNotExist()
  }

  @Test
  fun withoutADepositThereIsNoDepositRow() {
    setContent()

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_accepted_deposit).assertDoesNotExist()
  }

  @Test
  fun withADepositTheDepositShowsAsReleased() {
    setContent(declined.copy(deposit = 60))

    assertShown(C.Tag.rental_detail_accepted_deposit, "60 PP · released")
  }

  @Test
  fun thereIsNoLateFeesOfferOrSuggestedDepositRow() {
    setContent()

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_late_fees).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_offer).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_deposit).assertDoesNotExist()
  }

  @Test
  fun theBorrowerSeesTheDeclinedTexts() {
    assertSameTextsFor(isLender = false)
  }

  @Test
  fun theLenderSeesTheSameTextsAsTheBorrower() {
    assertSameTextsFor(isLender = true)
  }

  /** Asserts the texts of the declined rental, which do not depend on the role. */
  private fun assertSameTextsFor(isLender: Boolean) {
    setContent(declined.copy(isLender = isLender))

    assertShown(C.Tag.rental_detail_status, context.getString(R.string.rental_status_declined))
    assertShown(C.Tag.rental_detail_status_headline, "Request declined")
    assertShown(C.Tag.rental_detail_status_body, "The price and deposit are given back.")
    assertShown(C.Tag.rental_detail_dates, "1 – 8 Oct")
    assertShown(C.Tag.rental_detail_price, "120 PP · returned")
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

    assertNoMeetingPointCancelRowOrBottomButton()
  }

  @Test
  fun theLenderHasNoReviewRequestButton() {
    // A request with no offer would give its lender "Review request"; a declined one does not.
    setContent(declined.copy(isLender = true))

    assertNoMeetingPointCancelRowOrBottomButton()
  }

  private fun assertNoMeetingPointCancelRowOrBottomButton() {
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
