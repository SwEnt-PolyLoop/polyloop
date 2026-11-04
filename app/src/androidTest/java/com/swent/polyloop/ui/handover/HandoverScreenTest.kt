// Made with Claude.

package com.swent.polyloop.ui.handover

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.model.handover.HandoverPhase
import com.swent.polyloop.resources.C
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HandoverScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val pickup =
      HandoverUiState(
          isLoading = false,
          itemTitle = "Camping tent",
          otherPersonName = "Mara",
          phase = HandoverPhase.PICKUP,
          pickupDate = LocalDate.of(2026, 10, 6),
          isPickupDay = true,
      )

  private var retries = 0

  private fun setContent(uiState: HandoverUiState = pickup) {
    composeTestRule.setContent { HandoverContent(uiState = uiState, onRetry = { retries++ }) }
  }

  private fun assertShown(tag: String) = composeTestRule.onNodeWithTag(tag).assertIsDisplayed()

  private fun assertAbsent(tag: String) = composeTestRule.onNodeWithTag(tag).assertDoesNotExist()

  @Test
  fun showsASpinnerWhileLoading() {
    setContent(HandoverUiState())

    assertShown(C.Tag.handover_loading)
    assertAbsent(C.Tag.handover_error)
  }

  @Test
  fun aFailedLoadShowsAnErrorWhoseRetryButtonRetries() {
    setContent(HandoverUiState(isLoading = false, loadFailed = true))

    assertShown(C.Tag.handover_error)
    composeTestRule.onNodeWithTag(C.Tag.handover_retry_button).performClick()
    assertEquals(1, retries)
  }

  @Test
  fun aMissingRentalSaysSo() {
    setContent(HandoverUiState(isLoading = false, notFound = true))

    assertShown(C.Tag.handover_not_found)
  }

  @Test
  fun aRentalThatTakesNoPhotosHasNothingToHandOver() {
    setContent(pickup.copy(phase = null))

    assertShown(C.Tag.handover_nothing)
    assertAbsent(C.Tag.handover_loading)
  }

  // ---------- header ----------

  @Test
  fun theBorrowerSeesPickupYouBorrowTheItemAndTheLender() {
    setContent()

    composeTestRule.onNodeWithTag(C.Tag.handover_title).assertTextEquals("Pickup")
    composeTestRule.onNodeWithTag(C.Tag.handover_role_pill).assertTextEquals("You borrow")
    composeTestRule.onNodeWithTag(C.Tag.handover_item).assertTextEquals("Camping tent")
    composeTestRule.onNodeWithTag(C.Tag.handover_other_person).assertTextEquals("from Mara")
  }

  @Test
  fun theLenderSeesYouLendAndTheBorrower() {
    setContent(pickup.copy(isLender = true, otherPersonName = "Loca"))

    composeTestRule.onNodeWithTag(C.Tag.handover_role_pill).assertTextEquals("You lend")
    composeTestRule.onNodeWithTag(C.Tag.handover_other_person).assertTextEquals("with Loca")
  }

  @Test
  fun aBlankNameShowsTheOtherPersonsRole() {
    setContent(pickup.copy(isLender = true, otherPersonName = " "))
    composeTestRule.onNodeWithTag(C.Tag.handover_other_person).assertTextEquals("with the borrower")
  }

  @Test
  fun aBlankLenderNameShowsTheLender() {
    setContent(pickup.copy(otherPersonName = ""))
    composeTestRule.onNodeWithTag(C.Tag.handover_other_person).assertTextEquals("from the lender")
  }
}
