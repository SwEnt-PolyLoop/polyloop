// Made with Claude.

package com.swent.polyloop.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.model.location.MapPoint
import com.swent.polyloop.resources.C
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** When [PickupMap] shows the map and when its placeholder. Each screen tests its own wiring. */
@RunWith(AndroidJUnit4::class)
class PickupMapTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val pointNearEpfl = MapPoint(latitude = 46.52, longitude = 6.57)

  private fun setContent(point: MapPoint?, showMap: Boolean, area: String = "Ecublens") {
    composeTestRule.setContent { PickupMap(area = area, point = point, showMap = showMap) }
  }

  /** Checks that the placeholder shows, with the name Ecublens, and that the map does not. */
  private fun assertPlaceholderShownWithTheAreaName() {
    composeTestRule.onNodeWithTag(C.Tag.pickup_map_placeholder).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.pickup_map).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.pickup_map_area_name).assertTextEquals("Ecublens")
  }

  @Test
  fun showsTheMapOnlineWithAPoint() {
    setContent(point = pointNearEpfl, showMap = true)

    composeTestRule.onNodeWithTag(C.Tag.pickup_map).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.pickup_map_placeholder).assertDoesNotExist()
  }

  @Test
  fun showsThePlaceholderWithTheAreaNameWithoutAPoint() {
    setContent(point = null, showMap = true)

    assertPlaceholderShownWithTheAreaName()
  }

  @Test
  fun showsThePlaceholderWithTheAreaNameOffline() {
    setContent(point = pointNearEpfl, showMap = false)

    assertPlaceholderShownWithTheAreaName()
  }

  @Test
  fun showsNoAreaNameBeforeGeocoding() {
    setContent(point = null, showMap = true, area = "")

    composeTestRule.onNodeWithTag(C.Tag.pickup_map_placeholder).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.pickup_map_area_name).assertDoesNotExist()
  }
}
