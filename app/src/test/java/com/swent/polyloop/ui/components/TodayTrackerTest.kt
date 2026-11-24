// Made with Claude.

package com.swent.polyloop.ui.components

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodayTrackerTest {

  private val zurich = ZoneId.of("Europe/Zurich")

  private fun oct(day: Int) = LocalDate.of(2026, 10, day)

  private fun minutes(n: Long) = Duration.ofMinutes(n).toMillis()

  /**
   * The phone's clock: it starts at [start], written in Zurich time, and moves with the test's
   * virtual time. A test moves it by hand with [shift], or changes its time [zone].
   */
  private class FakeClock(start: String, zone: ZoneId, private val scope: TestScope) {
    private val origin = LocalDateTime.parse(start).atZone(zone).toInstant()
    var shift: Duration = Duration.ZERO
    var zone: ZoneId = zone

    fun now(): ZonedDateTime =
        origin.plusMillis(scope.testScheduler.currentTime).plus(shift).atZone(zone)
  }

  private fun TestScope.clockAt(start: String) = FakeClock(start, zurich, this)

  /** Starts following the midnights, as the app does each time it comes to the foreground. */
  private fun TestScope.follow(tracker: TodayTracker) {
    backgroundScope.launch { tracker.followMidnights() }
    runCurrent()
  }

  @Test
  fun startsWithTheClocksDate() = runTest {
    val clock = clockAt("2026-10-13T15:00")

    assertEquals(oct(13), TodayTracker(clock::now).today.value)
  }

  @Test
  fun movesToTheNextDayAtMidnight() = runTest {
    val tracker = TodayTracker(clockAt("2026-10-13T23:00")::now)
    follow(tracker)

    advanceTimeBy(minutes(59))
    assertEquals(oct(13), tracker.today.value)

    advanceTimeBy(minutes(1) + 1)
    runCurrent()
    assertEquals(oct(14), tracker.today.value)
  }

  @Test
  fun keepsMovingOnAtEachMidnight() = runTest {
    val tracker = TodayTracker(clockAt("2026-10-13T23:00")::now)
    follow(tracker)

    advanceTimeBy(minutes(61))
    advanceTimeBy(Duration.ofDays(1).toMillis())
    runCurrent()

    assertEquals(oct(15), tracker.today.value)
  }

  @Test
  fun waitsForMidnightOnADayShortenedBySummerTime() = runTest {
    // In Zurich, 29 March 2026 has 23 hours: the clocks go from 02:00 to 03:00.
    val tracker = TodayTracker(clockAt("2026-03-29T00:30")::now)
    follow(tracker)

    advanceTimeBy(minutes(22 * 60 + 29))
    assertEquals(LocalDate.of(2026, 3, 29), tracker.today.value)

    advanceTimeBy(minutes(2))
    assertEquals(LocalDate.of(2026, 3, 30), tracker.today.value)
  }

  @Test
  fun aWaitThatEndsBeforeMidnightWaitsForTheRest() = runTest {
    val clock = clockAt("2026-10-13T23:00")
    val tracker = TodayTracker(clock::now)
    follow(tracker)

    // The phone's clock is set back an hour while the tracker waits for midnight.
    advanceTimeBy(minutes(30))
    clock.shift = Duration.ofHours(-1)
    advanceTimeBy(minutes(31))
    assertEquals(oct(13), tracker.today.value)

    advanceTimeBy(minutes(60))
    assertEquals(oct(14), tracker.today.value)
  }

  @Test
  fun refreshReadsTheClockAgain() = runTest {
    val clock = clockAt("2026-10-13T15:00")
    val tracker = TodayTracker(clock::now)

    // E.g. the phone slept for two days, or its date was changed, while the app was hidden.
    clock.shift = Duration.ofDays(2)
    assertEquals(oct(13), tracker.today.value)

    tracker.refresh()
    assertEquals(oct(15), tracker.today.value)
  }

  @Test
  fun refreshFollowsAChangeOfTimeZone() = runTest {
    val clock = clockAt("2026-10-13T15:00")
    val tracker = TodayTracker(clock::now)

    // 15:00 in Zurich is already 03:00 the next day at UTC+14.
    clock.zone = ZoneId.of("Pacific/Kiritimati")
    tracker.refresh()

    assertEquals(oct(14), tracker.today.value)
  }

  @Test
  fun followingMidnightsReadsTheDateAtOnce() = runTest {
    val clock = clockAt("2026-10-13T15:00")
    val tracker = TodayTracker(clock::now)

    // As when the app comes back to the foreground three days later.
    clock.shift = Duration.ofDays(3)
    follow(tracker)

    assertEquals(oct(16), tracker.today.value)
  }
}
