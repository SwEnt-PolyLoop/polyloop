// Made with Claude.

package com.swent.polyloop.ui.dispute

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisputeSplitEditorTest {

  @Test
  fun parsePercentAcceptsWholeNumbersUpToAHundred() {
    assertEquals(0, parsePercent("0"))
    assertEquals(25, parsePercent("25"))
    assertEquals(100, parsePercent("100"))
    assertEquals(5, parsePercent("05"))
  }

  @Test
  fun parsePercentRefusesAnythingElse() {
    for (text in listOf("", "101", "250", "1000", "-1", "2.5", "1a", " 5")) {
      assertNull(text, parsePercent(text))
    }
  }

  @Test
  fun formatTenthsShowsADecimalOnlyWhenNeeded() {
    assertEquals("18", formatTenths(180))
    assertEquals("16.5", formatTenths(165))
    assertEquals("0.3", formatTenths(3))
  }

  @Test
  fun percentAtRoundsToOnePercentInsideTheInsetBar() {
    // A 220 px bar with 10 px at each end: 200 px from 0% to 100%, so 2 px per percent.
    assertEquals(0, percentAt(10f, 220, 10f))
    assertEquals(50, percentAt(110f, 220, 10f))
    assertEquals(13, percentAt(35f, 220, 10f))
    assertEquals(100, percentAt(210f, 220, 10f))
  }

  @Test
  fun percentAtKeepsTouchesBeyondTheEndsWithinTheBar() {
    assertEquals(0, percentAt(0f, 220, 10f))
    assertEquals(100, percentAt(220f, 220, 10f))
  }

  @Test
  fun percentAtIsZeroWhenTheBarIsNoWiderThanItsInsets() {
    assertEquals(0, percentAt(10f, 20, 10f))
    assertEquals(0, percentAt(5f, 10, 10f))
  }
}
