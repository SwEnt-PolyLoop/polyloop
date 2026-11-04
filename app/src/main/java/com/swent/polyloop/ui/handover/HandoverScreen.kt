// Made with Claude.

package com.swent.polyloop.ui.handover

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.polyloop.R
import com.swent.polyloop.model.handover.HandoverPhoto
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.components.ListingPhoto
import com.swent.polyloop.ui.components.dashedOutline
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The Handover screen of one rental, backed by [viewModel]. */
@Composable
fun HandoverScreen(viewModel: HandoverViewModel) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  HandoverContent(uiState = uiState, onRetry = viewModel::retry, onAddPhoto = {})
}

/**
 * The Handover screen drawn from [uiState]: a spinner while loading, an error with Retry, a missing
 * rental, a rental that takes no photos, or the pickup itself. [onAddPhoto] is called when an empty
 * photo tile is tapped; photo times are shown in [zone].
 */
@Composable
fun HandoverContent(
    uiState: HandoverUiState,
    onRetry: () -> Unit,
    onAddPhoto: () -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
  Box(
      modifier =
          modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .testTag(C.Tag.handover_screen)
  ) {
    when {
      uiState.isLoading ->
          CircularProgressIndicator(
              modifier = Modifier.align(Alignment.Center).testTag(C.Tag.handover_loading)
          )
      uiState.loadFailed ->
          Column(
              modifier = Modifier.align(Alignment.Center).padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            CenteredMessage(R.string.handover_error, C.Tag.handover_error)
            Button(onClick = onRetry, modifier = Modifier.testTag(C.Tag.handover_retry_button)) {
              Text(stringResource(R.string.handover_retry))
            }
          }
      uiState.notFound ->
          CenteredMessage(
              R.string.handover_not_found,
              C.Tag.handover_not_found,
              Modifier.align(Alignment.Center),
          )
      uiState.phase == null ->
          CenteredMessage(
              R.string.handover_nothing,
              C.Tag.handover_nothing,
              Modifier.align(Alignment.Center),
          )
      else ->
          Column(
              modifier =
                  Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            HandoverHeader(uiState)
            PhotosCard(uiState, zone, onAddPhoto)
          }
    }
  }
}

@Composable
private fun CenteredMessage(@StringRes text: Int, tag: String, modifier: Modifier = Modifier) {
  Text(
      text = stringResource(text),
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
      modifier = modifier.padding(16.dp).testTag(tag),
  )
}

/** "Pickup" with the role pill on the same row, then the item and the other person. */
@Composable
private fun HandoverHeader(uiState: HandoverUiState) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
          text = stringResource(R.string.handover_pickup_title),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.testTag(C.Tag.handover_title),
      )
      RolePill(isLender = uiState.isLender)
    }
    Text(
        text = uiState.itemTitle,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.testTag(C.Tag.handover_item),
    )
    Text(
        text = otherPersonLine(uiState.isLender, uiState.otherPersonName),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(C.Tag.handover_other_person),
    )
  }
}

/** "You lend" or "You borrow", in red on a light red pill. */
@Composable
private fun RolePill(isLender: Boolean) {
  val red = MaterialTheme.colorScheme.primary
  Surface(
      shape = CircleShape,
      color = red.copy(alpha = 0.12f),
      contentColor = red,
  ) {
    Text(
        text =
            stringResource(
                if (isLender) R.string.handover_you_lend else R.string.handover_you_borrow
            ),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier =
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp).testTag(C.Tag.handover_role_pill),
    )
  }
}

/** "with Loca" for the lender, "from Mara" for the borrower, or a placeholder for a blank name. */
@Composable
private fun otherPersonLine(isLender: Boolean, name: String): String =
    when {
      name.isBlank() && isLender -> stringResource(R.string.handover_with_the_borrower)
      name.isBlank() -> stringResource(R.string.handover_from_the_lender)
      isLender -> stringResource(R.string.handover_with, name)
      else -> stringResource(R.string.handover_from, name)
    }

private val TILE_CORNER = 12.dp
private const val TILES_PER_ROW = 4
private val PHOTO_TIME = DateTimeFormatter.ofPattern("HH:mm")

/**
 * How many dashed camera tiles follow the photos: enough to reach the 3 needed, and at least one
 * while another photo fits. None outside the pickup day, when no photo can be added.
 */
internal fun emptyTileCount(photoCount: Int, isPickupDay: Boolean): Int =
    if (!isPickupDay || photoCount >= HandoverPhoto.MAX_PHOTOS) 0
    else maxOf(HandoverPhoto.MIN_PHOTOS - photoCount, 1)

/** The "Condition photos" card: the user's photos, oldest first, then the dashed camera tiles. */
@Composable
private fun PhotosCard(uiState: HandoverUiState, zone: ZoneId, onAddPhoto: () -> Unit) {
  val photoTiles: List<@Composable () -> Unit> =
      uiState.photos.map { photo -> { PhotoTile(photo, zone) } }
  val emptyTiles: List<@Composable () -> Unit> =
      List(emptyTileCount(uiState.photos.size, uiState.isPickupDay)) {
        { EmptyTile(enabled = uiState.canAddPhoto, onClick = onAddPhoto) }
      }
  Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surface,
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.handover_photos_card),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            text = stringResource(R.string.handover_photos_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text =
                stringResource(
                    R.string.handover_photos_range,
                    HandoverPhoto.MIN_PHOTOS,
                    HandoverPhoto.MAX_PHOTOS,
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(C.Tag.handover_photos_range),
        )
      }
      TileGrid(photoTiles + emptyTiles)
    }
  }
}

/** [tiles] as square tiles, [TILES_PER_ROW] per row; a short last row keeps the same tile size. */
@Composable
private fun TileGrid(tiles: List<@Composable () -> Unit>) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    tiles.chunked(TILES_PER_ROW).forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        row.forEach { tile -> Box(modifier = Modifier.weight(1f).aspectRatio(1f)) { tile() } }
        repeat(TILES_PER_ROW - row.size) { Spacer(modifier = Modifier.weight(1f)) }
      }
    }
  }
}

/** A photo, cropped to fill its tile, with the time it was taken. */
@Composable
private fun PhotoTile(photo: HandoverPhoto, zone: ZoneId) {
  val time = PHOTO_TIME.format(photo.takenAt.atZone(zone))
  Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(TILE_CORNER))) {
    ListingPhoto(
        url = photo.url,
        contentDescription = stringResource(R.string.handover_photo_description, time),
        modifier = Modifier.fillMaxSize().testTag(C.Tag.handover_photo),
    )
    Text(
        text = time,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier =
            Modifier.align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
  }
}

/** A dashed tile with a camera icon, which opens the in-app camera when [enabled]. */
@Composable
private fun EmptyTile(enabled: Boolean, onClick: () -> Unit) {
  Box(
      modifier =
          Modifier.fillMaxSize()
              .clip(RoundedCornerShape(TILE_CORNER))
              .dashedOutline(MaterialTheme.colorScheme.outline, TILE_CORNER)
              .clickable(enabled = enabled, onClick = onClick)
              .testTag(C.Tag.handover_add_photo),
      contentAlignment = Alignment.Center,
  ) {
    Icon(
        painter = painterResource(R.drawable.ic_camera),
        contentDescription = stringResource(R.string.handover_take_photo),
        tint = MaterialTheme.colorScheme.primary,
    )
  }
}
