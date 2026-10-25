// Made with Claude.

package com.swent.polyloop.ui.components

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * The dates picked so far in the date range picker: nothing, a [start] only, or a full range from
 * [start] to [end]. [bufferError] is true when the last tap was refused because it left no free day
 * after the rental.
 */
data class DateRangeSelection(
    val start: LocalDate? = null,
    val end: LocalDate? = null,
    val bufferError: Boolean = false,
) {
  /** True once both ends are picked. */
  val isComplete: Boolean
    get() = start != null && end != null

  /** Days in the range, counting both the start and the end day. 0 until the range is complete. */
  val numberOfDays: Int
    get() = if (start == null || end == null) 0 else ChronoUnit.DAYS.between(start, end).toInt() + 1

  /** Price per day times [numberOfDays], in PP. */
  fun totalPrice(pricePerDay: Int): Int = pricePerDay * numberOfDays

  /** True once the range is complete and its total is more than [balance]. */
  fun exceedsBalance(pricePerDay: Int, balance: Int): Boolean =
      isComplete && totalPrice(pricePerDay) > balance

  /** True for the picked start alone, or for every day from [start] to [end]. */
  fun contains(day: LocalDate): Boolean =
      when {
        start == null -> false
        end == null -> day == start
        else -> day in start..end
      }
}

/** How one day of the month grid is drawn. */
internal enum class DayKind {
  /** Outside the window or before today: just the number, greyed out. */
  UNAVAILABLE,
  AVAILABLE,
  BOOKED,
  BUFFER,
  /** Part of the user's picked dates. */
  SELECTED,
}

/** The day right after each booked stretch, unless that day is booked too. */
fun bufferDays(disabledDates: Set<LocalDate>): Set<LocalDate> =
    disabledDates.map { it.plusDays(1) }.filterNot { it in disabledDates }.toSet()

/** True for a day inside [window], not before [today], neither booked nor a buffer day. */
fun isSelectable(
    day: LocalDate,
    window: ClosedRange<LocalDate>,
    disabledDates: Set<LocalDate>,
    today: LocalDate,
    buffers: Set<LocalDate> = bufferDays(disabledDates),
): Boolean = day in window && !day.isBefore(today) && day !in disabledDates && day !in buffers

/** How [day] is drawn, given the current [selection]. */
internal fun DateRangeSelection.kindOf(
    day: LocalDate,
    window: ClosedRange<LocalDate>,
    disabledDates: Set<LocalDate>,
    today: LocalDate,
    buffers: Set<LocalDate> = bufferDays(disabledDates),
): DayKind =
    when {
      day in disabledDates -> DayKind.BOOKED
      day in buffers -> DayKind.BUFFER
      !isSelectable(day, window, disabledDates, today, buffers) -> DayKind.UNAVAILABLE
      contains(day) -> DayKind.SELECTED
      else -> DayKind.AVAILABLE
    }

/**
 * The selection after a tap on [day]. Taps on days that are not selectable change nothing. With
 * [requireBufferDay], an end is refused unless the day after it is free and inside [window].
 */
fun DateRangeSelection.onTap(
    day: LocalDate,
    window: ClosedRange<LocalDate>,
    disabledDates: Set<LocalDate>,
    today: LocalDate,
    requireBufferDay: Boolean,
): DateRangeSelection {
  val buffers = bufferDays(disabledDates)
  if (!isSelectable(day, window, disabledDates, today, buffers)) return this
  val start = start
  val crossesBlockedDay =
      start != null && (disabledDates + buffers).any { !it.isBefore(start) && !it.isAfter(day) }
  if (start == null || end != null || day.isBefore(start) || crossesBlockedDay) {
    return DateRangeSelection(start = day)
  }
  val dayAfter = day.plusDays(1)
  if (requireBufferDay && (dayAfter in disabledDates || dayAfter > window.endInclusive)) {
    return DateRangeSelection(start = start, bufferError = true)
  }
  return DateRangeSelection(start = start, end = day)
}

/**
 * True if a borrower could pick [start]..[end] in the request picker: tapping [start] then [end]
 * gives exactly that range. So both days are selectable, [end] is not before [start], no booked or
 * buffer day lies between them, and the day after [end] is free and inside [window].
 */
fun isRequestable(
    start: LocalDate,
    end: LocalDate,
    window: ClosedRange<LocalDate>,
    disabledDates: Set<LocalDate>,
    today: LocalDate,
): Boolean =
    DateRangeSelection()
        .onTap(start, window, disabledDates, today, requireBufferDay = true)
        .onTap(end, window, disabledDates, today, requireBufferDay = true) ==
        DateRangeSelection(start = start, end = end)

/** The first day that can be picked, or null if there is none. */
internal fun firstSelectableDay(
    window: ClosedRange<LocalDate>,
    disabledDates: Set<LocalDate>,
    today: LocalDate,
): LocalDate? {
  val buffers = bufferDays(disabledDates)
  return generateSequence(maxOf(window.start, today)) { it.plusDays(1) }
      .takeWhile { it <= window.endInclusive }
      .firstOrNull { isSelectable(it, window, disabledDates, today, buffers) }
}

/** The month the picker opens on: the one holding the first selectable day, else the window's. */
internal fun initialMonth(
    window: ClosedRange<LocalDate>,
    disabledDates: Set<LocalDate>,
    today: LocalDate,
): YearMonth = YearMonth.from(firstSelectableDay(window, disabledDates, today) ?: window.start)
