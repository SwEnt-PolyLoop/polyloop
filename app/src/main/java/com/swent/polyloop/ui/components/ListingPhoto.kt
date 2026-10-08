// Made with Claude.

package com.swent.polyloop.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/**
 * A listing photo loaded from its download [url], cropped to fill the space given by [modifier].
 *
 * A grey box is shown instead while the photo loads, when [url] is null (no photo), and when it
 * cannot be loaded (broken link, or offline and not cached yet).
 */
@Composable
fun ListingPhoto(url: String?, contentDescription: String?, modifier: Modifier = Modifier) {
  val grey = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
  AsyncImage(
      model = url,
      contentDescription = contentDescription,
      modifier = modifier,
      placeholder = grey,
      error = grey,
      fallback = grey,
      contentScale = ContentScale.Crop,
  )
}
