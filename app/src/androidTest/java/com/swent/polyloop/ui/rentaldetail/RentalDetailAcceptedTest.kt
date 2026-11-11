// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
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
          pickupPoint = MapPoint(latitude = 46.52, longitude = 6.57),
          deposit = 60,
          pickupAddress = "Route Cantonale 1, Ecublens",
          returnAddress = "Rue de Lausanne 10, Renens",
      )

  private var startPickupClicks = 0

  private fun setContent(
      state: RentalDetailUiState = accepted,
      today: LocalDate = LocalDate.of(2026, 9, 28),
      // English is fixed so the expected dates pass on an emulator in any language.
      locale: Locale = Locale.ENGLISH,
  ) {
    composeTestRule.setContent {
      RentalDetailContent(
          uiState = state,
          onRetry = {},
          onChatClick = {},
          onPersonClick = {},
          onStartPickupClick = { startPickupClicks++ },
          onViewDisputeClick = {},
          today = today,
          zone = ZoneOffset.UTC,
          locale = locale,
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

    assertShown(
        C.Tag.rental_detail_status_headline,
        context.getString(
            R.string.rental_detail_accepted_headline,
            "1 Oct",
        ),
    )
    assertShown(
        C.Tag.rental_detail_status_body,
        context.getString(R.string.rental_detail_accepted_body),
    )
  }

  @Test
  fun theStatusCardWritesThePickupDayInTheGivenLocale() {
    setContent(locale = Locale.FRENCH)

    assertShown(
        C.Tag.rental_detail_status_headline,
        context.getString(R.string.rental_detail_accepted_headline, "1 oct."),
    )
  }

  @Test
  fun aPickupInAnotherYearShowsTheYear() {
    setContent(accepted.copy(startDate = LocalDate.of(2027, 1, 5)))

    assertShown(
        C.Tag.rental_detail_status_headline,
        context.getString(
            R.string.rental_detail_accepted_headline,
            "5 Jan 2027",
        ),
    )
  }

  @Test
  fun theFirstStageIsReached() {
    setContent()

    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_stage_reached, useUnmergedTree = true)
        .assertExists()
  }

  @Test
  fun theDetailsGiveTheDepositWithoutHeldOrASuggestion() {
    setContent()

    assertShown(
        C.Tag.rental_detail_accepted_deposit,
        context.getString(R.string.listing_card_price, 60),
    )
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_deposit).assertDoesNotExist()
  }

  @Test
  fun anUnknownDepositLeavesOutTheRow() {
    setContent(accepted.copy(deposit = 0))

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_accepted_deposit).assertDoesNotExist()
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
        .onNodeWithTag(C.Tag.rental_detail_pickup_point_label, useUnmergedTree = true)
        .performScrollTo()
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_pickup_area).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_pickup_note).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.pickup_map).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.pickup_map_placeholder).assertDoesNotExist()
  }

  @Test
  fun aMissingAddressShowsThePlaceholder() {
    setContent(accepted.copy(pickupAddress = "", returnAddress = ""))
    val placeholder = context.getString(R.string.rental_detail_address_placeholder)

    assertShown(C.Tag.rental_detail_pickup_address, placeholder)
    assertShown(
        C.Tag.rental_detail_return_address,
        context.getString(R.string.rental_detail_return_address, placeholder),
    )
  }

  @Test
  fun theDayBeforeThePickupStartPickupIsDisabledWithTheDay() {
    setContent(today = LocalDate.of(2026, 9, 30))

    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_action_button)
        .assertIsDisplayed()
        .assertIsNotEnabled()
        .assertTextEquals(context.getString(R.string.rental_detail_start_pickup))
    assertPinned(
        C.Tag.rental_detail_pickup_available_on,
        context.getString(R.string.rental_detail_pickup_available_on, "1 Oct"),
    )
  }

  /** Like [assertShown], for a node pinned under the scrolling column, so not scrolled to. */
  private fun assertPinned(tag: String, text: String) {
    composeTestRule.onNodeWithTag(tag).assertIsDisplayed().assertTextEquals(text)
  }

  @Test
  fun aPickupInAnotherYearGivesTheYearUnderStartPickup() {
    setContent(accepted.copy(startDate = LocalDate.of(2027, 1, 5)))

    assertPinned(
        C.Tag.rental_detail_pickup_available_on,
        context.getString(
            R.string.rental_detail_pickup_available_on,
            "5 Jan 2027",
        ),
    )
  }

  @Test
  fun onThePickupDayStartPickupIsEnabledAndOpensThePickup() {
    assertStartPickupOpensThePickupOn(LocalDate.of(2026, 10, 1))
  }

  @Test
  fun theDayAfterThePickupStartPickupIsStillEnabled() {
    assertStartPickupOpensThePickupOn(LocalDate.of(2026, 10, 2))
  }

  private fun assertStartPickupOpensThePickupOn(today: LocalDate) {
    setContent(today = today)

    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_action_button)
        .assertIsEnabled()
        .assertTextEquals(context.getString(R.string.rental_detail_start_pickup))
        .performClick()

    assertEquals(1, startPickupClicks)
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_pickup_available_on).assertDoesNotExist()
  }

  @Test
  fun aRequestStillShowsTheAreaAndNoAcceptedDeposit() {
    setContent(accepted.copy(status = RentalStatus.REQUESTED))

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_accepted_deposit).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_stage_reached).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_pickup_address).assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_pickup_area, useUnmergedTree = true)
        .performScrollTo()
        .assertIsDisplayed()
  }
}
