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

