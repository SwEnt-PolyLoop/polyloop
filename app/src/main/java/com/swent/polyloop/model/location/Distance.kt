// Made with Claude.

package com.swent.polyloop.model.location

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Mean Earth radius, in meters. */
private const val EARTH_RADIUS_METERS = 6_371_000.0

/**
 * The straight-line (great-circle, haversine) distance in meters between [from] and [to], or null
 * if a coordinate is NaN, infinite or out of range (latitude -90..90, longitude -180..180).
 * Negative coordinates are valid (south, west). The same point gives 0.0.
 */
fun distanceMeters(from: MapPoint, to: MapPoint): Double? {
  if (!from.isValid() || !to.isValid()) return null
  val lat1 = Math.toRadians(from.latitude)
  val lat2 = Math.toRadians(to.latitude)
  val dLat = lat2 - lat1
  val dLon = Math.toRadians(to.longitude - from.longitude)
  // A rounding error can push a past 1 (antipodal points); clamp so sqrt never gets a NaN.
  val a = (sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)).coerceIn(0.0, 1.0)
  return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
}

private fun MapPoint.isValid(): Boolean =
    latitude.isFinite() &&
        longitude.isFinite() &&
        latitude in -90.0..90.0 &&
        longitude in -180.0..180.0

/**
 * [meters] as text, or null if it is NaN, infinite or negative (no distance to show).
 * - Under 1000 m: whole meters rounded to the nearest 10 m, "350 m" ("0 m" up to 4 m).
 * - From 1000 m: kilometers with one decimal, "1.2 km", with [locale]'s decimal separator and no
 *   thousands separator.
 *
 * A distance that rounds to 1000 m or more is in km, so 995 m is "1.0 km", never "1000 m".
 */
fun formatDistance(meters: Double, locale: Locale = Locale.getDefault()): String? {
  if (!meters.isFinite() || meters < 0) return null
  // Half up with integer results, so the boundaries (5 m, 50 m) do not depend on how a double
  // prints: x.5 is exact in binary, so it always rounds up.
  val roundedMeters = floor(meters / 10 + 0.5).toLong() * 10
  if (roundedMeters < 1000) return "$roundedMeters m"
  val tenthsOfKm = floor(meters / 100 + 0.5).toLong()
  val format =
      NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = false
        minimumFractionDigits = 1
        maximumFractionDigits = 1
      }
  return "${format.format(tenthsOfKm / 10.0)} km"
}
