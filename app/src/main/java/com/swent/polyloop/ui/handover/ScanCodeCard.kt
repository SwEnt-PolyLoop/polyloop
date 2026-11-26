// Made with Claude.

package com.swent.polyloop.ui.handover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.swent.polyloop.R
import com.swent.polyloop.model.handover.HandoverPhase
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.theme.PositiveGreen

/**
 * The card of the person receiving the item, the borrower at pickup (Figma 15, 15.10 to 15.15) and
 * the lender at return (Figma 17): "Scan QR code", which calls [onScan], and why it is off or what
 * the last scan gave; once the pickup scan is confirmed, only "Picked up". [onRetry] reads the
 * code's fingerprint again after it failed.
 */
@Composable
internal fun ScanCodeCard(uiState: HandoverUiState, onScan: () -> Unit, onRetry: () -> Unit) {
  Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surface,
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.handover_scan_card),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      // Also when the phone learns it from the server, e.g. after the screen was opened again. A
      // confirmed return leaves Handover's phase once the rental is seen Returned, so it never
      // says "Picked up".
      if ((uiState.scanConfirmed && uiState.phase == HandoverPhase.PICKUP) || uiState.isPickedUp) {
        Confirmed()
        return@Column
      }
      Text(
          text = scanTitle(uiState.isLender, uiState.otherPersonName),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.testTag(C.Tag.handover_scan_title),
      )
      Text(
          text =
              phaseString(
                  uiState.phase,
                  R.string.handover_scan_hint,
                  R.string.handover_scan_hint_return,
              ),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      // A confirmed return keeps "Sending…" until the rental is seen Returned and the card goes.
      ScanButton(
          enabled = uiState.canScan,
          isSending = uiState.isSendingScan || uiState.scanConfirmed,
          onClick = onScan,
      )
      ScanMessage(uiState, onRetry)
    }
  }
}

/** "Scan QR code" with its icon, or a spinner with "Sending…" while the scan is sent. */
@Composable
private fun ScanButton(enabled: Boolean, isSending: Boolean, onClick: () -> Unit) {
  Button(
      onClick = onClick,
      enabled = enabled,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth().height(54.dp).testTag(C.Tag.handover_scan_button),
  ) {
    if (isSending) {
      CircularProgressIndicator(
          modifier = Modifier.size(18.dp),
          strokeWidth = 2.dp,
          color = LocalContentColor.current,
      )
    } else {
      Icon(painterResource(R.drawable.ic_qr_scan), contentDescription = null)
    }
    Text(
        text =
            stringResource(
                if (isSending) R.string.handover_scan_sending else R.string.handover_scan_button
            ),
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 10.dp),
    )
  }
}

/**
 * Under the button: what is missing first, then a code that could not be read, a rental that
 * changed, offline, and last the scan's error (a wrong code, a scan not sent or refused, or a
 * scanner that could not open). Nothing when the button can simply be pressed.
 */
@Composable
private fun ColumnScope.ScanMessage(uiState: HandoverUiState, onRetry: () -> Unit) {
  val red = MaterialTheme.colorScheme.error
  val grey = MaterialTheme.colorScheme.onSurfaceVariant
  when {
    uiState.photosNeeded > 0 ->
        Message(
            pluralStringResource(
                R.plurals.handover_scan_photos_missing,
                uiState.photosNeeded,
                uiState.photosNeeded,
            ),
            red,
        )
    uiState.codeFailed && uiState.isOffline ->
        Message(
            phaseString(
                uiState.phase,
                R.string.handover_code_no_copy,
                R.string.handover_code_no_copy_return,
            ),
            grey,
        )
    uiState.codeFailed -> {
      Message(stringResource(R.string.handover_scan_error), red)
      TextButton(
          onClick = onRetry,
          modifier =
              Modifier.align(Alignment.CenterHorizontally)
                  .testTag(C.Tag.handover_scan_retry_button),
      ) {
        Text(stringResource(R.string.handover_retry), fontWeight = FontWeight.Bold)
      }
    }
    uiState.scanError == ScanError.RENTAL_CHANGED ->
        Message(
            phaseString(
                uiState.phase,
                R.string.handover_scan_rental_changed,
                R.string.handover_scan_rental_changed_return,
            ),
            red,
        )
    uiState.isOffline -> Message(stringResource(R.string.handover_scan_offline), grey)
    uiState.scanError == ScanError.WRONG_CODE ->
        Message(wrongCodeMessage(uiState.otherPersonName), red)
    uiState.scanError == ScanError.NOT_SENT ->
        Message(stringResource(R.string.handover_scan_not_sent), red)
    uiState.scanError == ScanError.REFUSED ->
        Message(
            phaseString(
                uiState.phase,
                R.string.handover_scan_refused,
                R.string.handover_scan_refused_return,
            ),
            red,
        )
    uiState.scanError == ScanError.SCANNER_UNAVAILABLE ->
        Message(stringResource(R.string.handover_scan_unavailable), red)
  }
}

/** One centred line of [text] under the button. */
@Composable
private fun Message(text: String, color: Color) {
  Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = color,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.handover_scan_message),
  )
}

/** A green check with "Picked up · the rental has started". */
@Composable
private fun Confirmed() {
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
        text = stringResource(R.string.handover_code_picked_up_no_name),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        color = PositiveGreen,
        modifier = Modifier.testTag(C.Tag.handover_scan_confirmed),
    )
  }
}

/** "Scan Loca's code", or the lender's (at pickup) / the borrower's (at return) without a name. */
@Composable
private fun scanTitle(isLender: Boolean, name: String): String =
    when {
      name.isNotBlank() -> stringResource(R.string.handover_scan_title, name)
      isLender -> stringResource(R.string.handover_scan_title_borrower)
      else -> stringResource(R.string.handover_scan_title_lender)
    }

@Composable
private fun wrongCodeMessage(name: String): String =
    if (name.isBlank()) stringResource(R.string.handover_scan_wrong_code_no_name)
    else stringResource(R.string.handover_scan_wrong_code, name)
