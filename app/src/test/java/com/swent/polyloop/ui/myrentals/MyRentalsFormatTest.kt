// Made with Claude.

package com.swent.polyloop.ui.myrentals

import com.swent.polyloop.model.rental.RentalStatus
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class MyRentalsFormatTest {

  @Test
  fun formatDayShowsDayAndShortMonth() {
    assertEquals("22 Oct", formatDay(LocalDate.of(2026, 10, 22), Locale.ENGLISH))
    assertEquals("3 Nov", formatDay(LocalDate.of(2026, 11, 3), Locale.ENGLISH))
  }

  @Test
  fun everyStatusHasItsOwnLabel() {
    val labels = RentalStatus.entries.map { it.labelRes }

    assertEquals(RentalStatus.entries.size, labels.toSet().size)
  }
}
