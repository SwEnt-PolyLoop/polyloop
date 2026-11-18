// Made with Claude.

package com.swent.polyloop.model.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DistanceTest {

  // ---------- distanceMeters ----------

  @Test
  fun samePointIsZeroMetersAway() {
    assertEquals(0.0, distanceMeters(MapPoint(46.52, 6.57), MapPoint(46.52, 6.57))!!, 0.0)
  }

  @Test
  fun distanceBetweenEpflAndLausanneStationIsAboutFiveKilometers() {
    val epfl = MapPoint(46.5191, 6.5668)
    val station = MapPoint(46.5167, 6.6296)

    assertEquals(4_850.0, distanceMeters(epfl, station)!!, 100.0)
  }

  @Test
  fun distanceBetweenZurichAndGenevaIsAboutTwoHundredAndTwentyFourKilometers() {
    val zurich = MapPoint(47.3769, 8.5417)
    val geneva = MapPoint(46.2044, 6.1432)

    assertEquals(224_000.0, distanceMeters(zurich, geneva)!!, 2_000.0)
  }

  @Test
  fun distanceIsTheSameBothWays() {
    val a = MapPoint(46.52, 6.57)
    val b = MapPoint(-33.86, 151.21)

    assertEquals(distanceMeters(a, b)!!, distanceMeters(b, a)!!, 1e-6)
  }

  @Test
  fun negativeCoordinatesAreValid() {
    // One degree of latitude south of the equator and back is about 111 km.
    assertEquals(111_195.0, distanceMeters(MapPoint(0.0, 0.0), MapPoint(-1.0, 0.0))!!, 10.0)
  }

  @Test
  fun antipodalPointsDoNotGiveNaN() {
    val distance = distanceMeters(MapPoint(0.0, 0.0), MapPoint(0.0, 180.0))

    assertNotNull(distance)
    assertFalse(distance!!.isNaN())
    assertEquals(20_015_000.0, distance, 10_000.0)
  }

  @Test
  fun invalidCoordinatesGiveNoDistance() {
    val ok = MapPoint(46.52, 6.57)

    assertNull(distanceMeters(ok, MapPoint(Double.NaN, 6.57)))
    assertNull(distanceMeters(ok, MapPoint(46.52, Double.POSITIVE_INFINITY)))
    assertNull(distanceMeters(MapPoint(91.0, 6.57), ok))
    assertNull(distanceMeters(ok, MapPoint(46.52, -180.5)))
  }

  // ---------- formatDistance ----------

  private fun meters(value: Int) = RoundedDistance.Meters(value)

  private fun km(value: Double) = RoundedDistance.Kilometers(value)

  @Test
  fun metersUnderOneKilometerRoundToTheNearestTen() {
    assertEquals(meters(0), formatDistance(0.0))
    assertEquals(meters(0), formatDistance(-0.0))
    assertEquals(meters(0), formatDistance(4.0))
    assertEquals(meters(10), formatDistance(5.0))
    assertEquals(meters(350), formatDistance(350.0))
    assertEquals(meters(350), formatDistance(354.0))
    assertEquals(meters(360), formatDistance(355.0))
    assertEquals(meters(990), formatDistance(994.0))
  }

  @Test
  fun aDistanceRoundingToOneKilometerIsInKilometers() {
    assertEquals(km(1.0), formatDistance(995.0))
    assertEquals(km(1.0), formatDistance(999.9))
    assertEquals(km(1.0), formatDistance(1000.0))
  }

  @Test
  fun kilometersHaveOneDecimalRoundedHalfUp() {
    assertEquals(km(1.0), formatDistance(1049.9))
    assertEquals(km(1.1), formatDistance(1050.0))
    assertEquals(km(1.1), formatDistance(1149.9))
    assertEquals(km(1.2), formatDistance(1150.0))
    assertEquals(km(12.4), formatDistance(12_350.0))
  }

  @Test
  fun largeDistancesAreKeptWhole() {
    assertEquals(km(1234.5), formatDistance(1_234_500.0))
  }

  @Test
  fun noDistanceToShowGivesNull() {
    assertNull(formatDistance(Double.NaN))
    assertNull(formatDistance(Double.POSITIVE_INFINITY))
    assertNull(formatDistance(Double.NEGATIVE_INFINITY))
    assertNull(formatDistance(-1.0))
  }
}
