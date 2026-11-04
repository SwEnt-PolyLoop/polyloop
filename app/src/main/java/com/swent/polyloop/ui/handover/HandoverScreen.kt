// Made with Claude.

package com.swent.polyloop.ui.handover

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.polyloop.R
import com.swent.polyloop.resources.C

/** The Handover screen of one rental, backed by [viewModel]. */
@Composable
fun HandoverScreen(viewModel: HandoverViewModel) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  HandoverContent(uiState = uiState, onRetry = viewModel::retry)
}

/**
 * The Handover screen drawn from [uiState]: a spinner while loading, an error with Retry, a missing
 * rental, a rental that takes no photos, or the pickup itself.
 */
@Composable
fun HandoverContent(uiState: HandoverUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
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
          ) {}
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
