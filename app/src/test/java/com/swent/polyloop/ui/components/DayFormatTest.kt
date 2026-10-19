// Made with Claude.

package com.swent.polyloop.ui.components

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class DayFormatTest {

  @Test
  fun formatDayShowsDayAndShortMonth() {
    assertEquals("22 Oct", formatDay(LocalDate.of(2026, 10, 22), locale = Locale.ENGLISH))
    assertEquals("3 Nov", formatDay(LocalDate.of(2026, 11, 3), locale = Locale.ENGLISH))
  }

  @Test
  fun formatDayAddsTheYearWhenAsked() {
    assertEquals(
        "22 Oct 2027",
        formatDay(LocalDate.of(2027, 10, 22), withYear = true, locale = Locale.ENGLISH),
    )
  }

  private val today = LocalDate.of(2026, 10, 19)

  private fun range(start: LocalDate, end: LocalDate) =
      formatDayRange(start, end, today, Locale.ENGLISH)

  @Test
  fun formatDayRangeWithinOneMonthNamesTheMonthOnce() {
    assertEquals("15 – 17 Oct", range(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 17)))
  }

  @Test
  fun formatDayRangeAcrossMonthsNamesBothMonths() {
    assertEquals("30 Oct – 2 Nov", range(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 2)))
  }

  @Test
  fun formatDayRangeAcrossYearsShowsOnlyTheYearsThatAreNotThisYear() {
    assertEquals(
        "30 Dec – 2 Jan 2027",
        range(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 2)),
    )
    assertEquals(
        "30 Dec 2025 – 2 Jan",
        range(LocalDate.of(2025, 12, 30), LocalDate.of(2026, 1, 2)),
    )
  }

  @Test
  fun formatDayRangeInAnotherYearShowsTheYearOnceAtTheEnd() {
    assertEquals("15 – 17 Oct 2027", range(LocalDate.of(2027, 10, 15), LocalDate.of(2027, 10, 17)))
    assertEquals(
        "30 Oct – 2 Nov 2027",
        range(LocalDate.of(2027, 10, 30), LocalDate.of(2027, 11, 2)),
    )
  }

  @Test
  fun formatDayRangeOfOneDayShowsThatDay() {
    assertEquals("15 Oct", range(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 15)))
  }
}
