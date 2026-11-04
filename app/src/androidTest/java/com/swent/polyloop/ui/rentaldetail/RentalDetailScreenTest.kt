// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RentalDetailScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  /** A waiting request from [OtherPerson] "Ana" to the lender, for 3 days at 15 PP a day. */
  private val request =
      RentalDetailUiState(
          itemTitle = "Camping tent",
          status = RentalStatus.REQUESTED,
          isLender = true,
          startDate = LocalDate.of(2026, 10, 10),
          endDate = LocalDate.of(2026, 10, 12),
          rentalPrice = 45,
          otherPerson =
              OtherPerson(uid = "borrower", name = "Ana", averageRating = null, reviewCount = null),
      )

  private fun setContent(state: RentalDetailUiState) {
    composeTestRule.setContent {
      RentalDetailContent(
          uiState = state,
          onDepositChange = {},
          onPickupChange = {},
          onReturnChange = {},
          onAccept = {},
          onDecline = {},
          onRetry = {},
      )
    }
  }

  @Test
  fun acceptIsDisabledUntilTheDepositAndBothAddressesAreFilledIn() {
    setContent(request.copy(depositInput = "60"))

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_accept_button).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_decline_button).assertIsEnabled()
  }

  @Test
  fun acceptIsEnabledWhenTheAnswerIsComplete() {
    setContent(
        request.copy(
            depositInput = "60",
            pickupAddress = "Route Cantonale 1",
            returnAddress = "Rue de Lausanne 10",
        )
    )

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_accept_button).assertIsEnabled()
  }

  @Test
  fun showsHowManyRequestsAcceptingWouldDecline() {
    setContent(request.copy(overlapCount = 2))

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_overlap).assertIsDisplayed()
  }

  @Test
  fun otherUsersSeeNoAnswerButtons() {
    setContent(request.copy(isLender = false))

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_not_lender).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.rental_detail_accept_button).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(C.Tag.rental_detail_decline_button).assertCountEquals(0)
  }
}
