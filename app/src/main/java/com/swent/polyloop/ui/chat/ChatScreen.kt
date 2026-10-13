// Made with Claude.

package com.swent.polyloop.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.polyloop.R
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.chatlist.formatMessageTime
import com.swent.polyloop.ui.listingdetail.formatRangeEnds
import com.swent.polyloop.ui.theme.PolyLoopTheme
import java.time.Instant
import java.time.LocalDate

/** The Chat screen of one rental (Figma frame 19). */
@Composable
fun ChatScreen(viewModel: ChatViewModel, onRentalDetailsClick: () -> Unit = {}) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  ChatContent(
      uiState = uiState,
      onDraftChange = viewModel::onDraftChange,
      onSend = viewModel::send,
      onRetry = viewModel::retry,
      onRentalDetailsClick = onRentalDetailsClick,
  )
}

/**
 * Stateless Chat content: the header, then a loading indicator, an error with a Retry button, the
 * empty state or the messages, and the bar to type and send a message. [now] decides which messages
 * are from today.
 */
@Composable
fun ChatContent(
    uiState: ChatUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    onRentalDetailsClick: () -> Unit,
    now: Instant = Instant.now(),
) {
  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .testTag(C.Tag.chat_screen)
  ) {
    uiState.header?.let {
      ChatHeaderBar(header = it, onRentalDetailsClick = onRentalDetailsClick)
      HorizontalDivider()
    }
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      when {
        uiState.isLoading ->
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).testTag(C.Tag.chat_loading)
            )
        uiState.errorMsg != null -> ChatError(errorMsg = uiState.errorMsg, onRetry = onRetry)
        uiState.messages.isEmpty() ->
            Text(
                text = stringResource(R.string.chat_empty),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier.align(Alignment.Center).padding(16.dp).testTag(C.Tag.chat_empty),
            )
        else -> MessageList(messages = uiState.messages, now = now)
      }
    }
    // Typing is only possible once the chat is loaded.
    if (!uiState.isLoading && uiState.errorMsg == null) {
      HorizontalDivider()
      InputBar(uiState = uiState, onDraftChange = onDraftChange, onSend = onSend)
    }
  }
}

/** The other person, the dates and the item, with the "Rental details" link on the right. */
@Composable
private fun ChatHeaderBar(header: ChatHeader, onRentalDetailsClick: () -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
          text = header.otherPersonName.ifBlank { stringResource(R.string.chat_unknown_person) },
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.testTag(C.Tag.chat_header_name),
      )
      Text(
          text = formatDates(header.startDate, header.endDate),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag(C.Tag.chat_header_dates),
      )
      Text(
          text = header.itemTitle,
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.testTag(C.Tag.chat_header_item),
      )
    }
    Text(
        text = stringResource(R.string.chat_rental_details),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier =
            Modifier.padding(start = 12.dp)
                .clickable(onClick = onRentalDetailsClick)
                .testTag(C.Tag.chat_rental_details),
    )
  }
}

/** The rental's dates, e.g. "20 – 22 Oct". */
@Composable
private fun formatDates(start: LocalDate, end: LocalDate): String {
  val (from, to) = formatRangeEnds(start, end)
  return stringResource(R.string.chat_date_range, from, to)
}

/**
 * The messages, oldest first, scrolled to the newest one when the chat opens or a message arrives.
 */
@Composable
private fun MessageList(messages: List<ChatMessageRow>, now: Instant) {
  val listState = rememberLazyListState()
  LaunchedEffect(messages.size) { listState.scrollToItem(messages.lastIndex) }
  LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize().testTag(C.Tag.chat_messages),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    items(messages, key = { it.id }) { message ->
      when (message.kind) {
        MessageKind.SYSTEM -> SystemMessage(message, now)
        else -> MessageBubble(message, now)
      }
    }
  }
}

/**
 * One message in a bubble: mine on the right in red with white text, theirs on the left in white.
 * The time sits in the bottom corner on the same side.
 */
@Composable
private fun MessageBubble(message: ChatMessageRow, now: Instant) {
  val mine = message.kind == MessageKind.MINE
  val colors = MaterialTheme.colorScheme
  Box(
      modifier = Modifier.fillMaxWidth(),
      contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart,
  ) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (mine) colors.primary else colors.surface,
        contentColor = if (mine) colors.onPrimary else colors.onSurface,
        border = if (mine) null else BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.widthIn(max = 280.dp).testTag(C.Tag.chat_message_ + message.id),
    ) {
      Column(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
      ) {
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag(C.Tag.chat_message_text),
        )
        Text(
            text = formatMessageTime(message.sentAt, now),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 2.dp).testTag(C.Tag.chat_message_time),
        )
      }
    }
  }
}

/** A message written by the app, centred and in grey, with its time. */
@Composable
private fun SystemMessage(message: ChatMessageRow, now: Instant) {
  Text(
      text =
          stringResource(
              R.string.chat_system_message,
              message.text,
              formatMessageTime(message.sentAt, now),
          ),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.chat_message_ + message.id),
  )
}

/** The rounded text field and the red Send icon, with the "not sent" error under them. */
@Composable
private fun InputBar(uiState: ChatUiState, onDraftChange: (String) -> Unit, onSend: () -> Unit) {
  Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(
          value = uiState.draft,
          onValueChange = onDraftChange,
          placeholder = { Text(stringResource(R.string.chat_input_placeholder)) },
          shape = RoundedCornerShape(24.dp),
          maxLines = 4,
          modifier = Modifier.weight(1f).testTag(C.Tag.chat_input),
      )
      IconButton(
          onClick = onSend,
          enabled = uiState.canSend,
          modifier = Modifier.padding(start = 4.dp).testTag(C.Tag.chat_send_button),
      ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = stringResource(R.string.chat_send),
            tint =
                if (uiState.canSend) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    if (uiState.sendFailed) {
      Text(
          text = stringResource(R.string.chat_send_failed),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.padding(top = 4.dp).testTag(C.Tag.chat_send_failed),
      )
    }
  }
}

/** The load error ([errorMsg], or a generic text if it is blank) and a Retry button. */
@Composable
private fun ChatError(errorMsg: String, onRetry: () -> Unit) {
  Column(
      modifier = Modifier.fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
        text = errorMsg.ifBlank { stringResource(R.string.chat_error_fallback) },
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag(C.Tag.chat_error),
    )
    Button(onClick = onRetry, modifier = Modifier.testTag(C.Tag.chat_retry_button)) {
      Text(stringResource(R.string.chat_retry))
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun ChatContentPreview() {
  val now = Instant.parse("2026-10-13T15:30:00Z")
  PolyLoopTheme {
    ChatContent(
        uiState =
            ChatUiState(
                header =
                    ChatHeader(
                        "Ana",
                        "Camping tent",
                        LocalDate.of(2026, 10, 20),
                        LocalDate.of(2026, 10, 22),
                    ),
                messages =
                    listOf(
                        ChatMessageRow(
                            "m1",
                            "Request sent",
                            now.minusSeconds(600),
                            MessageKind.SYSTEM,
                        ),
                        ChatMessageRow(
                            "m2",
                            "Hi! When do you need it?",
                            now.minusSeconds(300),
                            MessageKind.THEIRS,
                        ),
                        ChatMessageRow("m3", "Friday, if that's ok", now, MessageKind.MINE),
                    ),
                draft = "See you",
                isLoading = false,
            ),
        onDraftChange = {},
        onSend = {},
        onRetry = {},
        onRentalDetailsClick = {},
        now = now,
    )
  }
}
