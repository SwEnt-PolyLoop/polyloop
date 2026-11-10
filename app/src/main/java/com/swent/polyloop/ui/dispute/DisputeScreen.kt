// Made with Claude.

package com.swent.polyloop.ui.dispute

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.polyloop.R
import com.swent.polyloop.model.dispute.DisputeStage
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.components.OfflineBanner
import com.swent.polyloop.ui.theme.PolyLoopTheme
import java.time.Instant
import java.time.LocalDate

/** What the user can do on the Dispute screen; each one is a [DisputeViewModel] method. */
class DisputeActions(
    val onStartEditing: () -> Unit = {},
    val onDraft: (lenderPercent: Int) -> Unit = {},
    val onCancelEditing: () -> Unit = {},
    val onSendDraft: () -> Unit = {},
    val onAccept: () -> Unit = {},
    val onAskEscalate: () -> Unit = {},
    val onDismissEscalate: () -> Unit = {},
    val onEscalate: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** The Dispute screen of one rental, backed by [viewModel]. */
@Composable
fun DisputeScreen(viewModel: DisputeViewModel) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val actions =
      remember(viewModel) {
        DisputeActions(
            onStartEditing = viewModel::startEditing,
            onDraft = viewModel::setDraft,
            onCancelEditing = viewModel::cancelEditing,
            onSendDraft = viewModel::sendDraft,
            onAccept = viewModel::accept,
            onAskEscalate = viewModel::askEscalate,
            onDismissEscalate = viewModel::dismissEscalate,
            onEscalate = viewModel::escalate,
            onRetry = viewModel::retry,
        )
      }
  DisputeContent(uiState = uiState, actions = actions)
}

/**
 * Stateless Dispute content: the offline banner, then a loading indicator, an error with a Retry
 * button, or the header and the current stage. The settle stage shows the evidence, the proposal
 * and the history; the later stages show "Coming soon" for now, then how the dispute got there when
 * the user did not move it on. A failed refresh is told under the header. [today] decides which
 * dates show their year.
 */
@Composable
fun DisputeContent(
    uiState: DisputeUiState,
    actions: DisputeActions,
    today: LocalDate = LocalDate.now(),
) {
  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .testTag(C.Tag.dispute_screen)
  ) {
    if (!uiState.isOnline) OfflineBanner()
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      when {
        uiState.isLoading ->
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).testTag(C.Tag.dispute_loading)
            )
        uiState.loadFailed -> DisputeError(onRetry = actions.onRetry)
        else ->
            Column(
                modifier =
                    Modifier.verticalScroll(rememberScrollState())
                        .padding(
                            start = dimensionResource(R.dimen.dispute_screen_padding),
                            top = dimensionResource(R.dimen.dispute_screen_padding_top),
                            end = dimensionResource(R.dimen.dispute_screen_padding),
                            bottom = dimensionResource(R.dimen.dispute_screen_padding),
                        ),
                verticalArrangement =
                    Arrangement.spacedBy(dimensionResource(R.dimen.dispute_section_spacing)),
            ) {
              DisputeHeader(uiState)
              if (uiState.refreshFailed) RefreshFailed(actions.onRetry)
              if (uiState.stage == DisputeStage.SETTLE) {
                EvidenceCard()
                ProposalCard(
                    uiState,
                    onStartEditing = actions.onStartEditing,
                    onDraft = actions.onDraft,
                    onCancel = actions.onCancelEditing,
                    onSend = actions.onSendDraft,
                    onAccept = actions.onAccept,
                )
                if (uiState.history.isNotEmpty()) HistoryCard(uiState, today)
              } else {
                ComingSoonCard()
                EscalationNoticeText(uiState)
              }
              EscalationFooter(uiState, actions.onAskEscalate)
              if (uiState.actionError == DisputeActionError.ESCALATE) {
                ErrorText(
                    text = stringResource(R.string.dispute_escalate_failed),
                    modifier = Modifier.testTag(C.Tag.dispute_escalate_error),
                )
              }
            }
      }
    }
  }
  if (uiState.isConfirmingEscalation) EscalateDialog(uiState, actions)
}

/** The note that the dispute could not be loaded again, with a Retry button. */
@Composable
private fun RefreshFailed(onRetry: () -> Unit) {
  Row(
      modifier = Modifier.testTag(C.Tag.dispute_refresh_failed),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    ErrorText(
        text = stringResource(R.string.dispute_refresh_failed),
        modifier = Modifier.weight(1f),
    )
    TextButton(onClick = onRetry, modifier = Modifier.testTag(C.Tag.dispute_retry_button)) {
      Text(stringResource(R.string.dispute_retry))
    }
  }
}

/** Who moved the dispute on to its current stage, if it was not the user. */
@Composable
private fun EscalationNoticeText(uiState: DisputeUiState) {
  val text =
      when (uiState.escalationNotice) {
        EscalationNotice.BY_OTHER ->
            stringResource(
                R.string.dispute_escalated_by_other,
                uiState.otherPersonName.ifBlank { stringResource(R.string.dispute_unknown_person) },
            )
        EscalationNotice.AUTOMATIC -> stringResource(R.string.dispute_escalated_automatically)
        null -> return
      }
  Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.testTag(C.Tag.dispute_escalation_notice),
  )
}

/** Asks to confirm moving the dispute on from direct settlement to the LLM proposal. */
@Composable
private fun EscalateDialog(uiState: DisputeUiState, actions: DisputeActions) {
  AlertDialog(
      onDismissRequest = actions.onDismissEscalate,
      title = { Text(stringResource(R.string.dispute_escalate_title)) },
      text = { Text(stringResource(R.string.dispute_escalate_text)) },
      confirmButton = {
        TextButton(
            onClick = actions.onEscalate,
            enabled = uiState.canEscalate,
            modifier = Modifier.testTag(C.Tag.dispute_escalate_confirm),
        ) {
          Text(stringResource(R.string.dispute_escalate_confirm))
        }
      },
      dismissButton = {
        TextButton(
            onClick = actions.onDismissEscalate,
            modifier = Modifier.testTag(C.Tag.dispute_escalate_dismiss),
        ) {
          Text(stringResource(R.string.dispute_cancel))
        }
      },
      modifier = Modifier.testTag(C.Tag.dispute_escalate_dialog),
  )
}

/**
 * Under the cards: the settled text once both parties agreed; otherwise, in direct settlement, the
 * only stage that escalates, the days left before it escalates on its own and "Escalate now", which
 * asks to confirm.
 */
@Composable
private fun EscalationFooter(uiState: DisputeUiState, onAskEscalate: () -> Unit) {
  when {
    uiState.isSettled ->
        Text(
            text = stringResource(R.string.dispute_settled_footer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().testTag(C.Tag.dispute_settled_footer),
        )
    uiState.stage == DisputeStage.SETTLE ->
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
              text =
                  if (uiState.daysUntilEscalation <= 0)
                      stringResource(R.string.dispute_auto_escalates_today)
                  else
                      pluralStringResource(
                          R.plurals.dispute_auto_escalates,
                          uiState.daysUntilEscalation,
                          uiState.daysUntilEscalation,
                      ),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.weight(1f).testTag(C.Tag.dispute_escalation),
          )
          TextButton(
              onClick = onAskEscalate,
              enabled = uiState.canEscalate,
              modifier = Modifier.testTag(C.Tag.dispute_escalate_button),
          ) {
            Text(stringResource(R.string.dispute_escalate_now), fontWeight = FontWeight.Bold)
          }
        }
  }
}

/** The load error and a Retry button. */
@Composable
private fun DisputeError(onRetry: () -> Unit) {
  Column(
      modifier = Modifier.fillMaxSize().padding(dimensionResource(R.dimen.dispute_screen_padding)),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
        text = stringResource(R.string.dispute_error_fallback),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag(C.Tag.dispute_error),
    )
    Button(
        onClick = onRetry,
        modifier =
            Modifier.padding(top = dimensionResource(R.dimen.dispute_section_spacing))
                .testTag(C.Tag.dispute_retry_button),
    ) {
      Text(stringResource(R.string.dispute_retry))
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun DisputeContentPreview() {
  val at = Instant.parse("2026-10-02T10:00:00Z")
  PolyLoopTheme {
    DisputeContent(
        uiState =
            DisputeUiState(
                itemTitle = "Bike pump",
                otherPersonName = "Lender",
                deposit = 60,
                history =
                    listOf(
                        ProposalRow(byMe = false, lenderPercent = 70, createdAt = at),
                        ProposalRow(
                            byMe = true,
                            lenderPercent = 40,
                            createdAt = at.minusSeconds(86_400),
                        ),
                    ),
                daysUntilEscalation = 28,
                isLoading = false,
            ),
        actions = DisputeActions(),
    )
  }
}
