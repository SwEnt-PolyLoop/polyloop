// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Accepted state of Rental detail (Figma frame 13.01). */
@RunWith(AndroidJUnit4::class)
class RentalDetailAcceptedTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private val accepted =
      RentalDetailUiState(
          itemTitle = "Ski helmet, size M",
          status = RentalStatus.ACCEPTED,
          startDate = LocalDate.of(2026, 10, 1),
          endDate = LocalDate.of(2026, 10, 8),
          rentalPrice = 120,
          suggestedDeposit = 200,
          pickupArea = "Ecublens",
          deposit = 60,
          pickupAddress = "Route Cantonale 1, Ecublens",
          returnAddress = "Rue de Lausanne 10, Renens",
      )

  private fun setContent(state: RentalDetailUiState = accepted) {
    composeTestRule.setContent {
      RentalDetailContent(
          uiState = state,
          onRetry = {},
          onChatClick = {},
          onPersonClick = {},
          today = LocalDate.of(2026, 9, 28),
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
  fun theStatusCardGivesThePickupDay() {
    setContent()

    assertShown(C.Tag.rental_detail_status_headline, "Pickup on 1 Oct")
    assertShown(
        C.Tag.rental_detail_status_body,
        context.getString(R.string.rental_detail_accepted_body),
    )
  }

  @Test
  fun aPickupInAnotherYearShowsTheYear() {
    setContent(accepted.copy(startDate = LocalDate.of(2027, 1, 5)))

    assertShown(C.Tag.rental_detail_status_headline, "Pickup on 5 Jan 2027")
  }

  @Test
  fun theDetailsGiveTheDepositWithoutHeldOrASuggestion() {
    setContent()

    assertShown(C.Tag.rental_detail_accepted_deposit, "60 PP")
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_deposit).assertDoesNotExist()
  }

  @Test
  fun theExactAddressesReplaceTheArea() {
    setContent()

    assertShown(C.Tag.rental_detail_pickup_address, "Route Cantonale 1, Ecublens")
    assertShown(
        C.Tag.rental_detail_return_address,
        context.getString(R.string.rental_detail_return_address, "Rue de Lausanne 10, Renens"),
    )
    composeTestRule
        .onNodeWithText(context.getString(R.string.rental_detail_pickup_point_label))
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_pickup_area).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_pickup_note).assertDoesNotExist()
  }

  @Test
  fun aMissingAddressShowsThePlaceholder() {
    setContent(accepted.copy(pickupAddress = ""))

    assertShown(
        C.Tag.rental_detail_pickup_address,
        context.getString(R.string.rental_detail_address_placeholder),
    )
  }

  @Test
  fun thereIsNoCancelButtonYet() {
    setContent()

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_cancel_button).assertDoesNotExist()
  }

  @Test
  fun aRequestStillShowsTheAreaAndNoAcceptedDeposit() {
    setContent(accepted.copy(status = RentalStatus.REQUESTED, deposit = 0))

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_accepted_deposit).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_pickup_address).assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_pickup_area, useUnmergedTree = true)
        .performScrollTo()
        .assertIsDisplayed()
  }
}
