// Made with Claude.

package com.swent.polyloop.ui.handover

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.swent.polyloop.R
import com.swent.polyloop.model.handover.HandoverPhase
import com.swent.polyloop.model.handover.HandoverPhoto
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.components.ListingPhoto
import com.swent.polyloop.ui.components.dashedOutline
import com.swent.polyloop.ui.components.formatDay
import com.swent.polyloop.ui.createlisting.CameraPermissionMessage
import com.swent.polyloop.ui.createlisting.PolyLoopCamera
import com.swent.polyloop.ui.theme.PolyLoopTheme
import com.swent.polyloop.ui.theme.PositiveGreen
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The Handover screen of one rental, backed by [viewModel]. An empty tile asks for the camera
 * permission, then opens the in-app camera for one photo, which [viewModel] uploads.
 */
@Composable
fun HandoverScreen(viewModel: HandoverViewModel) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var isCameraOpen by rememberSaveable { mutableStateOf(false) }
  var cameraPermissionGranted by rememberSaveable { mutableStateOf<Boolean?>(null) }
  val cameraPermissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraPermissionGranted = granted
        if (granted) isCameraOpen = true
      }
  HandoverContent(
      uiState = uiState,
      onRetry = viewModel::retry,
      onAddPhoto = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
      onRetryUpload = viewModel::retryUpload,
      isCameraOpen = isCameraOpen,
      cameraPermissionGranted = cameraPermissionGranted,
      onPhotoTaken = { uri ->
        isCameraOpen = false
        viewModel.addPhoto(uri)
      },
      onCloseCamera = { isCameraOpen = false },
      onCaptureFailed = viewModel::onCaptureFailed,
  )
}

/**
 * The Handover screen drawn from [uiState]: a spinner while loading, an error with Retry, a missing
 * rental, a rental that takes no photos, or the pickup itself. [onAddPhoto] is called when an empty
 * photo tile is tapped, [onRetryUpload] when the photo that failed to upload is; photo times are
 * shown in [zone]. While [isCameraOpen], the in-app camera fills the screen for one photo, given to
 * [onPhotoTaken], or to [onCaptureFailed] if the shot fails; [cameraPermissionGranted] false shows
 * how to allow the camera.
 */
@Composable
fun HandoverContent(
    uiState: HandoverUiState,
    onRetry: () -> Unit,
    onAddPhoto: () -> Unit,
    onRetryUpload: () -> Unit,
    modifier: Modifier = Modifier,
    isCameraOpen: Boolean = false,
    cameraPermissionGranted: Boolean? = null,
    onPhotoTaken: (uri: String) -> Unit = {},
    onCloseCamera: () -> Unit = {},
    onCaptureFailed: (uri: String) -> Unit = {},
    zone: ZoneId = ZoneId.systemDefault(),
) {
  // The photo shown full screen after a tap, by id; null when none is.
  var enlargedId by rememberSaveable { mutableStateOf<String?>(null) }
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
          Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
              HandoverHeader(uiState)
              if (!uiState.isPickupDay) NotPickupDayNote(uiState.pickupDate)
              PhotosCard(
                  uiState,
                  zone,
                  onAddPhoto,
                  onRetryUpload,
                  onPhotoClick = { enlargedId = it },
                  onRetryPhotos = onRetry,
              )
              CameraPermissionMessage(cameraPermissionGranted)
            }
            DepositFooter(uiState.deposit)
          }
    }
  }
  uiState.photos
      .find { it.id == enlargedId }
      ?.let { PhotoViewer(it, zone, onClose = { enlargedId = null }) }
  if (isCameraOpen) {
    // Its own window, so the camera covers the whole screen, the top bar too; Back closes it.
    Dialog(
        onDismissRequest = onCloseCamera,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
      PolyLoopCamera(
          photoCount = 0,
          onPhotoTaken = onPhotoTaken,
          onClose = onCloseCamera,
          onCaptureFailed = onCaptureFailed,
          maxPhotos = 1,
          // The photo waiting for a retry is still needed, however old.
          keepPhotos = listOfNotNull(uiState.failedPhotoUri),
      )
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
 * How many dashed camera tiles follow the [shownCount] photos (uploaded, uploading or failed):
 * enough to reach the 3 needed, and at least one while another photo fits. None outside the pickup
 * day, when no photo can be added.
 */
internal fun emptyTileCount(shownCount: Int, isPickupDay: Boolean): Int =
    if (!isPickupDay || shownCount >= HandoverPhoto.MAX_PHOTOS) 0
    else maxOf(HandoverPhoto.MIN_PHOTOS - shownCount, 1)

/**
 * The "Condition photos" card: the user's photos, oldest first, the one uploading or whose upload
 * failed, then the dashed camera tiles, and whether enough photos were taken.
 */
@Composable
private fun PhotosCard(
    uiState: HandoverUiState,
    zone: ZoneId,
    onAddPhoto: () -> Unit,
    onRetryUpload: () -> Unit,
    onPhotoClick: (id: String) -> Unit,
    onRetryPhotos: () -> Unit,
) {
  val photoTiles: List<@Composable () -> Unit> =
      uiState.photos.map { photo ->
        { PhotoTile(photo, zone, onClick = { onPhotoClick(photo.id) }) }
      }
  val pendingTiles: List<@Composable () -> Unit> = buildList {
    if (uiState.isUploading) add { UploadingTile() }
    uiState.failedPhotoUri?.let { add { FailedTile(it, onRetryUpload) } }
  }
  val shownCount = photoTiles.size + pendingTiles.size
  val emptyTiles: List<@Composable () -> Unit> =
      List(emptyTileCount(shownCount, uiState.isPickupDay)) {
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
      if (uiState.photosFailed) {
        Text(
            text = stringResource(R.string.handover_photos_error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(C.Tag.handover_photos_error),
        )
        OutlinedButton(
            onClick = onRetryPhotos,
            modifier = Modifier.testTag(C.Tag.handover_photos_retry_button),
        ) {
          Text(stringResource(R.string.handover_retry))
        }
      } else {
        TileGrid(photoTiles + pendingTiles + emptyTiles)
        if (uiState.isPickupDay) PhotosStatus(uiState.photosNeeded)
      }
    }
  }
}

/** A green check with "Pickup photos done", or a red warning with how many photos are missing. */
@Composable
private fun PhotosStatus(photosNeeded: Int) {
  val done = photosNeeded == 0
  val color = if (done) PositiveGreen else MaterialTheme.colorScheme.error
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Icon(
        imageVector = if (done) Icons.Default.CheckCircle else Icons.Default.Warning,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(18.dp),
    )
    Text(
        text =
            if (done) stringResource(R.string.handover_photos_done)
            else
                pluralStringResource(R.plurals.handover_photos_missing, photosNeeded, photosNeeded),
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = Modifier.testTag(C.Tag.handover_photos_status),
    )
  }
}

/** "Pickup is on 15 Oct": photos can only be taken on that day. */
@Composable
private fun NotPickupDayNote(pickupDate: LocalDate?) {
  Text(
      text =
          stringResource(R.string.handover_not_pickup_day, pickupDate?.let { formatDay(it) } ?: ""),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.testTag(C.Tag.handover_not_pickup_day),
  )
}

/** The tile of the photo being uploaded: a spinner in a dashed tile. */
@Composable
private fun UploadingTile() {
  Box(
      modifier =
          Modifier.fillMaxSize()
              .dashedOutline(MaterialTheme.colorScheme.outline, TILE_CORNER)
              .testTag(C.Tag.handover_uploading),
      contentAlignment = Alignment.Center,
  ) {
    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
  }
}

/** The photo at [uri] whose upload failed, dimmed with a retry icon; tapping it calls [onRetry]. */
@Composable
private fun FailedTile(uri: String, onRetry: () -> Unit) {
  Box(
      modifier =
          Modifier.fillMaxSize()
              .clip(RoundedCornerShape(TILE_CORNER))
              .clickable(onClick = onRetry)
              .testTag(C.Tag.handover_failed_photo),
      contentAlignment = Alignment.Center,
  ) {
    ListingPhoto(url = uri, contentDescription = null, modifier = Modifier.fillMaxSize())
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
    Icon(
        imageVector = Icons.Default.Refresh,
        contentDescription = stringResource(R.string.handover_upload_failed),
        tint = Color.White,
    )
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

/** A photo, cropped to fill its tile, with the time it was taken; tapping it calls [onClick]. */
@Composable
private fun PhotoTile(photo: HandoverPhoto, zone: ZoneId, onClick: () -> Unit) {
  val time = PHOTO_TIME.format(photo.takenAt.atZone(zone))
  Box(
      modifier =
          Modifier.fillMaxSize()
              .clip(RoundedCornerShape(TILE_CORNER))
              .clickable(onClick = onClick)
              .testTag(C.Tag.handover_photo)
  ) {
    ListingPhoto(
        url = photo.url,
        contentDescription = stringResource(R.string.handover_photo_description, time),
        modifier = Modifier.fillMaxSize(),
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

/**
 * [photo] full screen on black, whole (not cropped), with "Pickup · 14:02"; ✕ or Back closes it.
 */
@Composable
private fun PhotoViewer(photo: HandoverPhoto, zone: ZoneId, onClose: () -> Unit) {
  val label =
      stringResource(R.string.handover_photo_label, PHOTO_TIME.format(photo.takenAt.atZone(zone)))
  Dialog(
      onDismissRequest = onClose,
      properties =
          DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
  ) {
    Box(
        modifier =
            Modifier.fillMaxSize().background(Color.Black).testTag(C.Tag.handover_photo_viewer)
    ) {
      AsyncImage(
          model = photo.url,
          contentDescription = label,
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxSize(),
      )
      IconButton(
          onClick = onClose,
          modifier =
              Modifier.align(Alignment.TopEnd)
                  .safeDrawingPadding()
                  .padding(8.dp)
                  .testTag(C.Tag.handover_photo_viewer_close),
      ) {
        Icon(
            Icons.Default.Close,
            contentDescription = stringResource(R.string.handover_close_photo),
            tint = Color.White,
        )
      }
      Text(
          text = label,
          style = MaterialTheme.typography.bodyMedium,
          color = Color.White,
          modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(16.dp),
      )
    }
  }
}

/** "Deposit held" and its amount, pinned under the scrolling content. */
@Composable
private fun DepositFooter(deposit: Int) {
  Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          text = stringResource(R.string.handover_deposit_held),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
      )
      Text(
          text = stringResource(R.string.handover_deposit_amount, deposit),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.testTag(C.Tag.handover_deposit),
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun HandoverPreview() {
  val photos =
      List(2) {
        HandoverPhoto("p$it", HandoverPhase.PICKUP, Instant.parse("2026-10-06T12:02:00Z"), "")
      }
  PolyLoopTheme {
    HandoverContent(
        uiState =
            HandoverUiState(
                isLoading = false,
                itemTitle = "Camping tent, 2 people",
                otherPersonName = "Mara",
                phase = HandoverPhase.PICKUP,
                isPickupDay = true,
                photos = photos,
            ),
        onRetry = {},
        onAddPhoto = {},
        onRetryUpload = {},
    )
  }
}
