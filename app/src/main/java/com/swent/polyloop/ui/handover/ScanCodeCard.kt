// Made with Claude.

package com.swent.polyloop.ui.handover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swent.polyloop.R
import com.swent.polyloop.resources.C

/**
 * The card of the person receiving the item (Figma 15 and 15.10): "Scan QR code", which calls
 * [onScan] once it can be pressed.
 */
@Composable
internal fun ScanCodeCard(uiState: HandoverUiState, onScan: () -> Unit) {
  Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surface,
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.handover_scan_card),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(
          text = scanTitle(uiState.isLender, uiState.otherPersonName),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.testTag(C.Tag.handover_scan_title),
      )
      Text(
          text = stringResource(R.string.handover_scan_hint),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      ScanButton(enabled = uiState.canScan, onClick = onScan)
    }
  }
}

/** "Scan QR code" with its icon. */
@Composable
private fun ScanButton(enabled: Boolean, onClick: () -> Unit) {
  Button(
      onClick = onClick,
      enabled = enabled,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth().height(54.dp).testTag(C.Tag.handover_scan_button),
  ) {
    Icon(painterResource(R.drawable.ic_qr_scan), contentDescription = null)
    Text(
        text = stringResource(R.string.handover_scan_button),
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 10.dp),
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
