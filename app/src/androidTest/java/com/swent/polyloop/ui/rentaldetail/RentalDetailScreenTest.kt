// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RentalDetailScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val request =
      Rental(
          id = "rental-1",
          listingId = "listing-1",
          listingTitle = "Camping tent",
          ownerId = "lender",
          ownerName = "Vida",
          borrowerId = "borrower",
          borrowerName = "Loca",
          startDate = LocalDate.of(2026, 10, 20),
          endDate = LocalDate.of(2026, 10, 22),
          status = RentalStatus.REQUESTED,
          pricePerDay = 5,
          totalPrice = 15,
          pickupArea = "Ecublens",
          dropOffArea = "Renens",
          createdAt = Instant.parse("2026-10-13T10:00:00Z"),
      )

  /** Shows the stateless content with [state]; the callbacks do nothing. */
  private fun setContent(state: RentalDetailUiState) {
    composeTestRule.setContent {
      RentalDetailContent(
          uiState = state,
          onPickupAddressChange = {},
          onReturnAddressChange = {},
          onDepositChange = {},
          onAccept = {},
          onDecline = {},
          onRetry = {},
      )
    }
  }

  private val lenderState =
      RentalDetailUiState(
          rental = request,
          isLoading = false,
          isLender = true,
          deposit = "120",
      )

  @Test
  fun requestShowsItemAndForm() {
    setContent(lenderState)

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_item_title).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_pickup_field).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_deposit_field).assertIsDisplayed()
  }

  @Test
  fun acceptIsDisabledUntilTheFormIsFilledIn() {
    setContent(lenderState)

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_accept_button).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_decline_button).assertIsEnabled()
  }

  @Test
  fun acceptIsEnabledWhenAddressesAndDepositAreValid() {
    setContent(lenderState.copy(pickupAddress = "EPFL", returnAddress = "Renens"))

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_accept_button).assertIsEnabled()
  }

  @Test
  fun borrowerSeesNoAnswerForm() {
    setContent(lenderState.copy(isLender = false))

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_no_action).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_accept_button).assertDoesNotExist()
  }

  @Test
  fun errorShowsRetryButton() {
    setContent(RentalDetailUiState(isLoading = false, errorMsg = ""))

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_error).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_retry_button).performClick()
  }

  @Test
  fun typingInThePickupFieldReportsTheChange() {
    var pickup = ""
    composeTestRule.setContent {
      RentalDetailContent(
          uiState = lenderState,
          onPickupAddressChange = { pickup = it },
          onReturnAddressChange = {},
          onDepositChange = {},
          onAccept = {},
          onDecline = {},
          onRetry = {},
      )
    }

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_pickup_field).performTextInput("EPFL")

    assertEquals("EPFL", pickup)
  }
}
