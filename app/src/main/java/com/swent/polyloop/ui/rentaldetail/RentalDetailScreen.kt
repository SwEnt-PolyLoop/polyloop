// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.polyloop.R
import com.swent.polyloop.resources.C
import java.time.LocalDate
import java.time.ZoneId

/**
 * The Rental detail screen, showing the rental loaded by [viewModel]. [onChatClick] opens the
 * rental's chat and [onPersonClick] the other person's profile. The app's top bar is drawn by the
 * navigation scaffold, not here.
 */
@Composable
fun RentalDetailScreen(
    viewModel: RentalDetailViewModel,
    onChatClick: () -> Unit,
    onPersonClick: (uid: String) -> Unit,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  // Split from the stateless content below so that tests and previews can set the state directly,
  // without a ViewModel and its repositories.
  RentalDetailContent(
      uiState = uiState,
      onRetry = viewModel::retry,
      onChatClick = onChatClick,
      onPersonClick = onPersonClick,
  )
}

/**
 * Stateless Rental detail content: a loading indicator, an error with a Retry button, a not-found
 * message, a placeholder for a status not built yet, or the Requested rental, depending on
 * [uiState]. [today] and [zone] decide how dates are written (e.g. whether the year is shown); tests
 * pass fixed values so their expected texts do not depend on the day they run.
 */
@Composable
fun RentalDetailContent(
    uiState: RentalDetailUiState,
    onRetry: () -> Unit,
    onChatClick: () -> Unit,
    onPersonClick: (uid: String) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .testTag(C.Tag.rental_detail_screen)
  ) {
    // The checks go from the most general to the most specific: while loading, nothing else in the
    // state is meaningful yet; an error or a missing rental leaves no rental to show; and only a
    // rental that exists can be one whose status is not built yet.
    when {
      uiState.isLoading ->
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.testTag(C.Tag.rental_detail_loading))
          }
      uiState.errorMsg != null -> RentalDetailError(errorMsg = uiState.errorMsg, onRetry = onRetry)
      uiState.notFound -> RentalNotFound()
      !uiState.isSupported ->
          Column(
              modifier = Modifier.fillMaxSize().padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            RentalTitle(title = uiState.itemTitle)
            Text(
                text = stringResource(R.string.rental_detail_unsupported),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(C.Tag.rental_detail_unsupported),
            )
          }
      else ->
          Column(
              modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            RentalTitle(title = uiState.itemTitle)
          }
    }
  }
}

/** The load error ([errorMsg], or a generic text if it is blank) and a Retry button. */
@Composable
private fun RentalDetailError(errorMsg: String, onRetry: () -> Unit) {
  Column(
      modifier = Modifier.fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
        text = errorMsg.ifBlank { stringResource(R.string.rental_detail_error_fallback) },
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag(C.Tag.rental_detail_error),
    )
    Button(onClick = onRetry, modifier = Modifier.testTag(C.Tag.rental_detail_retry_button)) {
      Text(stringResource(R.string.rental_detail_retry))
    }
  }
}

@Composable
private fun RentalNotFound() {
  Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
    Text(
        text = stringResource(R.string.rental_detail_not_found),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag(C.Tag.rental_detail_not_found),
    )
  }
}

@Composable
private fun RentalTitle(title: String) {
  Text(
      text = title,
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.testTag(C.Tag.rental_detail_title),
  )
}
