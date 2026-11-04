// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
}
