// Made with Claude.

package com.swent.polyloop.ui.listingdetail

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class DateRangeFormatTest {

  private fun ends(from: LocalDate, to: LocalDate) = formatRangeEnds(from, to, Locale.ENGLISH)

  @Test
  fun sameMonthShowsMonthOnlyOnEnd() {
    assertEquals(
        "6" to "20 Oct",
        ends(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 20)),
    )
  }

  @Test
  fun differentMonthsShowBothMonths() {
    assertEquals(
        "28 Oct" to "3 Nov",
        ends(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 3)),
    )
  }

  @Test
  fun differentYearsShowBothYears() {
    assertEquals(
        "20 Dec 2026" to "5 Jan 2027",
        ends(LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 5)),
    )
  }

  @Test
  fun sameMonthInDifferentYearsShowsBothYears() {
    assertEquals(
        "6 Oct 2026" to "20 Oct 2027",
        ends(LocalDate.of(2026, 10, 6), LocalDate.of(2027, 10, 20)),
    )
  }
}
