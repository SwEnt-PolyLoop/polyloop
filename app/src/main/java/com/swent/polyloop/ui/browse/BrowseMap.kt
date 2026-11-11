// Made with Claude.

package com.swent.polyloop.ui.browse

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.swent.polyloop.R
import com.swent.polyloop.resources.C

/** The centre of the EPFL campus, where the map opens. */
private val EPFL = LatLng(46.5191, 6.5668)

/** Zoom of the map when it opens: the campus and the towns around it. */
private const val MAP_ZOOM = 13f

/**
 * The Browse map, filling the space it is given: an interactive Google map that can be scrolled and
 * zoomed, opening on EPFL. The map toolbar and zoom buttons are hidden, so tapping the map never
 * leaves the app.
 */
@Composable
fun BrowseMap(modifier: Modifier = Modifier) {
  val cameraPositionState = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(EPFL, MAP_ZOOM)
  }
  GoogleMap(
      modifier = modifier.fillMaxSize().testTag(C.Tag.browse_map),
      cameraPositionState = cameraPositionState,
      contentDescription = stringResource(R.string.browse_map_description),
      uiSettings = MapUiSettings(mapToolbarEnabled = false, zoomControlsEnabled = false),
  )
}
