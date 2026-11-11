// Made with Claude.

package com.swent.polyloop.ui.browse

import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.model.location.MapPoint
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MapFramingTest {

  private val listing =
      Listing(
          id = "tent",
          ownerId = "owner",
          title = "Camping tent",
          description = "Light 2-person tent",
          category = ListingCategory.SPORTS_OUTDOOR,
          photoUrls = emptyList(),
          pricePerDay = 15,
          itemValue = 180,
          availableFrom = LocalDate.of(2026, 10, 1),
          availableTo = LocalDate.of(2026, 12, 31),
          status = ListingStatus.PUBLISHED,
      )

  private fun pin(latitude: Double, longitude: Double) =
      BrowsePin(MapPoint(latitude, longitude), listOf(listing))

  @Test fun noPinKeepsTheMapOnTheCampus() = assertEquals(MapFraming.Campus, framingOf(emptyList()))

  @Test
  fun onePinCentresTheMapOnIt() =
      assertEquals(MapFraming.OnePin(MapPoint(46.54, 6.59)), framingOf(listOf(pin(46.54, 6.59))))

  @Test
  fun severalPinsFitTheSmallestBoxHoldingThemAll() {
    // Ecublens, Renens and Lausanne: the box takes each side from a different pin.
    val pins = listOf(pin(46.52, 6.57), pin(46.54, 6.59), pin(46.51, 6.63))

    assertEquals(
        MapFraming.AllPins(southWest = MapPoint(46.51, 6.57), northEast = MapPoint(46.54, 6.63)),
        framingOf(pins),
    )
  }
}
