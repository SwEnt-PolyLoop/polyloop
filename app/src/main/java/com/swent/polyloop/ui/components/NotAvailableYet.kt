// Made with Claude.

package com.swent.polyloop.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.swent.polyloop.R

/**
 * "Not available yet", small and grey, shown next to a button that does nothing yet, so that a
 * tester knows the feature is planned, not broken. Remove it in the PR that makes the button work.
 */
@Composable
fun NotAvailableYet(testTag: String, modifier: Modifier = Modifier) {
  Text(
      text = stringResource(R.string.not_available_yet),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = modifier.testTag(testTag),
  )
}
