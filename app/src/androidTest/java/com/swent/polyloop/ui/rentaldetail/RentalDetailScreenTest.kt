// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RentalDetailScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private val today = LocalDate.of(2026, 9, 28)

  private val requested =
      RentalDetailUiState(
          isLender = false,
          itemTitle = "Ski helmet, size M",
          status = RentalStatus.REQUESTED,
          sentAt = Instant.parse("2026-09-28T10:00:00Z"),
          startDate = LocalDate.of(2026, 10, 1),
          endDate = LocalDate.of(2026, 10, 8),
          rentalPrice = 120,
          suggestedDeposit = 180,
          pickupArea = "Ecublens",
          otherPerson =
              OtherPerson(uid = "lea", name = "Léa", averageRating = 4.6, reviewCount = 5),
      )

  private var retries = 0
  private var chatClicks = 0
  private val personClicks = mutableListOf<String>()

  private fun setContent(uiState: RentalDetailUiState = requested) {
    composeTestRule.setContent {
      RentalDetailContent(
          uiState = uiState,
          onRetry = { retries++ },
          onChatClick = { chatClicks++ },
          onPersonClick = { personClicks += it },
          today = today,
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

  private fun assertAbsent(tag: String) {
    composeTestRule.onNodeWithTag(tag).assertDoesNotExist()
  }

  @Test
  fun loadingShowsTheSpinner() {
    setContent(RentalDetailUiState(isLoading = true))

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_screen).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_loading).assertIsDisplayed()
    assertAbsent(C.Tag.rental_detail_title)
  }

  @Test
  fun errorShowsItsMessageAndRetryCallsOnRetry() {
    setContent(RentalDetailUiState(errorMsg = "Network down"))

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_error).assertTextEquals("Network down")
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_retry_button).performClick()
    assertEquals(1, retries)
    assertAbsent(C.Tag.rental_detail_title)
  }

  @Test
  fun blankErrorShowsTheFallback() {
    setContent(RentalDetailUiState(errorMsg = ""))

    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_error)
        .assertTextEquals(context.getString(R.string.rental_detail_error_fallback))
  }

  @Test
  fun notFoundShowsItsMessage() {
    setContent(RentalDetailUiState(notFound = true))

    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_not_found)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.rental_detail_not_found))
    assertAbsent(C.Tag.rental_detail_title)
  }

  @Test
  fun unsupportedStatusShowsTheTitleAndTextOnly() {
    setContent(RentalDetailUiState(isSupported = false, itemTitle = "Ski helmet, size M"))

    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_title)
        .assertIsDisplayed()
        .assertTextEquals("Ski helmet, size M")
    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_unsupported)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.rental_detail_unsupported))
    assertAbsent(C.Tag.rental_detail_loading)
    assertAbsent(C.Tag.rental_detail_error)
    assertAbsent(C.Tag.rental_detail_not_found)
    assertAbsent(C.Tag.rental_detail_stage_strip)
    assertAbsent(C.Tag.rental_detail_status)
    assertAbsent(C.Tag.rental_detail_price)
    assertAbsent(C.Tag.rental_detail_person_card)
    assertAbsent(C.Tag.rental_detail_map)
    assertAbsent(C.Tag.rental_detail_cancel_button)
  }

  @Test
  fun requestedShowsTheTitle() {
    setContent()

    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_title)
        .assertIsDisplayed()
        .assertTextEquals("Ski helmet, size M")
    assertAbsent(C.Tag.rental_detail_unsupported)
  }

  @Test
  fun stageStripShowsItsFourLabels() {
    setContent()

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_stage_strip).assertIsDisplayed()
    listOf(
            R.string.rental_detail_stage_accepted,
            R.string.rental_detail_stage_in_use,
            R.string.rental_detail_stage_returned,
            R.string.rental_detail_stage_closed,
        )
        .forEach { label ->
          composeTestRule
              .onNode(
                  hasText(context.getString(label)) and
                      hasAnyAncestor(hasTestTag(C.Tag.rental_detail_stage_strip))
              )
              .assertIsDisplayed()
        }
  }

  @Test
  fun statusCardShowsTheBadgeAndHeadline() {
    setContent()

    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_status)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.rental_status_requested))
    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_status_headline)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.rental_detail_status_headline))
  }

  @Test
  fun statusBodyShowsTheSentDayWithoutTheYear() {
    setContent()

    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_status_body)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.rental_detail_status_body, "28 Sep"))
  }

  @Test
  fun statusBodyShowsTheYearWhenSentInAnotherYear() {
    setContent(requested.copy(sentAt = Instant.parse("2025-12-30T10:00:00Z")))

    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_status_body)
        .assertTextEquals(context.getString(R.string.rental_detail_status_body, "30 Dec 2025"))
  }

  @Test
  fun detailsCardShowsEachRow() {
    setContent()

    assertShown(C.Tag.rental_detail_dates, "1 – 8 Oct")
    assertShown(C.Tag.rental_detail_return, "8 Oct")
    assertShown(C.Tag.rental_detail_price, context.getString(R.string.rental_detail_price, 120))
    assertShown(C.Tag.rental_detail_deposit, context.getString(R.string.listing_card_price, 180))
  }

  @Test
  fun datesAcrossTwoMonthsShowBothMonths() {
    setContent(
        requested.copy(startDate = LocalDate.of(2026, 10, 28), endDate = LocalDate.of(2026, 11, 3))
    )

    assertShown(C.Tag.rental_detail_dates, "28 Oct – 3 Nov")
    assertShown(C.Tag.rental_detail_return, "3 Nov")
  }

  @Test
  fun returnShowsTheYearWhenNotThisYear() {
    setContent(
        requested.copy(startDate = LocalDate.of(2027, 1, 2), endDate = LocalDate.of(2027, 1, 5))
    )

    assertShown(C.Tag.rental_detail_return, "5 Jan 2027")
  }

  @Test
  fun depositRowIsAbsentWhenUnknown() {
    setContent(requested.copy(suggestedDeposit = null))

    assertShown(C.Tag.rental_detail_price, context.getString(R.string.rental_detail_price, 120))
    assertAbsent(C.Tag.rental_detail_deposit)
  }

  private fun withPerson(name: String = "Léa", averageRating: Double? = 4.6, reviewCount: Int? = 5) =
      requested.copy(otherPerson = OtherPerson("lea", name, averageRating, reviewCount))

  @Test
  fun personCardShowsTheInitialAndName() {
    setContent()

    assertShown(C.Tag.rental_detail_person_initial, "L")
    assertShown(C.Tag.rental_detail_person_name, "Léa")
  }

  @Test
  fun blankNameShowsThePlaceholderAndAQuestionMark() {
    setContent(withPerson(name = "  "))

    assertShown(C.Tag.rental_detail_person_initial, "?")
    assertShown(
        C.Tag.rental_detail_person_name,
        context.getString(R.string.profile_name_placeholder),
    )
  }

  @Test
  fun ratingShowsTheAverageAndReviewCount() {
    setContent()

    assertShown(C.Tag.rental_detail_person_rating, "4.6 · 5 reviews")
  }

  @Test
  fun noReviewsShowsNoReviewsYet() {
    setContent(withPerson(averageRating = 0.0, reviewCount = 0))

    assertShown(
        C.Tag.rental_detail_person_rating,
        context.getString(R.string.profile_no_reviews),
    )
  }

  @Test
  fun unknownRatingHidesTheRatingLine() {
    setContent(withPerson(averageRating = null, reviewCount = null))

    assertShown(C.Tag.rental_detail_person_name, "Léa")
    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_person_rating, useUnmergedTree = true)
        .assertDoesNotExist()
  }

  @Test
  fun noOtherPersonShowsNoCard() {
    setContent(requested.copy(otherPerson = null))

    assertAbsent(C.Tag.rental_detail_person_card)
    assertAbsent(C.Tag.rental_detail_chat_button)
  }

  @Test
  fun tappingThePersonCardCallsOnPersonClickWithTheUid() {
    setContent()

    // The left edge is the avatar side, away from the Chat button.
    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_person_card)
        .performScrollTo()
        .performTouchInput { click(centerLeft + Offset(8f, 0f)) }

    assertEquals(listOf("lea"), personClicks)
    assertEquals(0, chatClicks)
  }

  @Test
  fun tappingChatCallsOnChatClickOnly() {
    setContent()

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_chat_button).performScrollTo().performClick()

    assertEquals(1, chatClicks)
    assertEquals(emptyList<String>(), personClicks)
  }

  @Test
  fun pickupAreaShowsTheMapBoxTheAreaAndTheNote() {
    setContent()

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_map).performScrollTo().assertIsDisplayed()
    assertShown(C.Tag.rental_detail_pickup_area, "Ecublens")
    assertShown(
        C.Tag.rental_detail_pickup_note,
        context.getString(R.string.rental_detail_pickup_note),
    )
  }

  @Test
  fun blankPickupAreaShowsThePlaceholder() {
    setContent(requested.copy(pickupArea = ""))

    assertShown(
        C.Tag.rental_detail_pickup_area,
        context.getString(R.string.rental_detail_area_placeholder),
    )
  }

  @Test
  fun borrowerSeesTheCancelRow() {
    setContent()

    assertShown(
        C.Tag.rental_detail_cancel_until,
        context.getString(R.string.rental_detail_cancel_until, "Wed 30 Sep"),
    )
    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_cancel_button)
        .performScrollTo()
        .assertIsDisplayed()
  }

  @Test
  fun cancelRowShowsOnTheLastCancelDay() {
    // Pickup tomorrow: today is the last day to cancel.
    setContent(requested.copy(startDate = today.plusDays(1)))

    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_cancel_button)
        .performScrollTo()
        .assertIsDisplayed()
  }

  @Test
  fun cancelRowShowsTheYearWhenNotThisYear() {
    setContent(
        requested.copy(startDate = LocalDate.of(2027, 1, 6), endDate = LocalDate.of(2027, 1, 8))
    )

    assertShown(
        C.Tag.rental_detail_cancel_until,
        context.getString(R.string.rental_detail_cancel_until, "Tue 5 Jan 2027"),
    )
  }

  @Test
  fun lenderSeesNoCancelRow() {
    setContent(requested.copy(isLender = true))

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_map).performScrollTo()
    assertAbsent(C.Tag.rental_detail_cancel_until)
    assertAbsent(C.Tag.rental_detail_cancel_button)
  }

  @Test
  fun noCancelRowOnThePickupDay() {
    setContent(requested.copy(startDate = today))

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_map).performScrollTo()
    assertAbsent(C.Tag.rental_detail_cancel_until)
    assertAbsent(C.Tag.rental_detail_cancel_button)
  }

  @Test
  fun tappingCancelRequestChangesNothing() {
    setContent()

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_cancel_button).performScrollTo().performClick()

    composeTestRule.onNodeWithTag(C.Tag.rental_detail_cancel_button).assertIsDisplayed()
    assertShown(C.Tag.rental_detail_title, "Ski helmet, size M")
    assertEquals(0, retries)
    assertEquals(0, chatClicks)
    assertEquals(emptyList<String>(), personClicks)
  }
}
