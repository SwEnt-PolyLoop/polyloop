// Made with Claude.

package com.swent.polyloop.ui.handover

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.swent.polyloop.R
import com.swent.polyloop.model.handover.HandoverPhase
import com.swent.polyloop.model.handover.HandoverPhoto
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.theme.PolyLoopTheme
import com.swent.polyloop.ui.theme.PositiveGreen
import java.time.Instant
import java.time.ZoneId

// Before and after photos are larger than the condition photos: 3 per row, as in Figma 17b.
private const val CHECK_PHOTOS_PER_ROW = 3

/**
 * The lender's check of the returned item (Figma 17b): the header, that the return was scanned, and
 * their pickup and return photos side by side, over the deposit footer. Tapping a photo gives its
 * id to [onPhotoClick]; [onRetryPhotos] loads the photos again after they failed to load.
 */
@Composable
internal fun ReturnCheckContent(
    uiState: HandoverUiState,
    zone: ZoneId,
    onPhotoClick: (id: String) -> Unit,
    onRetryPhotos: () -> Unit,
) {
  ReturnedScaffold(uiState, Modifier.testTag(C.Tag.handover_return_check)) {
    ReturnScannedCard()
    BeforeAfterCard(uiState, zone, onPhotoClick, onRetryPhotos)
  }
}

/**
 * What the borrower sees once the return is scanned: the header, then that the lender checks the
 * item against the pickup photos, and the deposit footer.
 */
@Composable
internal fun WaitingForCheckContent(uiState: HandoverUiState) {
  ReturnedScaffold(uiState) {
    HandoverCard {
      Text(
          text =
              if (uiState.otherPersonName.isBlank()) {
                stringResource(R.string.handover_waiting_check_no_name)
              } else {
                stringResource(R.string.handover_waiting_check, uiState.otherPersonName)
              },
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.testTag(C.Tag.handover_waiting_check),
      )
    }
  }
}

/** The header and [content] scrolling over the deposit footer pinned at the bottom. */
@Composable
private fun ReturnedScaffold(
    uiState: HandoverUiState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
  Column(modifier = modifier.fillMaxSize()) {
    Column(
        modifier =
            Modifier.weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(dimensionResource(R.dimen.handover_check_padding)),
        verticalArrangement =
            Arrangement.spacedBy(dimensionResource(R.dimen.handover_check_spacing)),
    ) {
      HandoverHeader(uiState)
      content()
    }
    DepositFooter(uiState.deposit, uiState.depositHeld, uiState.isLender)
  }
}

/**
 * A green check with "Return scanned". The time of the scan is not shown until rentals store it.
 */
@Composable
private fun ReturnScannedCard() {
  HandoverCard {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(dimensionResource(R.dimen.handover_check_icon_spacing)),
    ) {
      Surface(
          shape = CircleShape,
          color = PositiveGreen.copy(alpha = 0.12f),
          contentColor = PositiveGreen,
          modifier = Modifier.size(dimensionResource(R.dimen.handover_check_icon_size)),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(Icons.Default.Check, contentDescription = null)
        }
      }
      Text(
          text = stringResource(R.string.handover_return_scanned),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.testTag(C.Tag.handover_return_scanned),
      )
    }
  }
}

/**
 * "Before and after": the lender's pickup photos, then their return photos, 3 per row; a spinner
 * until both are read, or an error with Retry when they could not be loaded.
 */
@Composable
private fun BeforeAfterCard(
    uiState: HandoverUiState,
    zone: ZoneId,
    onPhotoClick: (id: String) -> Unit,
    onRetryPhotos: () -> Unit,
) {
  HandoverCard {
    Text(
        text = stringResource(R.string.handover_before_after),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    when {
      uiState.photosFailed -> PhotosLoadError(onRetryPhotos)
      // "No photos" before they are read would tell the lender they took none.
      uiState.isLoadingPhotos ->
          CircularProgressIndicator(
              modifier =
                  Modifier.align(Alignment.CenterHorizontally)
                      .testTag(C.Tag.handover_check_photos_loading)
          )
      else -> {
        PhotoSection(
            R.string.handover_pickup_title,
            uiState.pickupPhotos,
            zone,
            onPhotoClick,
            C.Tag.handover_before_photo,
            C.Tag.handover_no_before_photos,
        )
        PhotoSection(
            R.string.handover_return_title,
            uiState.photos,
            zone,
            onPhotoClick,
            C.Tag.handover_after_photo,
            C.Tag.handover_no_after_photos,
        )
      }
    }
  }
}

/**
 * The [label] "Pickup" or "Return", in capitals, then [photos] tagged [photoTag], or "No photos"
 * tagged [emptyTag] when there are none.
 */
@Composable
private fun PhotoSection(
    @StringRes label: Int,
    photos: List<HandoverPhoto>,
    zone: ZoneId,
    onPhotoClick: (id: String) -> Unit,
    photoTag: String,
    emptyTag: String,
) {
  Text(
      text = stringResource(label).uppercase(),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  if (photos.isEmpty()) {
    Text(
        text = stringResource(R.string.handover_no_photos),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(emptyTag),
    )
  } else {
    TileGrid(
        photos.map { photo ->
          { PhotoTile(photo, zone, onClick = { onPhotoClick(photo.id) }, tag = photoTag) }
        },
        perRow = CHECK_PHOTOS_PER_ROW,
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun ReturnCheckPreview() {
  fun photos(phase: HandoverPhase) =
      List(3) { HandoverPhoto("$phase$it", phase, Instant.parse("2026-10-06T12:02:00Z"), "") }
  PolyLoopTheme {
    HandoverContent(
        uiState =
            HandoverUiState(
                isLoading = false,
                isLender = true,
                itemTitle = "Camping tent, 2 people",
                otherPersonName = "Borrower",
                isReturned = true,
                deposit = 150,
                photos = photos(HandoverPhase.RETURN),
                pickupPhotos = photos(HandoverPhase.PICKUP),
            ),
        onRetry = {},
        onAddPhoto = {},
        onRetryUpload = {},
    )
  }
}
