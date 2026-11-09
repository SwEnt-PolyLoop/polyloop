// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.model.location.MapPoint
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Disputed state of Rental detail (Figma frame 13.09). */
@RunWith(AndroidJUnit4::class)
class RentalDetailDisputedTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private val disputed =
      RentalDetailUiState(
          itemTitle = "Ski helmet, size M",
          status = RentalStatus.DISPUTED,
          startDate = LocalDate.of(2026, 10, 1),
          endDate = LocalDate.of(2026, 10, 8),
          rentalPrice = 120,
          pickupArea = "Ecublens",
          pickupPoint = MapPoint(latitude = 46.52, longitude = 6.57),
          otherPerson = OtherPerson("lea", "Léa", averageRating = 4.6, reviewCount = 5),
          deposit = 60,
          pickupAddress = "Route Cantonale 1, Ecublens",
          returnAddress = "Rue de Lausanne 10, Renens",
          lateFees = 0,
      )

  private var chatClicks = 0
  private val personClicks = mutableListOf<String>()

  private fun setContent(state: RentalDetailUiState = disputed) {
    composeTestRule.setContent {
      RentalDetailContent(
          uiState = state,
          onRetry = {},
          onChatClick = { chatClicks++ },
          onPersonClick = { personClicks += it },
          today = LocalDate.of(2026, 10, 12),
          zone = ZoneOffset.UTC,
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
  fun theStatusCardSaysDamageWasFlagged() {
    setContent()

    assertShown(C.Tag.rental_detail_status, context.getString(R.string.rental_status_disputed))
    assertShown(
        C.Tag.rental_detail_status_headline,
        context.getString(R.string.rental_detail_disputed_headline),
    )
    assertShown(
        C.Tag.rental_detail_status_body,
        context.getString(R.string.rental_detail_disputed_body),
    )
  }

  @Test
  fun theDetailsGiveTheDatesThePaidPriceTheHeldDepositAndNoLateFees() {
    setContent()

    assertShown(C.Tag.rental_detail_dates, "1 – 8 Oct")
    assertShown(
        C.Tag.rental_detail_price,
        context.getString(R.string.rental_detail_price_paid, 120),
    )
    assertShown(
        C.Tag.rental_detail_accepted_deposit,
        context.getString(R.string.rental_detail_deposit_held, 60),
    )
    assertShown(
        C.Tag.rental_detail_late_fees,
        context.getString(R.string.rental_detail_late_fees, 0),
    )
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_return).assertDoesNotExist()
  }

  @Test
  fun theDetailsGiveTheLateFeesTaken() {
    setContent(disputed.copy(lateFees = 34))

    assertShown(
        C.Tag.rental_detail_late_fees,
        context.getString(R.string.rental_detail_late_fees, 34),
    )
  }

  @Test
  fun chatIsDisabledAndTappingItDoesNotOpenTheChat() {
    setContent()

    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_chat_button)
        .performScrollTo()
        .assertIsNotEnabled()
        .performClick()

    assertEquals(0, chatClicks)
  }

  @Test
  fun theChatPausedNoteIsShown() {
    setContent()

    assertShown(
        C.Tag.rental_detail_chat_paused,
        context.getString(R.string.rental_detail_chat_paused),
    )
  }

  @Test
  fun tappingThePersonCardStillOpensTheirProfile() {
    setContent()

    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_person_card)
        .performScrollTo()
        .performTouchInput { click(centerLeft + Offset(8f, 0f)) }

    assertEquals(listOf("lea"), personClicks)
  }

  @Test
  fun thereIsNoMapNoMeetingPointAndNoCancelRow() {
    setContent()

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_map).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.pickup_map).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_pickup_point_label).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_pickup_area).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_cancel_button).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_cancel_until).assertDoesNotExist()
  }
}
