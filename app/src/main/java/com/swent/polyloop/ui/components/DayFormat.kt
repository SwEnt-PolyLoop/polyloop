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
