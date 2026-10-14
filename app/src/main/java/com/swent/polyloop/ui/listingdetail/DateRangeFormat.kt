// Made with Claude.

package com.swent.polyloop.ui.listingdetail

import com.swent.polyloop.ui.components.formatDay
import java.time.LocalDate
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
  return when {
    from.year != to.year ->
        formatDay(from, withYear = true, locale) to formatDay(to, withYear = true, locale)
    from.month != to.month -> formatDay(from, locale = locale) to formatDay(to, locale = locale)
    else -> from.dayOfMonth.toString() to formatDay(to, locale = locale)
  }
}
