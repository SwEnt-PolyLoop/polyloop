// Made with Claude.

package com.swent.polyloop.ui.components

import com.swent.polyloop.model.listing.BookedRange
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DateRangeSelectionTest {

  private fun oct(day: Int) = LocalDate.of(2026, 10, day)

  private val today = oct(1)
  private val window = oct(1)..oct(10)
  private val booked = setOf(oct(6), oct(7), oct(8))

  /** Taps [days] in order on an empty selection, with the worked example's window and bookings. */
  private fun tap(
      vararg days: LocalDate,
      disabledDates: Set<LocalDate> = booked,
      window: ClosedRange<LocalDate> = this.window,
      requireBufferDay: Boolean = true,
  ): DateRangeSelection =
      days.fold(DateRangeSelection()) { selection, day ->
        selection.onTap(day, window, disabledDates, today, requireBufferDay)
      }

  @Test
  fun bufferDayFollowsEachBookedStretch() {
    assertEquals(setOf(oct(9)), bufferDays(setOf(oct(6), oct(7), oct(8))))
    assertEquals(setOf(oct(12), oct(21)), bufferDays(setOf(oct(10), oct(11), oct(20))))
    assertEquals(emptySet<LocalDate>(), bufferDays(emptySet()))
  }

  @Test
  fun daysOutsideWindowBeforeTodayBookedOrBufferAreNotSelectable() {
    val window = oct(1)..oct(20)
    assertFalse(isSelectable(LocalDate.of(2026, 9, 30), window, booked, today))
    assertFalse(isSelectable(oct(21), window, booked, today))
    assertFalse(isSelectable(oct(2), window, booked, today = oct(3)))
    assertFalse(isSelectable(oct(7), window, booked, today))
    assertFalse(isSelectable(oct(9), window, booked, today))
  }

  @Test
  fun dayInsideWindowIsSelectable() {
    assertTrue(isSelectable(oct(5), window, booked, today))
    assertTrue(isSelectable(oct(1), window, booked, today))
    assertTrue(isSelectable(oct(10), window, booked, today))
  }

  @Test
  fun firstTapSetsStart() {
    assertEquals(DateRangeSelection(start = oct(2)), tap(oct(2)))
  }

  @Test
  fun tapBeforeStartMovesStart() {
    assertEquals(DateRangeSelection(start = oct(2)), tap(oct(4), oct(2)))
  }

  @Test
  fun tapAfterFullRangeStartsNewRange() {
    assertEquals(DateRangeSelection(start = oct(3)), tap(oct(1), oct(2), oct(3)))
  }

  @Test
  fun tapOnUnselectableDayChangesNothing() {
    val selection = tap(oct(2))
    assertEquals(selection, selection.onTap(oct(7), window, booked, today, true))
    assertEquals(selection, selection.onTap(oct(9), window, booked, today, true))
    assertEquals(selection, selection.onTap(oct(11), window, booked, today, true))
  }

  @Test
  fun sameDayTwiceGivesOneDayRange() {
    assertEquals(
        DateRangeSelection(start = oct(5), end = oct(5)),
        tap(oct(5), oct(5), disabledDates = emptySet(), requireBufferDay = false),
    )
    assertEquals(DateRangeSelection(start = oct(3), end = oct(3)), tap(oct(3), oct(3)))
  }

  @Test
  fun rangeOverBookedDayRestartsFromTappedDay() {
    val window = oct(1)..oct(20)
    assertEquals(DateRangeSelection(start = oct(12)), tap(oct(4), oct(12), window = window))
  }

  @Test
  fun endBeforeBookedDayShowsBufferError() {
    val selection = tap(oct(4), oct(5))
    assertEquals(DateRangeSelection(start = oct(4), bufferError = true), selection)
    assertFalse(selection.isComplete)
  }

  @Test
  fun endWithFreeNextDayIsValid() {
    val selection = tap(oct(3), oct(4))
    assertEquals(DateRangeSelection(start = oct(3), end = oct(4)), selection)
    assertTrue(selection.isComplete)
  }

  @Test
  fun endOnLastWindowDayShowsBufferError() {
    assertEquals(DateRangeSelection(start = oct(10), bufferError = true), tap(oct(10), oct(10)))
  }

  @Test
  fun bufferErrorClearsOnNextTap() {
    assertEquals(DateRangeSelection(start = oct(4), end = oct(4)), tap(oct(4), oct(5), oct(4)))
  }

  @Test
  fun availabilityModeAllowsEndOnLastWindowDay() {
    assertEquals(
        DateRangeSelection(start = oct(2), end = oct(10)),
        tap(oct(2), oct(10), disabledDates = emptySet(), requireBufferDay = false),
    )
  }

  @Test
  fun summaryCountsDaysAndTotal() {
    val selection = DateRangeSelection(start = oct(15), end = oct(17))
    assertEquals(3, selection.numberOfDays)
    assertEquals(45, selection.totalPrice(15))
  }

  @Test
  fun oneDayRangeIsOneDay() {
    assertEquals(1, DateRangeSelection(start = oct(15), end = oct(15)).numberOfDays)
  }

  @Test
  fun incompleteRangeHasNoDays() {
    assertEquals(0, DateRangeSelection().numberOfDays)
    assertEquals(0, DateRangeSelection(start = oct(15)).totalPrice(15))
  }

  @Test
  fun incompleteRangeNeverExceedsBalance() {
    assertFalse(DateRangeSelection(start = oct(15)).exceedsBalance(pricePerDay = 15, balance = 0))
  }

  @Test
  fun totalAboveBalanceExceedsBalance() {
    val selection = DateRangeSelection(start = oct(15), end = oct(17))
    assertTrue(selection.exceedsBalance(pricePerDay = 15, balance = 44))
  }

  @Test
  fun totalEqualToBalanceDoesNotExceedBalance() {
    val selection = DateRangeSelection(start = oct(15), end = oct(17))
    assertFalse(selection.exceedsBalance(pricePerDay = 15, balance = 45))
  }

  @Test
  fun containsStartAloneOrWholeRange() {
    assertFalse(DateRangeSelection().contains(oct(3)))
    assertTrue(DateRangeSelection(start = oct(3)).contains(oct(3)))
    assertFalse(DateRangeSelection(start = oct(3)).contains(oct(4)))
    val range = DateRangeSelection(start = oct(3), end = oct(5))
    assertTrue(range.contains(oct(4)))
    assertFalse(range.contains(oct(6)))
  }

  @Test
  fun kindOfDescribesEachDay() {
    val window = oct(6)..oct(20)
    val booked = setOf(oct(10), oct(11))
    val selection = DateRangeSelection(start = oct(15), end = oct(17))
    fun kind(day: Int) = selection.kindOf(oct(day), window, booked, today)
    assertEquals(DayKind.UNAVAILABLE, kind(5))
    assertEquals(DayKind.AVAILABLE, kind(6))
    assertEquals(DayKind.BOOKED, kind(10))
    assertEquals(DayKind.BUFFER, kind(12))
    assertEquals(DayKind.SELECTED, kind(16))
  }

  @Test
  fun initialMonthHoldsFirstSelectableDay() {
    val window = LocalDate.of(2026, 10, 25)..LocalDate.of(2026, 12, 31)
    val booked = (25..31).map { oct(it) }.toSet()
    // 1 Nov is the buffer day, so 2 Nov is the first selectable day.
    assertEquals(LocalDate.of(2026, 11, 2), firstSelectableDay(window, booked, today))
    assertEquals(YearMonth.of(2026, 11), initialMonth(window, booked, today))
  }

  @Test
  fun initialMonthFallsBackToWindowStartWhenNothingIsSelectable() {
    val window = oct(6)..oct(7)
    assertNull(firstSelectableDay(window, setOf(oct(6), oct(7)), today))
    assertEquals(YearMonth.of(2026, 10), initialMonth(window, setOf(oct(6), oct(7)), today))
  }

  @Test
  fun bookedDaysListsEveryDayOfEachRange() {
    val ranges = listOf(BookedRange(oct(1), oct(3)), BookedRange(oct(8), oct(8)))
    assertEquals(setOf(oct(1), oct(2), oct(3), oct(8)), bookedDays(ranges))
    assertEquals(emptySet<LocalDate>(), bookedDays(emptyList()))
  }

  // The issue's example: window 1–10, a rental books 1–3, so 4 is the buffer day.
  private fun requestable(start: Int, end: Int, today: LocalDate = this.today) =
      isRequestable(
          oct(start),
          oct(end),
          window,
          bookedDays(listOf(BookedRange(oct(1), oct(3)))),
          today,
      )

  @Test
  fun rangeClearOfBookingsAndLeavingAFreeDayIsRequestable() {
    assertTrue(requestable(5, 9))
    assertTrue(requestable(5, 5))
  }

  @Test
  fun rangeOverlappingABookingOrStartingOnItsBufferDayIsNotRequestable() {
    assertFalse(requestable(1, 4))
    assertFalse(requestable(3, 5))
    assertFalse(requestable(4, 6))
  }

  @Test
  fun rangeEndingOnTheLastDayOfTheWindowIsNotRequestable() {
    assertFalse(requestable(5, 10))
  }

  @Test
  fun rangeEndingRightBeforeABookingIsNotRequestable() {
    // 6–8 is booked, so a range ending on 5 leaves no free day after it.
    assertFalse(isRequestable(oct(2), oct(5), window, booked, today))
    assertTrue(isRequestable(oct(2), oct(4), window, booked, today))
  }

  @Test
  fun rangeEndingBeforeItStartsOrStartingInThePastIsNotRequestable() {
    assertFalse(requestable(8, 6))
    assertFalse(requestable(5, 7, today = oct(6)))
  }
}
