// Made with Claude.

package com.swent.polyloop.ui.browse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.swent.polyloop.R
import com.swent.polyloop.model.location.MapPoint
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

/** Width of the border around a pin, which sets a white pin apart from light parts of the map. */
private val PIN_BORDER_WIDTH = 1.dp

/** Drawing order of the selected pin: above the others (0 by default), so they never hide it. */
private const val SELECTED_PIN_Z_INDEX = 1f

/** Space kept between the outermost pins and the edges of the map when it fits them all. */
private val FRAMING_PADDING = 64.dp

/** What the map frames when it opens, depending on the pins. */
internal sealed interface MapFraming {
  /** No pin: the map stays on EPFL. */
  data object Campus : MapFraming

  /** One pin: the map centres on its [point], at the usual zoom. */
  data class OnePin(val point: MapPoint) : MapFraming

  /** Several pins: the map fits the box from [southWest] to [northEast], which holds them all. */
  data class AllPins(val southWest: MapPoint, val northEast: MapPoint) : MapFraming
}

/**
 * What the map frames for [pins]: EPFL when there are none, the pin itself when there is one, and
 * the smallest box holding them all when there are several.
 */
internal fun framingOf(pins: List<BrowsePin>): MapFraming =
    when (pins.size) {
      0 -> MapFraming.Campus
      1 -> MapFraming.OnePin(pins.single().point)
      else ->
          MapFraming.AllPins(
              southWest =
                  MapPoint(pins.minOf { it.point.latitude }, pins.minOf { it.point.longitude }),
              northEast =
                  MapPoint(pins.maxOf { it.point.latitude }, pins.maxOf { it.point.longitude }),
          )
    }

/**
 * The Browse map, filling the space it is given: an interactive Google map that can be scrolled and
 * zoomed, with one pin per entry of [pins]. Once loaded, it frames the pins (see [framingOf]), once
 * per opening of the map: changing the filter afterwards keeps the user's view, and so does coming
 * back from a listing or rotating the phone. The map toolbar and zoom buttons are hidden, so
 * tapping the map never leaves the app. Tapping a pin calls [onPinClick] with its point, and the
 * map centres on it; tapping the map anywhere else calls [onMapClick]. The pin at [selectedPoint],
 * if any, is highlighted (see [BrowsePinLabel]).
 */
@Composable
fun BrowseMap(
    pins: List<BrowsePin>,
    selectedPoint: MapPoint?,
    onPinClick: (MapPoint) -> Unit,
    onMapClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val cameraPositionState = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(EPFL, MAP_ZOOM)
  }
  // Saved with the camera, so the restored view is not replaced after a rotation or a Back.
  var hasFramedPins by rememberSaveable { mutableStateOf(false) }
  // The map calls onMapLoaded later; this gives it the pins of that moment.
  val currentPins by rememberUpdatedState(pins)
  val framingPaddingPx = with(LocalDensity.current) { FRAMING_PADDING.roundToPx() }
  GoogleMap(
      modifier = modifier.fillMaxSize().testTag(C.Tag.browse_map),
      cameraPositionState = cameraPositionState,
      contentDescription = stringResource(R.string.browse_map_description),
      uiSettings = MapUiSettings(mapToolbarEnabled = false, zoomControlsEnabled = false),
      onMapClick = { onMapClick() },
      // Fitting a box needs the map's size on screen, which is known once the map has loaded.
      onMapLoaded = {
        hasFramedPins =
            framePinsOnce(hasFramedPins, currentPins, framingPaddingPx, cameraPositionState::move)
      },
  ) {
    // Keyed on the point, so a pin keeps its marker when the filter changes the other pins.
    pins.forEach { pin ->
      key(pin.point) {
        BrowsePinMarker(
            pin = pin,
            isSelected = pin.point == selectedPoint,
            onClick = { onPinClick(pin.point) },
        )
      }
    }
  }
}

/**
 * Frames [pins] with [move] (see [framingOf]), keeping [paddingPx] pixels between the pins and the
 * edges, unless [alreadyFramed]: the map frames them only once per opening. Returns true, the new
 * value of the "already framed" flag.
 */
internal fun framePinsOnce(
    alreadyFramed: Boolean,
    pins: List<BrowsePin>,
    paddingPx: Int,
    move: (CameraUpdate) -> Unit,
): Boolean {
  if (!alreadyFramed) cameraUpdateFor(framingOf(pins), paddingPx)?.let(move)
  return true
}

/**
 * The camera move that shows [framing], keeping [paddingPx] pixels between the pins and the edges,
 * or null for [MapFraming.Campus], where the map already opens.
 */
internal fun cameraUpdateFor(framing: MapFraming, paddingPx: Int): CameraUpdate? =
    when (framing) {
      MapFraming.Campus -> null
      is MapFraming.OnePin -> CameraUpdateFactory.newLatLngZoom(framing.point.toLatLng(), MAP_ZOOM)
      is MapFraming.AllPins ->
          CameraUpdateFactory.newLatLngBounds(
              LatLngBounds(framing.southWest.toLatLng(), framing.northEast.toLatLng()),
              paddingPx,
          )
    }

/** This point as the Maps SDK's own type. */
private fun MapPoint.toLatLng() = LatLng(latitude, longitude)

/**
 * The marker of [pin] at its point, drawn as [BrowsePinLabel], highlighted and above the other pins
 * when [isSelected], and read out as its label. Tapping it calls [onClick].
 */
@Composable
private fun BrowsePinMarker(pin: BrowsePin, isSelected: Boolean, onClick: () -> Unit) {
  val label = pinLabel(pin)
  MarkerComposable(
      // The marker's picture is drawn once, and again only when one of these keys changes.
      pin.point,
      label,
      isSelected,
      state = rememberUpdatedMarkerState(pin.point.toLatLng()),
      contentDescription = label,
      zIndex = if (isSelected) SELECTED_PIN_Z_INDEX else 0f,
      onClick = {
        onClick()
        // Not consumed, so the map still does what it does on a tap: it centres on the pin.
        false
      },
  ) {
    BrowsePinLabel(label, isSelected)
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

/**
 * How a pin looks: [label] in bold, in a pill with a thin border. The selected pin ([isSelected])
 * is in the app's main colour and the others are white, so the selected one stands out.
 */
@Composable
internal fun BrowsePinLabel(label: String, isSelected: Boolean, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  Surface(
      shape = RoundedCornerShape(percent = PIN_CORNER_PERCENT),
      color = if (isSelected) colors.primary else colors.surface,
      contentColor = if (isSelected) colors.onPrimary else colors.onSurface,
      border = BorderStroke(PIN_BORDER_WIDTH, colors.outlineVariant),
      modifier = modifier,
  ) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        modifier =
            Modifier.padding(horizontal = PIN_PADDING_HORIZONTAL, vertical = PIN_PADDING_VERTICAL)
                .testTag(C.Tag.browse_pin_label)
                .semantics { selected = isSelected },
    )
  }
}
