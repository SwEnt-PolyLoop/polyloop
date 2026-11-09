// Made with Claude.

package com.swent.polyloop.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.swent.polyloop.R
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.components.formatDay
import java.time.ZoneId

/**
 * The deposit offer above the message bar (Figma frames 19.10 to 19.13). The author of the offer
 * only sees it waiting. The other person can accept or decline it and, on the lender's first offer,
 * write one counteroffer.
 *
 * @param offer the offer waiting for an answer.
 * @param uiState gives the other person's name, the form and whether the actions are available.
 * @param onAccept accepts the offer.
 * @param onDecline declines the offer.
 * @param onStartCounter opens the counteroffer form.
 * @param onCancelCounter closes the counteroffer form.
 * @param onCounterDraftChange reports the amount typed in the form.
 * @param onSendCounter sends the counteroffer.
 * @param zone the time zone that decides the expiry day; tests pass a fixed one.
 */
@Composable
internal fun ChatOfferCard(
    offer: ChatOffer,
    uiState: ChatUiState,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onStartCounter: () -> Unit,
    onCancelCounter: () -> Unit,
    onCounterDraftChange: (String) -> Unit,
    onSendCounter: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
  OutlinedCard(
      modifier =
          Modifier.fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 8.dp)
              .testTag(C.Tag.chat_offer_card)
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      when {
        uiState.isCountering ->
            CounterForm(
                offer = offer,
                uiState = uiState,
                onCancel = onCancelCounter,
                onDraftChange = onCounterDraftChange,
                onSend = onSendCounter,
            )
        offer.isMine -> WaitingOffer(offer, uiState.header?.otherPersonName.orEmpty(), zone)
        else ->
            ReceivedOffer(
                offer = offer,
                uiState = uiState,
                onAccept = onAccept,
                onDecline = onDecline,
                onStartCounter = onStartCounter,
                zone = zone,
            )
      }
      uiState.offerError?.let {
        Text(
            text = it.ifBlank { stringResource(R.string.chat_offer_error) },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(C.Tag.chat_offer_error),
        )
      }
    }
  }
}

/** The signed-in user's own offer, with no action: they wait for the other person. */
@Composable
private fun WaitingOffer(offer: ChatOffer, otherPersonName: String, zone: ZoneId) {
  Header(
      title =
          stringResource(
              if (offer.isCounter) R.string.chat_offer_title_my_counter
              else R.string.chat_offer_title_mine
          ),
      badge = stringResource(R.string.chat_offer_badge_waiting),
      isAnswer = false,
  )
  Amount(stringResource(R.string.chat_offer_amount_mine, offer.amount))
  Note(
      stringResource(
          R.string.chat_offer_note_waiting,
          otherPersonName.ifBlank { stringResource(R.string.chat_unknown_person) },
          expiryDay(offer, zone),
      )
  )
}

/** The other person's offer, with its answers. */
@Composable
private fun ReceivedOffer(
    offer: ChatOffer,
    uiState: ChatUiState,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onStartCounter: () -> Unit,
    zone: ZoneId,
) {
  Header(
      title =
          stringResource(
              if (offer.isCounter) R.string.chat_offer_title_counter else R.string.chat_offer_title
          ),
      badge = stringResource(R.string.chat_offer_badge_answer),
      isAnswer = true,
  )
  Amount(stringResource(R.string.listing_card_price, offer.amount))
  val previous = offer.previousAmount
  Note(
      if (offer.isCounter && previous != null) {
        stringResource(R.string.chat_offer_note_counter, previous, expiryDay(offer, zone))
      } else {
        stringResource(R.string.chat_offer_note_expires, expiryDay(offer, zone))
      }
  )
  if (offer.overlappingRequests > 0) {
    Text(
        text =
            pluralStringResource(
                R.plurals.chat_offer_overlapping,
                offer.overlappingRequests,
                offer.overlappingRequests,
            ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(C.Tag.chat_offer_overlapping),
    )
  }
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedButton(
        onClick = onDecline,
        enabled = uiState.canAnswerOffer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.weight(1f).testTag(C.Tag.chat_offer_decline),
    ) {
      Text(stringResource(R.string.chat_offer_decline), maxLines = 1)
    }
    if (offer.canCounter) {
      OutlinedButton(
          onClick = onStartCounter,
          enabled = uiState.canAnswerOffer,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.weight(1f).testTag(C.Tag.chat_offer_counter),
      ) {
        Text(stringResource(R.string.chat_offer_counter), maxLines = 1)
      }
    }
    Button(
        onClick = onAccept,
        enabled = uiState.canAnswerOffer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.weight(1f).testTag(C.Tag.chat_offer_accept),
    ) {
      Text(stringResource(R.string.chat_offer_accept), maxLines = 1)
    }
  }
}

/** The form of the borrower's single counteroffer. */
@Composable
private fun CounterForm(
    offer: ChatOffer,
    uiState: ChatUiState,
    onCancel: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
  Text(
      text = stringResource(R.string.chat_offer_title_my_counter),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.testTag(C.Tag.chat_offer_title),
  )
  OutlinedTextField(
      value = uiState.counterDraft,
      onValueChange = onDraftChange,
      enabled = !uiState.isAnswering,
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      suffix = { Text(stringResource(R.string.chat_offer_suffix)) },
      shape = MaterialTheme.shapes.medium,
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.chat_offer_counter_field),
  )
  Note(stringResource(R.string.chat_offer_counter_hint, offer.amount))
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedButton(
        onClick = onCancel,
        enabled = !uiState.isAnswering,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.weight(1f).testTag(C.Tag.chat_offer_counter_back),
    ) {
      Text(stringResource(R.string.chat_offer_back))
    }
    Button(
        onClick = onSend,
        enabled = uiState.canSendCounter,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.weight(1f).testTag(C.Tag.chat_offer_counter_send),
    ) {
      Text(stringResource(R.string.chat_offer_send))
    }
  }
}

/** The card's title and, on its right, a badge in red when an answer is expected. */
@Composable
private fun Header(title: String, badge: String, isAnswer: Boolean) {
  val colors = MaterialTheme.colorScheme
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.weight(1f).testTag(C.Tag.chat_offer_title),
    )
    Surface(
        shape = CircleShape,
        color = if (isAnswer) colors.primaryContainer else colors.surfaceVariant,
        contentColor = if (isAnswer) colors.onPrimaryContainer else colors.onSurfaceVariant,
    ) {
      Text(
          text = badge,
          style = MaterialTheme.typography.labelMedium,
          modifier =
              Modifier.padding(horizontal = 10.dp, vertical = 5.dp).testTag(C.Tag.chat_offer_badge),
      )
    }
  }
}

@Composable
private fun Amount(text: String) {
  Text(
      text = text,
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.testTag(C.Tag.chat_offer_amount),
  )
}

@Composable
private fun Note(text: String) {
  Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.testTag(C.Tag.chat_offer_note),
  )
}

/** The day the offer expires, e.g. "22 Oct". */
@Composable
private fun expiryDay(offer: ChatOffer, zone: ZoneId): String =
    formatDay(
        offer.expiresAt.atZone(zone).toLocalDate(),
        locale = LocalConfiguration.current.locales[0],
    )
