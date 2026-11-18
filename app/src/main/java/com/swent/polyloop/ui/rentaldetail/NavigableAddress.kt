// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.swent.polyloop.R
import java.net.URLEncoder

/**
 * The Google Maps link that gives directions to [address] from where the phone is. It opens Google
 * Maps, or the browser on a phone without it, and needs no API key.
 */
internal fun directionsUrl(address: String): String =
    // URLEncoder writes a space as "+", the form encoding; "%20" is right in any part of a URL. The
    // Charset overload needs API 33, so the charset is given by name.
    "https://www.google.com/maps/dir/?api=1&destination=" +
        URLEncoder.encode(address, "UTF-8").replace("+", "%20")

/**
 * An exact meeting point: a pin, [address] on the left (tagged [addressTag], wrapping when long),
 * and a "Navigate" button on the right that opens the directions to it ([directionsUrl]). A blank
 * [address] shows "Address not known yet" and no button. The button stays enabled offline: the
 * navigation app may have the map downloaded. If the link cannot be opened (no app on the phone can
 * open it, the app chosen for it refuses to be opened, or opening it fails in any other way), an
 * error shows under the row until the next tap or another [address]. The button is tagged
 * [navigateTag] and the error [errorTag], so that a screen can show several meeting points.
 */
@Composable
internal fun NavigableAddress(
    address: String,
    addressTag: String,
    navigateTag: String,
    errorTag: String,
) {
  val uriHandler = LocalUriHandler.current
  // Keyed on the address, so an error about the old address goes away when it changes.
  var openFailed by remember(address) { mutableStateOf(false) }
  Column {
    Row(verticalAlignment = Alignment.CenterVertically) {
      PinnedText(
          text = address.ifBlank { stringResource(R.string.rental_detail_address_placeholder) },
          tag = addressTag,
          // Takes the room the button leaves, so the button stays at the right edge.
          modifier = Modifier.weight(1f),
      )
      if (address.isNotBlank()) {
        Spacer(Modifier.width(dimensionResource(R.dimen.navigable_address_spacing)))
        NavigateButton(
            tag = navigateTag,
            onClick = {
              openFailed = false
              try {
                uriHandler.openUri(directionsUrl(address))
              } catch (e: RuntimeException) {
                // The platform's handler throws IllegalArgumentException when no app can open the
                // link, and SecurityException when the app chosen does not let others open it; any
                // other failure to open it shows the same error rather than crashing the screen.
                openFailed = true
              }
            },
        )
      }
    }
    if (openFailed) {
      Text(
          text = stringResource(R.string.rental_detail_navigate_failed),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.testTag(errorTag),
      )
    }
  }
}

/**
 * A pin before [text] (tagged [tag]), wrapping when long: the line of an exact meeting point and of
 * the public pickup area on Rental detail.
 */
@Composable
internal fun PinnedText(text: String, tag: String, modifier: Modifier = Modifier) {
  Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
    Icon(
        Icons.Outlined.Place,
        contentDescription = null,
        modifier = Modifier.size(dimensionResource(R.dimen.navigable_address_pin_size)),
    )
    Spacer(Modifier.width(dimensionResource(R.dimen.navigable_address_spacing)))
    Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(tag))
  }
}

/** The small "Navigate" button, tagged [tag], with the directions icon before its text. */
@Composable
private fun NavigateButton(tag: String, onClick: () -> Unit) {
  OutlinedButton(
      onClick = onClick,
      contentPadding =
          PaddingValues(
              horizontal = dimensionResource(R.dimen.navigate_button_padding_horizontal),
              vertical = dimensionResource(R.dimen.navigate_button_padding_vertical),
          ),
      modifier = Modifier.testTag(tag),
  ) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(dimensionResource(R.dimen.navigate_button_icon_spacing)),
    ) {
      Icon(
          painterResource(R.drawable.ic_directions),
          contentDescription = null,
          modifier = Modifier.size(dimensionResource(R.dimen.navigate_button_icon_size)),
      )
      Text(stringResource(R.string.rental_detail_navigate))
    }
  }
}
