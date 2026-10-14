// Made with Claude.

package com.swent.polyloop.ui.createlisting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swent.polyloop.R
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.components.ListingPhoto
import com.swent.polyloop.ui.theme.PolyLoopTheme

private val PHOTO_TILE_SIZE = 88.dp
private val PHOTO_TILE_CORNER = 12.dp

/**
 * The photo row with a remove button on each photo, the "Add photo" tile while more photos fit, and
 * the number of photos still needed to publish.
 */
@Composable
internal fun PhotosCard(
    photos: List<String>,
    canAddPhoto: Boolean,
    missingPhotos: Int,
    onAddPhoto: () -> Unit,
    onRemovePhoto: (index: Int) -> Unit,
) {
  OutlinedCard(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.create_listing_photos),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text =
                stringResource(
                    R.string.create_listing_photo_count,
                    photos.size,
                    Listing.MAX_PHOTOS,
                    Listing.MIN_PHOTOS,
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(C.Tag.create_listing_photos_counter),
        )
      }
      Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        photos.forEachIndexed { index, uri ->
          PhotoTile(uri = uri, index = index, onRemove = { onRemovePhoto(index) })
        }
        if (canAddPhoto) {
          AddPhotoTile(onClick = onAddPhoto)
        }
      }
      if (missingPhotos > 0) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
              painter = painterResource(R.drawable.ic_error_outline),
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(16.dp),
          )
          Text(
              text =
                  pluralStringResource(
                      R.plurals.create_listing_missing_photos,
                      missingPhotos,
                      missingPhotos,
                  ),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.testTag(C.Tag.create_listing_photos_hint),
          )
        }
      }
    }
  }
}

/** A square photo with a small remove button in its corner. */
@Composable
private fun PhotoTile(uri: String, index: Int, onRemove: () -> Unit) {
  Box(modifier = Modifier.size(PHOTO_TILE_SIZE).clip(RoundedCornerShape(PHOTO_TILE_CORNER))) {
    ListingPhoto(
        url = uri,
        contentDescription = stringResource(R.string.create_listing_photo_description, index + 1),
        modifier = Modifier.fillMaxSize().testTag(C.Tag.create_listing_photo_ + index),
    )
    Surface(
        onClick = onRemove,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        modifier =
            Modifier.align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .testTag(C.Tag.create_listing_remove_photo_ + index),
    ) {
      Icon(
          Icons.Default.Close,
          contentDescription = stringResource(R.string.create_listing_remove_photo, index + 1),
          modifier = Modifier.padding(4.dp),
      )
    }
  }
}

/** The dashed "Add photo" tile, which will open the in-app camera. */
@Composable
private fun AddPhotoTile(onClick: () -> Unit) {
  val borderColor = MaterialTheme.colorScheme.outline
  val contentColor = MaterialTheme.colorScheme.primary
  Column(
      modifier =
          Modifier.size(PHOTO_TILE_SIZE)
              .clip(RoundedCornerShape(PHOTO_TILE_CORNER))
              .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius(PHOTO_TILE_CORNER.toPx()),
                    style =
                        Stroke(
                            width = 1.dp.toPx(),
                            pathEffect =
                                PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                        ),
                )
              }
              .clickable(onClick = onClick)
              .testTag(C.Tag.create_listing_add_photo),
      verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(
        painter = painterResource(R.drawable.ic_camera),
        contentDescription = null,
        tint = contentColor,
    )
    Text(
        text = stringResource(R.string.create_listing_add_photo),
        style = MaterialTheme.typography.labelMedium,
        color = contentColor,
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun PhotosCardPreview() {
  val uiState = CreateListingUiState(photos = List(2) { "" })
  PolyLoopTheme {
    PhotosCard(
        photos = uiState.photos,
        canAddPhoto = uiState.canAddPhoto,
        missingPhotos = uiState.missingPhotos,
        onAddPhoto = {},
        onRemovePhoto = {},
    )
  }
}
