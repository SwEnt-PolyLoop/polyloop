// Made with Claude.

package com.swent.polyloop.ui.dispute

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.swent.polyloop.R
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.components.formatDay
import java.time.LocalDate
import java.time.ZoneId

/** A white card with a bold [title] and its [content] under it. */
@Composable
private fun DisputeCard(
    title: String,
    modifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
  OutlinedCard(modifier = modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(dimensionResource(R.dimen.dispute_card_padding)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.dispute_card_spacing)),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = titleModifier.weight(1f),
        )
        trailing()
      }
      content()
    }
  }
}

/** The evidence card; it stays empty until the pickup and return photos exist (Handover). */
@Composable
internal fun EvidenceCard() {
  DisputeCard(
      title = stringResource(R.string.dispute_evidence),
      modifier = Modifier.testTag(C.Tag.dispute_evidence_card),
      trailing = {
        Text(
            text = stringResource(R.string.dispute_evidence_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      },
  )
}

/**
 * The proposal on the table, with what the user would get back and the buttons that apply: "Propose
 * a split" when there is none, "Update" for the user's own (and who it waits for), "Counter" and
 * "Accept" for the other person's. Once settled, the agreed split without any button.
 *
 * While [DisputeUiState.isEditing], the card edits the draft instead: a handle on the bar and the
 * two typed percentages move it by 1% through [onDraft], and the buttons are "Cancel" and "Send".
 * What the other person just did shows at the top, and the error of a failed proposal or acceptance
 * at the bottom.
 */
@Composable
internal fun ProposalCard(
    uiState: DisputeUiState,
    onStartEditing: () -> Unit,
    onDraft: (lenderPercent: Int) -> Unit,
    onCancel: () -> Unit,
    onSend: () -> Unit,
    onAccept: () -> Unit,
) {
  val proposal = uiState.latestProposal
  val otherName =
      uiState.otherPersonName.ifBlank { stringResource(R.string.dispute_unknown_person) }
  val draft = uiState.draftLenderPercent
  val title =
      when {
        draft != null -> stringResource(R.string.dispute_your_proposal)
        proposal == null -> stringResource(R.string.dispute_no_proposal)
        uiState.isSettled -> stringResource(R.string.dispute_agreed)
        proposal.byMe -> stringResource(R.string.dispute_you_propose)
        else -> stringResource(R.string.dispute_they_propose, otherName)
      }
  DisputeCard(
      title = title,
      modifier = Modifier.testTag(C.Tag.dispute_proposal_card),
      titleModifier = Modifier.testTag(C.Tag.dispute_proposal_title),
  ) {
    ProposalNotice(uiState, otherName)
    when {
      draft != null -> SplitEditor(uiState, draft, onDraft, onCancel, onSend)
      proposal == null ->
          Button(
              onClick = onStartEditing,
              enabled = uiState.canPropose,
              modifier = Modifier.fillMaxWidth().testTag(C.Tag.dispute_counter_button),
          ) {
            Text(stringResource(R.string.dispute_propose))
          }
      else -> {
        ProposalSplitBar(lenderPercent = proposal.lenderPercent)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          InlinePercent(
              label = stringResource(R.string.dispute_lender),
              description = stringResource(R.string.dispute_lender_share_description),
              text = "${proposal.lenderPercent}",
          )
          InlinePercent(
              label = stringResource(R.string.dispute_borrower),
              description = stringResource(R.string.dispute_borrower_share_description),
              text = "${proposal.borrowerPercent}",
          )
        }
        GetBackRow(
            label =
                stringResource(
                    if (uiState.isSettled) R.string.dispute_you_get_back
                    else R.string.dispute_you_would_get_back
                ),
            tenths = uiState.youGetBackTenths ?: 0,
            modifier = Modifier.testTag(C.Tag.dispute_get_back),
        )
        if (!uiState.isSettled)
            ProposalButtons(uiState, proposal.byMe, otherName, onStartEditing, onAccept)
      }
    }
    ProposalError(uiState.actionError)
  }
}

/**
 * The editor of the [draft] split. Only the typed text is kept here, so that a field can stay empty
 * while the user types; the split itself is [draft], moved through [onDraft].
 */
@Composable
private fun SplitEditor(
    uiState: DisputeUiState,
    draft: Int,
    onDraft: (lenderPercent: Int) -> Unit,
    onCancel: () -> Unit,
    onSend: () -> Unit,
) {
  // A field the user emptied, until they type a number or leave it; null shows the draft.
  var lenderTyped by rememberSaveable { mutableStateOf<String?>(null) }
  var borrowerTyped by rememberSaveable { mutableStateOf<String?>(null) }
  fun move(lenderPercent: Int) {
    lenderTyped = null
    borrowerTyped = null
    onDraft(lenderPercent)
  }
  ProposalSplitBar(lenderPercent = draft, onLenderPercent = ::move)
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    InlinePercent(
        label = stringResource(R.string.dispute_lender),
        description = stringResource(R.string.dispute_lender_share_description),
        text = lenderTyped ?: "$draft",
        onText = { if (it.isEmpty()) lenderTyped = "" else move(it.toInt()) },
        onBlur = { lenderTyped = null },
        modifier = Modifier.testTag(C.Tag.dispute_split_lender_field),
    )
    InlinePercent(
        label = stringResource(R.string.dispute_borrower),
        description = stringResource(R.string.dispute_borrower_share_description),
        text = borrowerTyped ?: "${100 - draft}",
        onText = { if (it.isEmpty()) borrowerTyped = "" else move(100 - it.toInt()) },
        onBlur = { borrowerTyped = null },
        modifier = Modifier.testTag(C.Tag.dispute_split_borrower_field),
    )
  }
  GetBackRow(
      label = stringResource(R.string.dispute_you_would_get_back),
      tenths = uiState.draftGetBackTenths ?: 0,
      modifier = Modifier.testTag(C.Tag.dispute_get_back),
  )
  Row(
      horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.dispute_card_spacing))
  ) {
    OutlinedButton(
        onClick = onCancel,
        enabled = !uiState.isSending,
        modifier = Modifier.weight(1f).testTag(C.Tag.dispute_split_cancel),
    ) {
      Text(stringResource(R.string.dispute_cancel))
    }
    Button(
        onClick = onSend,
        enabled = uiState.canSend && lenderTyped != "" && borrowerTyped != "",
        modifier = Modifier.weight(1f).testTag(C.Tag.dispute_split_send),
    ) {
      Text(stringResource(R.string.dispute_send))
    }
  }
}

/**
 * What the other person just did to the proposal: accepted the user's, or sent a new one, with its
 * split while the user edits (the card then shows the draft, not their offer).
 */
@Composable
private fun ProposalNotice(uiState: DisputeUiState, otherName: String) {
  val proposal = uiState.latestProposal
  val text =
      when {
        uiState.acceptedByOther -> stringResource(R.string.dispute_accepted_by_other, otherName)
        !uiState.showsOfferUpdated || proposal == null -> return
        uiState.isEditing ->
            stringResource(
                R.string.dispute_offer_changed_to,
                otherName,
                proposal.lenderPercent,
                proposal.borrowerPercent,
            )
        else -> stringResource(R.string.dispute_offer_updated, otherName)
      }
  Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.testTag(C.Tag.dispute_proposal_notice),
  )
}

/** The error of a failed proposal or acceptance, if [error] is one. */
@Composable
private fun ProposalError(error: DisputeActionError?) {
  val text =
      when (error) {
        DisputeActionError.PROPOSE -> R.string.dispute_propose_failed
        DisputeActionError.ACCEPT -> R.string.dispute_accept_failed
        DisputeActionError.PROPOSAL_CHANGED -> R.string.dispute_proposal_changed
        DisputeActionError.ESCALATE,
        null -> return
      }
  ErrorText(stringResource(text), Modifier.testTag(C.Tag.dispute_proposal_error))
}

/** [text] in the error colour. */
@Composable
internal fun ErrorText(text: String, modifier: Modifier = Modifier) {
  Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.error,
      modifier = modifier,
  )
}

/** "Update" and who the user waits for, or "Counter" and "Accept". */
@Composable
private fun ProposalButtons(
    uiState: DisputeUiState,
    byMe: Boolean,
    otherName: String,
    onStartEditing: () -> Unit,
    onAccept: () -> Unit,
) {
  Row(
      horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.dispute_card_spacing)),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    OutlinedButton(
        onClick = onStartEditing,
        enabled = uiState.canPropose,
        modifier = Modifier.weight(1f).testTag(C.Tag.dispute_counter_button),
    ) {
      Text(stringResource(if (byMe) R.string.dispute_update else R.string.dispute_counter))
    }
    if (byMe) {
      Text(
          text = stringResource(R.string.dispute_waiting, otherName),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.weight(1f).testTag(C.Tag.dispute_waiting),
      )
    } else {
      Button(
          onClick = onAccept,
          enabled = uiState.canAccept,
          modifier = Modifier.weight(1f).testTag(C.Tag.dispute_accept_button),
      ) {
        Text(stringResource(R.string.dispute_accept))
      }
    }
  }
}

/**
 * Every proposal, newest first: who sent it and on which day (with the year if it is not [today]'s
 * year), and the split as lender % / borrower %.
 */
@Composable
internal fun HistoryCard(uiState: DisputeUiState, today: LocalDate) {
  val otherName =
      uiState.otherPersonName.ifBlank { stringResource(R.string.dispute_unknown_person) }
  DisputeCard(
      title = stringResource(R.string.dispute_history),
      modifier = Modifier.testTag(C.Tag.dispute_history_card),
  ) {
    uiState.history.forEach { row ->
      val day = row.createdAt.atZone(ZoneId.systemDefault()).toLocalDate()
      Row(modifier = Modifier.fillMaxWidth().testTag(C.Tag.dispute_history_row)) {
        Text(
            text =
                stringResource(
                    R.string.dispute_history_author,
                    if (row.byMe) stringResource(R.string.dispute_history_you) else otherName,
                    formatDay(day, withYear = day.year != today.year),
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text =
                stringResource(
                    R.string.dispute_history_split,
                    row.lenderPercent,
                    row.borrowerPercent,
                ),
            style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
  }
}

/** What the LLM proposal and court stages show until they are built. */
@Composable
internal fun ComingSoonCard() {
  DisputeCard(
      title = stringResource(R.string.dispute_coming_soon),
      modifier = Modifier.testTag(C.Tag.dispute_coming_soon),
  )
}
