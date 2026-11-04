// Made with Claude.

package com.swent.polyloop.ui.handover

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
          ) {
            HandoverHeader(uiState)
          }
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
