// Made with Claude.

package com.swent.polyloop.ui.myrentals

import com.swent.polyloop.model.rental.RentalStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MyRentalsFormatTest {

  @Test
  fun everyStatusHasItsOwnLabel() {
    val labels = RentalStatus.entries.map { it.labelRes }

    assertEquals(RentalStatus.entries.size, labels.toSet().size)
  }
}
