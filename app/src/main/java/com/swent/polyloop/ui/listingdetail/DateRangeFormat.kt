// Made with Claude.

package com.swent.polyloop.ui.listingdetail

import com.swent.polyloop.ui.components.formatDay
import java.time.LocalDate
import java.util.Locale

/**
 * The two ends of a date range, written as briefly as possible: "6" and "20 Oct" in the same month,
 * "28 Oct" and "3 Nov" across months. The year is shown only when it is not the current year
 * ([today]), and then once, at the end, when both dates share it: "2 – 6 Oct 2028", "28 Oct – 3 Nov
 * 2028". Dates in two different years show the year on both ends.
 */
internal fun formatRangeEnds(
    from: LocalDate,
    to: LocalDate,
    locale: Locale = Locale.getDefault(),
    today: LocalDate = LocalDate.now(),
): Pair<String, String> {
  if (from.year != to.year) {
    return formatDay(from, withYear = true, locale) to formatDay(to, withYear = true, locale)
  }
  val withYear = from.year != today.year
  return when {
    from.month != to.month -> formatDay(from, locale = locale) to formatDay(to, withYear, locale)
    else -> from.dayOfMonth.toString() to formatDay(to, withYear, locale)
  }
}
