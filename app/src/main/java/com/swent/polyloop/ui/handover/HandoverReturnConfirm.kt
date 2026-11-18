// Made with Claude.

package com.swent.polyloop.ui.handover

import androidx.annotation.StringRes
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
 * The lender's check of the returned item (Figma 17b): the return was scanned, and their pickup and
 * return photos side by side. Tapping a photo gives its id to [onPhotoClick]; [onRetryPhotos] loads
 * the photos again after they failed to load.
 */
@Composable
internal fun ReturnCheckContent(
    uiState: HandoverUiState,
    zone: ZoneId,
    onPhotoClick: (id: String) -> Unit,
    onRetryPhotos: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize().testTag(C.Tag.handover_return_check)) {
    Column(
        modifier =
            Modifier.weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(dimensionResource(R.dimen.handover_check_padding)),
        verticalArrangement =
            Arrangement.spacedBy(dimensionResource(R.dimen.handover_check_spacing)),
    ) {
      HandoverHeader(uiState)
      ReturnScannedCard()
      BeforeAfterCard(uiState, zone, onPhotoClick, onRetryPhotos)
    }
    DepositFooter(uiState.deposit, uiState.depositHeld, isLender = true)
  }
}

/** A white card with rounded corners holding [content] in a column. */
@Composable
private fun CheckCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Surface(
      shape = RoundedCornerShape(dimensionResource(R.dimen.handover_check_card_corner)),
      color = MaterialTheme.colorScheme.surface,
      modifier = modifier.fillMaxWidth(),
  ) {
    Column(
        modifier = Modifier.padding(dimensionResource(R.dimen.handover_check_card_padding)),
        verticalArrangement =
            Arrangement.spacedBy(dimensionResource(R.dimen.handover_check_card_spacing)),
    ) {
      content()
    }
  }
}

/**
 * A green check with "Return scanned" and "The deposit stays held until you decide.". The time of
 * the scan is not shown until rentals store it.
 */
@Composable
private fun ReturnScannedCard() {
  CheckCard {
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
      Column {
        Text(
            text = stringResource(R.string.handover_return_scanned),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(C.Tag.handover_return_scanned),
        )
        Text(
            text = stringResource(R.string.handover_deposit_stays_held),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

/**
 * "Before and after": the lender's pickup photos, then their return photos, 3 per row, or an error
 * with Retry when they could not be loaded.
 */
@Composable
private fun BeforeAfterCard(
    uiState: HandoverUiState,
    zone: ZoneId,
    onPhotoClick: (id: String) -> Unit,
    onRetryPhotos: () -> Unit,
) {
  CheckCard {
    Text(
        text = stringResource(R.string.handover_before_after),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
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

/**
 * What the borrower sees once the return is scanned: the lender, [lenderName], checks the item
 * before the deposit is released.
 */
@Composable
internal fun WaitingForCheck(lenderName: String, modifier: Modifier = Modifier) {
  Text(
      text =
          if (lenderName.isBlank()) stringResource(R.string.handover_waiting_check_no_name)
          else stringResource(R.string.handover_waiting_check, lenderName),
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
      modifier =
          modifier
              .padding(dimensionResource(R.dimen.handover_check_padding))
              .testTag(C.Tag.handover_waiting_check),
  )
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
                otherPersonName = "Loca",
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
