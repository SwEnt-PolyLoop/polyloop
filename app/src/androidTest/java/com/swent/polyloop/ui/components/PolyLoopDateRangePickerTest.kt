// Made with Claude.

package com.swent.polyloop.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.resources.C
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PolyLoopDateRangePickerTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private fun oct(day: Int) = LocalDate.of(2026, 10, day)

  // The mock-up data.
  private val window = oct(6)..oct(20)
  private val booked = setOf(oct(10), oct(11))
  private val requestMode = DateRangePickerMode.Request(pricePerDay = 15, balance = 120)
  private val today = oct(1)

  private val confirmed = mutableListOf<Pair<LocalDate, LocalDate>>()

  private fun setBody(
      window: ClosedRange<LocalDate> = this.window,
      disabledDates: Set<LocalDate> = booked,
      mode: DateRangePickerMode = requestMode,
      today: LocalDate = this.today,
  ) {
    composeTestRule.setContent {
      DateRangePickerBody(
          window = window,
          disabledDates = disabledDates,
          mode = mode,
          onConfirm = { start, end -> confirmed += start to end },
          today = today,
      )
    }
  }

  private fun day(date: LocalDate) = composeTestRule.onNodeWithTag(C.Tag.datePickerDay(date))

  private fun tap(vararg dates: LocalDate) = dates.forEach { day(it).performClick() }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  @Test
  fun requestModeShowsMonthAvailableLegendAndSendRequest() {
    setBody()

    node(C.Tag.date_picker_month_title).assertTextEquals("October 2026")
    node(C.Tag.date_picker_available)
        .assertTextEquals(context.getString(R.string.date_picker_available, "6", "20 Oct"))
    node(C.Tag.date_picker_legend).assertIsDisplayed()
    node(C.Tag.date_picker_confirm_button)
        .assert(hasText(context.getString(R.string.date_picker_send_request)))
        .assertIsNotEnabled()
  }

  @Test
  fun availabilityModeShowsConfirmDatesWithoutSummaryOrLegend() {
    setBody(window = today..today.plusYears(1), mode = DateRangePickerMode.Availability)
    tap(oct(15), oct(17))

    node(C.Tag.date_picker_confirm_button)
        .assert(hasText(context.getString(R.string.date_picker_confirm_dates)))
        .assertIsEnabled()
    node(C.Tag.date_picker_summary).assertDoesNotExist()
    node(C.Tag.date_picker_legend).assertDoesNotExist()
    node(C.Tag.date_picker_available).assertDoesNotExist()
  }

  @Test
  fun bookedBufferAndOutOfWindowDaysAreNotEnabled() {
    setBody()

    day(oct(10))
        .assertIsNotEnabled()
        .assert(hasContentDescription(context.getString(R.string.date_picker_day_booked, 10)))
    day(oct(12))
        .assertIsNotEnabled()
        .assert(hasContentDescription(context.getString(R.string.date_picker_day_buffer, 12)))
    day(oct(5)).assertIsNotEnabled()
    day(oct(6)).assertIsEnabled()
  }

  @Test
  fun pickingRangeSelectsDaysAndShowsSummary() {
    setBody()
    tap(oct(15), oct(17))

    day(oct(15)).assertIsSelected()
    day(oct(16)).assertIsSelected()
    day(oct(17)).assertIsSelected()
    day(oct(14)).assertIsNotSelected()
    day(oct(18)).assertIsNotSelected()
    node(C.Tag.date_picker_summary).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(
            context.resources.getQuantityString(R.plurals.date_picker_summary_days, 3, 3, 15)
        )
        .assertIsDisplayed()
    node(C.Tag.date_picker_summary_total)
        .assertTextEquals(context.getString(R.string.listing_card_price, 45))
    node(C.Tag.date_picker_balance)
        .assertTextEquals(context.getString(R.string.listing_card_price, 120))
    node(C.Tag.date_picker_confirm_button).assertIsEnabled()
  }

  @Test
  fun bufferViolationShowsErrorAndKeepsButtonDisabled() {
    setBody(window = oct(1)..oct(10), disabledDates = setOf(oct(6), oct(7), oct(8)))

    tap(oct(4), oct(5))
    node(C.Tag.date_picker_error)
        .assertTextEquals(context.getString(R.string.date_picker_buffer_error))
    node(C.Tag.date_picker_confirm_button).assertIsNotEnabled()

    tap(oct(3), oct(4))
    node(C.Tag.date_picker_error).assertDoesNotExist()
    node(C.Tag.date_picker_confirm_button).assertIsEnabled()
  }

  @Test
  fun confirmCallsOnConfirmWithRange() {
    setBody()
    tap(oct(15), oct(17))
    node(C.Tag.date_picker_confirm_button).performClick()

    assertEquals(listOf(oct(15) to oct(17)), confirmed)
  }

  @Test
  fun monthNavigationMovesBetweenMonthsAndStopsAtWindowEnds() {
    val today = LocalDate.of(2026, 10, 13)
    setBody(
        window = today..LocalDate.of(2027, 10, 13),
        mode = DateRangePickerMode.Availability,
        today = today,
    )

    node(C.Tag.date_picker_month_title).assertTextEquals("October 2026")
    node(C.Tag.date_picker_prev_month).assertIsNotEnabled()
    node(C.Tag.date_picker_next_month).performClick()
    node(C.Tag.date_picker_month_title).assertTextEquals("November 2026")
    node(C.Tag.date_picker_prev_month).assertIsEnabled()

    repeat(11) { node(C.Tag.date_picker_next_month).performClick() }
    node(C.Tag.date_picker_month_title).assertTextEquals("October 2027")
    node(C.Tag.date_picker_next_month).assertIsNotEnabled()
  }

  @Test
  fun navigationArrowsHiddenForOneMonthWindow() {
    setBody()

    node(C.Tag.date_picker_prev_month).assertDoesNotExist()
    node(C.Tag.date_picker_next_month).assertDoesNotExist()
  }

  @Test
  fun sheetOpensEmptyEachTime() {
    var visible by mutableStateOf(true)
    composeTestRule.setContent {
      if (visible) {
        PolyLoopDateRangePicker(
            window = window,
            disabledDates = booked,
            mode = requestMode,
            onConfirm = { _, _ -> },
            onDismiss = { visible = false },
            today = today,
        )
      }
    }
    tap(oct(15), oct(17))
    node(C.Tag.date_picker_confirm_button).assertIsEnabled()

    visible = false
    composeTestRule.waitForIdle()
    node(C.Tag.date_picker_sheet).assertDoesNotExist()
    visible = true
    composeTestRule.waitForIdle()

    composeTestRule.onAllNodes(isSelected()).assertCountEquals(0)
    node(C.Tag.date_picker_confirm_button).assertIsNotEnabled()
  }

  @Test
  fun dismissCallsOnDismiss() {
    var dismissed = 0
    composeTestRule.setContent {
      PolyLoopDateRangePicker(
          window = window,
          disabledDates = booked,
          mode = requestMode,
          onConfirm = { _, _ -> },
          onDismiss = { dismissed++ },
          today = today,
      )
    }
    node(C.Tag.date_picker_sheet).assertIsDisplayed()

    node(C.Tag.date_picker_sheet).performTouchInput { swipeDown() }
    composeTestRule.waitUntil(timeoutMillis = 5_000) { dismissed > 0 }

    assertEquals(1, dismissed)
  }
}
