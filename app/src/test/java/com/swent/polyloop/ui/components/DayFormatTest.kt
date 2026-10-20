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
}
