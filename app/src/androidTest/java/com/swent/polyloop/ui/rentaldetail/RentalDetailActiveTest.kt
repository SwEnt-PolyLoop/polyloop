// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Active state of Rental detail (Figma frame 13.02-active). */
@RunWith(AndroidJUnit4::class)
class RentalDetailActiveTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private val active =
      RentalDetailUiState(
          itemTitle = "Ski helmet, size M",
          status = RentalStatus.ACTIVE,
          startDate = LocalDate.of(2026, 10, 1),
          endDate = LocalDate.of(2026, 10, 8),
          rentalPrice = 120,
          deposit = 60,
          pickupAddress = "Route Cantonale 1, Ecublens",
          returnAddress = "Rue de Lausanne 10, Renens",
          otherPerson =
              OtherPerson(uid = "mara", name = "Mara", averageRating = 4.6, reviewCount = 5),
          // The same day as the phone's today in setContent, unless a test sets another.
          serverToday = LocalDate.of(2026, 10, 3),
      )

  private var chatClicks = 0

  private fun setContent(
      state: RentalDetailUiState = active,
      today: LocalDate = LocalDate.of(2026, 10, 3),
  ) {
    composeTestRule.setContent {
      RentalDetailContent(
          uiState = state,
          onRetry = {},
          onChatClick = { chatClicks++ },
          onPersonClick = {},
          onStartPickupClick = {},
          onViewDisputeClick = {},
          today = today,
          zone = ZoneOffset.UTC,
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
  fun theStripReachesInUseAsTheCurrentStage() {
    setContent()

    composeTestRule
        .onAllNodesWithTag(C.Tag.rental_detail_stage_reached, useUnmergedTree = true)
        .assertCountEquals(2)
    assertShown(
        C.Tag.rental_detail_stage_current,
        context.getString(R.string.rental_detail_stage_in_use),
    )
  }

  @Test
  fun theStatusCardGivesTheReturnDay() {
    setContent()

    assertShown(C.Tag.rental_detail_status, context.getString(R.string.rental_status_active))
    assertShown(
        C.Tag.rental_detail_status_headline,
        context.getString(R.string.rental_detail_active_headline, "8 Oct"),
    )
    assertShown(
        C.Tag.rental_detail_status_body,
        context.getString(R.string.rental_detail_active_body),
    )
  }

  @Test
  fun aReturnInAnotherYearShowsTheYear() {
    setContent(active.copy(endDate = LocalDate.of(2027, 1, 5)))

    assertShown(
        C.Tag.rental_detail_status_headline,
        context.getString(R.string.rental_detail_active_headline, "5 Jan 2027"),
    )
  }

  @Test
  fun theDetailsGiveTheReturnByDay() {
    setContent()

    composeTestRule
        .onNodeWithText(context.getString(R.string.rental_detail_return_by_label))
        .performScrollTo()
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.rental_detail_return_label))
        .assertDoesNotExist()
    assertShown(C.Tag.rental_detail_return, "8 Oct")
  }

  @Test
  fun theDetailsGiveThePriceAsPaidAndTheDepositAsHeld() {
    setContent()

    assertShown(
        C.Tag.rental_detail_price,
        context.getString(R.string.rental_detail_price_paid, 120),
    )
    assertShown(
        C.Tag.rental_detail_accepted_deposit,
        context.getString(R.string.rental_detail_deposit_held, 60),
    )
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_late_fees).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_deposit).assertDoesNotExist()
  }

  @Test
  fun anUnknownDepositLeavesOutTheRow() {
    setContent(active.copy(deposit = 0))

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_accepted_deposit).assertDoesNotExist()
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
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_chat_paused).assertDoesNotExist()
  }

  @Test
  fun theReturnPointGivesTheReturnAddressAlone() {
    setContent()

    assertShown(
        C.Tag.rental_detail_return_point_label,
        context.getString(R.string.rental_detail_return_point_label),
    )
    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_map, useUnmergedTree = true)
        .performScrollTo()
        .assertIsDisplayed()
    assertShown(C.Tag.rental_detail_return_address, "Rue de Lausanne 10, Renens")
    // No "Drop-off: …" line, and nothing of the pickup point or the public area.
    composeTestRule
        .onNodeWithText(
            context.getString(R.string.rental_detail_return_address, "Rue de Lausanne 10, Renens")
        )
        .assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_pickup_point_label).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_pickup_address).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_pickup_area).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.pickup_map).assertDoesNotExist()
  }

  @Test
  fun aMissingReturnAddressShowsThePlaceholder() {
    setContent(active.copy(returnAddress = ""))

    assertShown(
        C.Tag.rental_detail_return_address,
        context.getString(R.string.rental_detail_address_placeholder),
    )
  }

  @Test
  fun thereIsNoCancelRow() {
    setContent()

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_cancel_button).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_cancel_until).assertDoesNotExist()
  }
}
