// Made with Claude.

package com.swent.polyloop.ui.components

import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Today's date on the phone, by [clock], which the app gives every screen through [LocalToday].
 * [clock] is read each time, so a change of the phone's clock or time zone shows at the next read:
 * on return to the foreground, or at the end of the wait for midnight. Nothing listens for those
 * changes while the app stays in the foreground, so one made then (an automatic time zone change
 * when crossing a border, a large network time correction) can leave the date wrong until the
 * midnight that was being waited for, up to a day later.
 */
class TodayTracker(private val clock: () -> ZonedDateTime = { ZonedDateTime.now() }) {

  private val _today = MutableStateFlow(clock().toLocalDate())

  /** Today's date, as last read. */
  val today: StateFlow<LocalDate> = _today.asStateFlow()

  /** Reads today's date again. */
  fun refresh() {
    _today.value = clock().toLocalDate()
  }

  /**
   * Reads today's date at once, then again at each midnight in the clock's time zone, until
   * cancelled. The wait may stop while the phone sleeps, so the caller starts this again when the
   * app comes back to the foreground (see [ProvideToday]).
   */
  suspend fun followMidnights(): Nothing {
    while (true) {
      refresh()
      delay(untilNextMidnight())
    }
  }

  /**
   * The milliseconds until just after the next midnight, so the clock reads the new day by then. A
   * wait that ends before the clock reaches midnight (it was set back meanwhile) reads the same day
   * again, then waits for the rest.
   */
  private fun untilNextMidnight(): Long {
    val now = clock()
    val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
    return Duration.between(now, midnight).toMillis() + 1
  }
}
