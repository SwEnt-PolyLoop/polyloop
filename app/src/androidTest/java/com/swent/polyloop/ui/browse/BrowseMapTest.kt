// Made with Claude.

package com.swent.polyloop.ui.browse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.MapsInitializer
import com.swent.polyloop.R
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.model.location.MapPoint
import com.swent.polyloop.resources.C
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowseMapTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

  private val ecublens = MapPoint(latitude = 46.52, longitude = 6.57)

  private val lausanne = MapPoint(latitude = 46.51, longitude = 6.63)

  private fun listing(id: String, pricePerDay: Int) =
      Listing(
          id = id,
          ownerId = "owner",
          title = "Item $id",
          description = "Description of $id",
          category = ListingCategory.OTHER,
          photoUrls = emptyList(),
          pricePerDay = pricePerDay,
          itemValue = 100,
          availableFrom = LocalDate.of(2026, 10, 1),
          availableTo = LocalDate.of(2026, 12, 31),
          status = ListingStatus.PUBLISHED,
          pickupPoint = ecublens,
      )

  /**
   * Shows the pin of [listings], all at Ecublens, as the map draws it, selected if [isSelected].
   */
  private fun setPin(listings: List<Listing>, isSelected: Boolean = false) {
    composeTestRule.setContent {
      BrowsePinLabel(pinLabel(BrowsePin(ecublens, listings)), isSelected = isSelected)
    }
  }

  /** What a screen reader says for the pin of [listings], at Ecublens, selected if [isSelected]. */
  private fun descriptionOf(listings: List<Listing>, isSelected: Boolean): String {
    var description = ""
    composeTestRule.setContent {
      description = pinDescription(BrowsePin(ecublens, listings), isSelected)
    }
    composeTestRule.waitForIdle()
    return description
  }

  @Test
  fun selectedPinIsReadOutAsSelected() {
    val price = resources.getString(R.string.listing_card_price, 15)

    assertEquals(
        resources.getString(R.string.browse_pin_selected, price),
        descriptionOf(listOf(listing("tent", 15)), isSelected = true),
    )
  }

  @Test
  fun otherPinsAreReadOutAsTheirLabel() {
    assertEquals(
        resources.getQuantityString(R.plurals.browse_pin_items, 2, 2),
        descriptionOf(listOf(listing("tent", 15), listing("drill", 8)), isSelected = false),
    )
  }

  @Test
  fun selectedPinStillShowsItsLabel() {
    setPin(listOf(listing("tent", 15)), isSelected = true)

    composeTestRule
        .onNodeWithTag(C.Tag.browse_pin_label)
        .assertTextEquals(resources.getString(R.string.listing_card_price, 15))
  }

  @Test
  fun pinOfOneListingShowsItsPricePerDay() {
    setPin(listOf(listing("tent", pricePerDay = 15)))

    composeTestRule
        .onNodeWithTag(C.Tag.browse_pin_label)
        .assertTextEquals(resources.getString(R.string.listing_card_price, 15))
  }

  @Test
  fun pinOfSeveralListingsShowsHowManyItHolds() {
    setPin(listOf(listing("tent", 15), listing("drill", 8), listing("bike", 20)))

    composeTestRule
        .onNodeWithTag(C.Tag.browse_pin_label)
        .assertTextEquals(resources.getQuantityString(R.plurals.browse_pin_items, 3, 3))
  }

  @Test
  fun mapWithPinsIsDisplayed() {
    composeTestRule.setContent {
      BrowseMap(
          pins = listOf(BrowsePin(ecublens, listOf(listing("tent", 15)))),
          selectedPoint = ecublens,
          onPinClick = {},
          onMapClick = {},
      )
    }

    composeTestRule.onNodeWithTag(C.Tag.browse_map).assertIsDisplayed()
  }

  /**
   * Loads the Maps SDK, which builds the camera moves. The map does it when it shows, but the
   * framing tests below build moves without showing one.
   */
  private fun initializeMaps() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.runOnMainSync { MapsInitializer.initialize(instrumentation.targetContext) }
  }

  /** The camera moves made by [framePinsOnce] for [pins], and the flag it returns. */
  private fun frame(
      alreadyFramed: Boolean,
      pins: List<BrowsePin>,
  ): Pair<List<CameraUpdate>, Boolean> {
    val moves = mutableListOf<CameraUpdate>()
    val framed = framePinsOnce(alreadyFramed, pins, FRAMING_PADDING_PX, moves::add)
    return moves to framed
  }

  @Test
  fun framingNoPinLeavesTheCameraOnTheCampus() {
    val (moves, framed) = frame(alreadyFramed = false, pins = emptyList())

    assertTrue(moves.isEmpty())
    assertTrue(framed)
  }

  @Test
  fun framingOnePinMovesTheCameraOnce() {
    initializeMaps()

    val (moves, framed) = frame(false, listOf(BrowsePin(ecublens, listOf(listing("tent", 15)))))

    assertEquals(1, moves.size)
    assertTrue(framed)
  }

  @Test
  fun framingSeveralPinsMovesTheCameraOnce() {
    initializeMaps()
    val pins =
        listOf(
            BrowsePin(ecublens, listOf(listing("tent", 15))),
            BrowsePin(lausanne, listOf(listing("drill", 8))),
        )

    val (moves, framed) = frame(alreadyFramed = false, pins = pins)

    assertEquals(1, moves.size)
    assertTrue(framed)
  }

  @Test
  fun alreadyFramedMapKeepsTheUsersView() {
    val (moves, framed) = frame(true, listOf(BrowsePin(ecublens, listOf(listing("tent", 15)))))

    assertTrue(moves.isEmpty())
    assertTrue(framed)
  }

  private companion object {
    /** Space kept around the pins in the framing tests; any value works there. */
    const val FRAMING_PADDING_PX = 64
  }
}
