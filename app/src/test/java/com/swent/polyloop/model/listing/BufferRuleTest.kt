// Made with Claude.

package com.swent.polyloop.model.listing

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BufferRuleTest {

  private fun day(d: Int) = LocalDate.of(2026, 11, d)

  private val booked = BookedRange(day(10), day(12))

  @Test
  fun overlappingStaysConflict() {
    assertTrue(conflictsWithBuffer(booked, BookedRange(day(11), day(15))))
    assertTrue(conflictsWithBuffer(booked, BookedRange(day(11), day(11))))
  }

  @Test
  fun staysTouchingThroughABufferDayConflict() {
    assertTrue(conflictsWithBuffer(booked, BookedRange(day(13), day(15))))
    assertTrue(conflictsWithBuffer(booked, BookedRange(day(7), day(9))))
  }

  @Test
  fun oneFreeDayBetweenStaysDoesNotConflict() {
    assertFalse(conflictsWithBuffer(booked, BookedRange(day(14), day(15))))
    assertFalse(conflictsWithBuffer(booked, BookedRange(day(6), day(8))))
  }

  @Test
  fun conflictIsSymmetric() {
    val other = BookedRange(day(13), day(15))
    assertEquals(conflictsWithBuffer(booked, other), conflictsWithBuffer(other, booked))
  }

  @Test
  fun bufferDaysAreTheDaysAroundTheBookedDaysNotBookedThemselves() {
    val buffers = bufferDays(bookedDays(listOf(booked)))

    assertEquals(setOf(day(9), day(13)), buffers)
  }
}
