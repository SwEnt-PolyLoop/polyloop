// Made with Claude.

package com.swent.polyloop.ui.handover

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.swent.polyloop.R
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.theme.Border
import com.swent.polyloop.ui.theme.PositiveGreen

private val QR_SIZE = 160.dp

/**
 * The card of the person giving the item (Figma 14, 14.10 to 14.13): the QR code once they have 3
 * photos and the code, with a note while offline; otherwise what is missing. Once the other person
 * has scanned it, only "Picked up". [onRetry] reads the code again after it failed.
 */
@Composable
internal fun ShowCodeCard(uiState: HandoverUiState, onRetry: () -> Unit) {
  Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surface,
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.handover_code_card),
  ) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      if (uiState.isPickedUp) {
        PickedUp(uiState.otherPersonName)
        return@Column
      }
      Text(
          text = showCodeTitle(uiState.isLender, uiState.otherPersonName),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
          modifier = Modifier.testTag(C.Tag.handover_code_title),
      )
      val code = uiState.code
      when {
        uiState.photosNeeded > 0 -> {
          Hint()
          Message(
              pluralStringResource(
                  R.plurals.handover_show_code_photos_missing,
                  uiState.photosNeeded,
                  uiState.photosNeeded,
              ),
              MaterialTheme.colorScheme.error,
          )
        }
        code != null -> {
          Box(
              modifier = Modifier.border(1.dp, Border, RoundedCornerShape(12.dp)).padding(8.dp),
          ) {
            QrCode(
                code = code,
                contentDescription = stringResource(R.string.handover_code_description),
                modifier = Modifier.size(QR_SIZE).testTag(C.Tag.handover_qr_code),
            )
          }
          Hint()
          if (uiState.isOffline) {
            Message(
                offlineNote(uiState.otherPersonName),
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        uiState.codeFailed && uiState.isOffline -> {
          Hint()
          Message(
              stringResource(R.string.handover_code_no_copy),
              MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        uiState.codeFailed -> {
          Message(stringResource(R.string.handover_code_error), MaterialTheme.colorScheme.error)
          TextButton(
              onClick = onRetry,
              modifier = Modifier.testTag(C.Tag.handover_code_retry_button),
          ) {
            Text(stringResource(R.string.handover_retry), fontWeight = FontWeight.Bold)
          }
        }
        // The code is still being read.
        else ->
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp).testTag(C.Tag.handover_code_loading),
                strokeWidth = 2.dp,
            )
      }
    }
  }
}

/**
 * A green check with "Picked up by Mara · the rental has started", seen once the scan went through.
 */
@Composable
private fun PickedUp(name: String) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(
        imageVector = Icons.Default.CheckCircle,
        contentDescription = null,
        tint = PositiveGreen,
        modifier = Modifier.size(20.dp),
    )
    Text(
        text =
            if (name.isBlank()) stringResource(R.string.handover_code_picked_up_no_name)
            else stringResource(R.string.handover_code_picked_up, name),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        color = PositiveGreen,
        modifier = Modifier.testTag(C.Tag.handover_code_picked_up),
    )
  }
}

/** "They scan it to confirm they received the item." */
@Composable
private fun Hint() {
  Text(
      text = stringResource(R.string.handover_show_code_hint),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
  )
}

@Composable
private fun Message(text: String, color: Color) {
  Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = color,
      textAlign = TextAlign.Center,
      modifier = Modifier.testTag(C.Tag.handover_code_message),
  )
}

/** "Show this code to Mara", or to "the borrower" / "the lender" when the name is blank. */
@Composable
private fun showCodeTitle(isLender: Boolean, name: String): String =
    when {
      name.isNotBlank() -> stringResource(R.string.handover_show_code_title, name)
      isLender -> stringResource(R.string.handover_show_code_title_borrower)
      else -> stringResource(R.string.handover_show_code_title_lender)
    }

@Composable
private fun offlineNote(name: String): String =
    if (name.isBlank()) stringResource(R.string.handover_code_offline_note_no_name)
    else stringResource(R.string.handover_code_offline_note, name)
