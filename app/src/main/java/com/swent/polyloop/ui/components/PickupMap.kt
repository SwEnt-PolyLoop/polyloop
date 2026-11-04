// Made with Claude.

package com.swent.polyloop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.swent.polyloop.R
import com.swent.polyloop.model.location.MapPoint
import com.swent.polyloop.resources.C

/** Diameter of the circle drawn on the placeholder that stands in for the map. */
private val PLACEHOLDER_CIRCLE_SIZE = 88.dp

/** Opacity of the pickup circle's fill, on the map and on the placeholder. */
private const val PICKUP_CIRCLE_FILL_ALPHA = 0.2f

/** Width of the pickup circle's outline, on the map and on the placeholder. */
private val PICKUP_CIRCLE_STROKE_WIDTH = 1.dp

/**
 * Radius of the circle on the map, in meters. `geocodeListingAddresses` rounds the public point to
 * a 0.01° grid, so in Switzerland the exact address is at most about 680 m from it.
 */
private const val PICKUP_CIRCLE_RADIUS_METERS = 700.0

/**
 * Zoom of the map: about 2 km from top to bottom around EPFL at Listing detail's height, so the
 * circle is about as big as the placeholder's and the places around it still show. A whole number,
 * because a lite-mode map may not show fractional zooms.
 */
private const val PICKUP_MAP_ZOOM = 13f

/**
 * Fills its parent with the pickup area, for Listing detail and Rental detail: a small map centered
 * on [point] if [showMap] is true. Without a [point] (the address is not geocoded yet, or its
 * geocoding failed) or when [showMap] is false (offline, since the map tiles need a connection), it
 * shows a placeholder with the [area] name instead.
 */
@Composable
fun PickupMap(area: String, point: MapPoint?, showMap: Boolean) {
  if (point != null && showMap) PickupGoogleMap(point = point) else PickupPlaceholder(area = area)
}

/**
 * A map centered on [point], with a circle over the pickup area. It is a lite-mode map: a still
 * picture that cannot be scrolled or zoomed, so it never takes over the screen's scrolling, and
 * tapping it does nothing instead of opening Google Maps. It shows no exact address.
 */
@Composable
private fun PickupGoogleMap(point: MapPoint) {
  val center = LatLng(point.latitude, point.longitude)
  val circleColor = MaterialTheme.colorScheme.primary
  val strokeWidth = with(LocalDensity.current) { PICKUP_CIRCLE_STROKE_WIDTH.toPx() }
  // Keyed on the point, so a new point moves the camera instead of keeping the saved position.
  key(center) {
    GoogleMap(
        modifier = Modifier.fillMaxSize().testTag(C.Tag.pickup_map),
        cameraPositionState =
            rememberCameraPositionState {
              position = CameraPosition.fromLatLngZoom(center, PICKUP_MAP_ZOOM)
            },
        contentDescription = stringResource(R.string.pickup_map_description),
        googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
        uiSettings = MapUiSettings(mapToolbarEnabled = false, zoomControlsEnabled = false),
        onMapClick = {},
    ) {
      Circle(
          center = center,
          radius = PICKUP_CIRCLE_RADIUS_METERS,
          fillColor = circleColor.copy(alpha = PICKUP_CIRCLE_FILL_ALPHA),
          strokeColor = circleColor,
          strokeWidth = strokeWidth,
      )
    }
  }
}

/**
 * Stands in for the map: a translucent circle with the [area] name over it, or the circle alone
 * when [area] is blank (the address is not geocoded yet).
 */
@Composable
private fun PickupPlaceholder(area: String) {
  val circleColor = MaterialTheme.colorScheme.primary
  Box(
      modifier = Modifier.fillMaxSize().testTag(C.Tag.pickup_map_placeholder),
      contentAlignment = Alignment.Center,
  ) {
    Box(
        modifier =
            Modifier.size(PLACEHOLDER_CIRCLE_SIZE)
                .background(circleColor.copy(alpha = PICKUP_CIRCLE_FILL_ALPHA), CircleShape)
                .border(PICKUP_CIRCLE_STROKE_WIDTH, circleColor, CircleShape)
    )
    if (area.isNotBlank()) {
      Text(
          text = area,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(horizontal = 16.dp).testTag(C.Tag.pickup_map_area_name),
      )
    }
  }
}
