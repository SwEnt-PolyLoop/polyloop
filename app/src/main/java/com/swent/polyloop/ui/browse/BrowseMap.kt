// Made with Claude.

package com.swent.polyloop.ui.browse

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.swent.polyloop.R
import com.swent.polyloop.resources.C

/** The centre of the EPFL campus, where the map opens. */
private val EPFL = LatLng(46.5191, 6.5668)

/** Zoom of the map when it opens: the campus and the towns around it. */
private const val MAP_ZOOM = 13f

/** Space between a pin's text and its left and right edges. */
private val PIN_PADDING_HORIZONTAL = 10.dp

/** Space between a pin's text and its top and bottom edges. */
private val PIN_PADDING_VERTICAL = 4.dp

/** Rounding of a pin's corners: half its height, so the ends are round. */
private const val PIN_CORNER_PERCENT = 50

/**
 * The Browse map, filling the space it is given: an interactive Google map that can be scrolled and
 * zoomed, opening on EPFL, with one pin per entry of [pins]. The map toolbar and zoom buttons are
 * hidden, so tapping the map never leaves the app. With no pins, the map shows none.
 */
@Composable
fun BrowseMap(pins: List<BrowsePin>, modifier: Modifier = Modifier) {
  val cameraPositionState = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(EPFL, MAP_ZOOM)
  }
  GoogleMap(
      modifier = modifier.fillMaxSize().testTag(C.Tag.browse_map),
      cameraPositionState = cameraPositionState,
      contentDescription = stringResource(R.string.browse_map_description),
      uiSettings = MapUiSettings(mapToolbarEnabled = false, zoomControlsEnabled = false),
  ) {
    // Keyed on the point, so a pin keeps its marker when the filter changes the other pins.
    pins.forEach { pin -> key(pin.point) { BrowsePinMarker(pin) } }
  }
}

/** The marker of [pin] at its point, drawn as [BrowsePinLabel] and read out as its label. */
@Composable
private fun BrowsePinMarker(pin: BrowsePin) {
  val label = pinLabel(pin)
  MarkerComposable(
      // The marker's picture is drawn once, and again only when one of these keys changes.
      pin.point,
      label,
      state = rememberUpdatedMarkerState(LatLng(pin.point.latitude, pin.point.longitude)),
      contentDescription = label,
  ) {
    BrowsePinLabel(label)
  }
}

/**
 * The text of [pin]: the price per day of its listing when it has one, as on the listing cards ("15
 * PP"), or how many listings it holds ("3 items") when several share its point.
 */
@Composable
internal fun pinLabel(pin: BrowsePin): String {
  val count = pin.listings.size
  return if (count == 1) {
    stringResource(R.string.listing_card_price, pin.listings.single().pricePerDay)
  } else {
    pluralStringResource(R.plurals.browse_pin_items, count, count)
  }
}

/** How a pin looks: [label] in bold, in a pill of the app's main colour. */
@Composable
internal fun BrowsePinLabel(label: String, modifier: Modifier = Modifier) {
  Surface(
      shape = RoundedCornerShape(percent = PIN_CORNER_PERCENT),
      color = MaterialTheme.colorScheme.primary,
      contentColor = MaterialTheme.colorScheme.onPrimary,
      modifier = modifier,
  ) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        modifier =
            Modifier.padding(horizontal = PIN_PADDING_HORIZONTAL, vertical = PIN_PADDING_VERTICAL)
                .testTag(C.Tag.browse_pin_label),
    )
  }
}
