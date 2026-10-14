// Made with Claude.

package com.swent.polyloop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swent.polyloop.R
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.theme.PolyLoopTheme

/**
 * The banner a screen shows at its top while the phone is offline. Each screen shows it from its
 * own UI state, which its ViewModel reads from ConnectivityRepository.
 */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
  val contentColor = MaterialTheme.colorScheme.onTertiaryContainer
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .background(MaterialTheme.colorScheme.tertiaryContainer)
              .padding(horizontal = 16.dp, vertical = 12.dp)
              .testTag(C.Tag.offline_banner),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    // Decorative: the text already says the phone is offline.
    Icon(
        painter = painterResource(R.drawable.ic_wifi_off),
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(18.dp),
    )
    Text(
        text = stringResource(R.string.offline_banner),
        style = MaterialTheme.typography.bodyMedium,
        color = contentColor,
    )
  }
}

@Preview
@Composable
private fun OfflineBannerPreview() {
  PolyLoopTheme { OfflineBanner() }
}
