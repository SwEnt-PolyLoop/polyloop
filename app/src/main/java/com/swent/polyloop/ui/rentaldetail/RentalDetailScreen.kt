// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.polyloop.R
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.components.badgeColors
import com.swent.polyloop.ui.components.formatDay
import com.swent.polyloop.ui.myrentals.labelRes
import java.time.Instant
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
            StageStrip()
            StatusCard(status = uiState.status, sentAt = uiState.sentAt, today = today, zone = zone)
          }
    }
  }
}

/**
 * The stages after the request, one grey bar each. A Requested rental has reached none of them, so
 * all four stay grey.
 */
@Composable
private fun StageStrip() {
  val labels =
      listOf(
          R.string.rental_detail_stage_accepted,
          R.string.rental_detail_stage_in_use,
          R.string.rental_detail_stage_returned,
          R.string.rental_detail_stage_closed,
      )
  Row(
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.rental_detail_stage_strip),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    labels.forEach { label ->
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
        )
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

/**
 * The status badge, the headline and when the request was sent. The sent day is read in [zone] and
 * written with its year only when it is not [today]'s year.
 */
@Composable
private fun StatusCard(status: RentalStatus?, sentAt: Instant?, today: LocalDate, zone: ZoneId) {
  val locale = LocalConfiguration.current.locales[0]
  OutlinedCard(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        status?.let { StatusBadge(it) }
        Text(
            text = stringResource(R.string.rental_detail_status_headline),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(C.Tag.rental_detail_status_headline),
        )
      }
      sentAt?.let {
        val sentDay = it.atZone(zone).toLocalDate()
        Text(
            text =
                stringResource(
                    R.string.rental_detail_status_body,
                    formatDay(sentDay, withYear = sentDay.year != today.year, locale = locale),
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(C.Tag.rental_detail_status_body),
        )
      }
    }
  }
}

@Composable
private fun StatusBadge(status: RentalStatus) {
  val (container, content) = status.badgeColors()
  Surface(shape = CircleShape, color = container, contentColor = content) {
    Text(
        text = stringResource(status.labelRes),
        style = MaterialTheme.typography.labelMedium,
        modifier =
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                .testTag(C.Tag.rental_detail_status),
    )
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
