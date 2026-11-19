// Made with Claude.

package com.swent.polyloop.model.location

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

/** A distance rounded for display, with the unit to show. */
sealed interface RoundedDistance {
  /** Whole meters, a multiple of 10, under 1000. */
  data class Meters(val meters: Int) : RoundedDistance

  /** Kilometers with one decimal, from 1.0. */
  data class Kilometers(val kilometers: Double) : RoundedDistance
}

/**
 * [meters] rounded for display, or null if it is NaN, infinite or negative (no distance to show).
 * - Under 1000 m: [RoundedDistance.Meters], to the nearest 10 m (0 up to 4 m).
 * - From 1000 m: [RoundedDistance.Kilometers], with one decimal.
 *
 * A distance that rounds to 1000 m or more is in km, so 995 m is 1.0 km, never 1000 m. The text
 * comes from strings.xml, so that it follows the language.
 */
fun formatDistance(meters: Double): RoundedDistance? {
  if (!meters.isFinite() || meters < 0) return null
  // Half up with integer results, so the boundaries (5 m, 50 m) do not depend on how a double
  // prints: x.5 is exact in binary, so it always rounds up.
  val roundedMeters = floor(meters / 10 + 0.5).toLong() * 10
  if (roundedMeters < 1000) return RoundedDistance.Meters(roundedMeters.toInt())
  val tenthsOfKm = floor(meters / 100 + 0.5).toLong()
  return RoundedDistance.Kilometers(tenthsOfKm / 10.0)
}
