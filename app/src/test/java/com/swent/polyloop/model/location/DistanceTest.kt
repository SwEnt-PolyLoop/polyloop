// Made with Claude.

package com.swent.polyloop.model.location

import java.util.Locale
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
  fun distanceBetweenEpflAndLausanneStationIsAboutThreeKilometers() {
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

  private fun format(meters: Double, locale: Locale = Locale.US) = formatDistance(meters, locale)

  @Test
  fun metersUnderOneKilometerRoundToTheNearestTen() {
    assertEquals("0 m", format(0.0))
    assertEquals("0 m", format(-0.0))
    assertEquals("0 m", format(4.0))
    assertEquals("10 m", format(5.0))
    assertEquals("350 m", format(350.0))
    assertEquals("350 m", format(354.0))
    assertEquals("360 m", format(355.0))
    assertEquals("990 m", format(994.0))
  }

  @Test
  fun aDistanceRoundingToOneKilometerIsInKilometers() {
    assertEquals("1.0 km", format(995.0))
    assertEquals("1.0 km", format(999.9))
    assertEquals("1.0 km", format(1000.0))
  }

  @Test
  fun kilometersHaveOneDecimalRoundedHalfUp() {
    assertEquals("1.0 km", format(1049.9))
    assertEquals("1.1 km", format(1050.0))
    assertEquals("1.1 km", format(1149.9))
    assertEquals("1.2 km", format(1150.0))
    assertEquals("12.4 km", format(12_350.0))
  }

  @Test
  fun largeDistancesHaveNoThousandsSeparator() {
    assertEquals("1234.5 km", format(1_234_500.0))
  }

  @Test
  fun kilometersUseTheLocaleDecimalSeparator() {
    assertEquals("350 m", format(350.0, Locale.FRANCE))
    assertEquals("1,2 km", format(1200.0, Locale.FRANCE))
    assertEquals("1.2 km", format(1200.0, Locale.US))
  }

  @Test
  fun noDistanceToShowGivesNull() {
    assertNull(format(Double.NaN))
    assertNull(format(Double.POSITIVE_INFINITY))
    assertNull(format(Double.NEGATIVE_INFINITY))
    assertNull(format(-1.0))
  }

  @Test
  fun defaultLocaleIsTheDevicesOne() {
    val original = Locale.getDefault()
    try {
      Locale.setDefault(Locale.FRANCE)
      assertEquals("1,2 km", formatDistance(1200.0))
    } finally {
      Locale.setDefault(original)
    }
  }
}
