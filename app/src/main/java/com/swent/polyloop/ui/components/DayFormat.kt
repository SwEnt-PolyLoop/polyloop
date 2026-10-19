// Made with Claude.

package com.swent.polyloop.ui.components

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A day written briefly: "22 Oct", or "22 Oct 2027" with [withYear]. */
internal fun formatDay(
    day: LocalDate,
    withYear: Boolean = false,
    locale: Locale = Locale.getDefault(),
): String = day.format(DateTimeFormatter.ofPattern(if (withYear) "d MMM yyyy" else "d MMM", locale))

/**
 * The days from [start] to [end], both included, written briefly: "15 – 17 Oct" within one month,
 * "30 Oct – 2 Nov" across months, and "15 Oct" for a single day. A year is shown only when it is
 * not the year of [today], once at the end when both days share it: "15 – 17 Oct 2027", "30 Dec – 2
 * Jan 2027".
 */
internal fun formatDayRange(
    start: LocalDate,
    end: LocalDate,
    today: LocalDate,
    locale: Locale = Locale.getDefault(),
): String {
  val endText = formatDay(end, withYear = end.year != today.year, locale)
  return when {
    start == end -> endText
    start.year == end.year && start.month == end.month -> "${start.dayOfMonth} – $endText"
    else -> {
      val startWithYear = start.year != end.year && start.year != today.year
      "${formatDay(start, startWithYear, locale)} – $endText"
    }
  }
}
