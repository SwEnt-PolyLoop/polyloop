// Made with Claude.

package com.swent.polyloop.ui.listingdetail

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The two ends of a date range, written as briefly as possible: "6" and "20 Oct" in the same month,
 * "28 Oct" and "3 Nov" across months, and with the year when the years differ.
 */
internal fun formatRangeEnds(
    from: LocalDate,
    to: LocalDate,
    locale: Locale = Locale.getDefault(),
): Pair<String, String> {
  val dayMonth = DateTimeFormatter.ofPattern("d MMM", locale)
  return when {
    from.year != to.year -> {
      val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
      from.format(dayMonthYear) to to.format(dayMonthYear)
    }
    from.month != to.month -> from.format(dayMonth) to to.format(dayMonth)
    else -> from.dayOfMonth.toString() to to.format(dayMonth)
  }
}
