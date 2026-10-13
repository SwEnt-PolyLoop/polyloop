// Made with Claude.

package com.swent.polyloop.ui.chatlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.polyloop.R
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.theme.PolyLoopTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The Chat list screen: one row per conversation of the signed-in user (Figma frame 18). */
@Composable
fun ChatListScreen(viewModel: ChatListViewModel, onConversationClick: (String) -> Unit = {}) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  ChatListContent(
      uiState = uiState,
      onConversationClick = onConversationClick,
      onRetry = viewModel::retry,
  )
}

/**
 * Stateless Chat list content: the title, then a loading indicator, an error with a Retry button,
 * the empty state or the conversations, depending on [uiState]. [now] decides which messages are
 * from today.
 */
@Composable
fun ChatListContent(
    uiState: ChatListUiState,
    onConversationClick: (String) -> Unit,
    onRetry: () -> Unit,
    now: Instant = Instant.now(),
) {
  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .testTag(C.Tag.chat_list_screen)
  ) {
    Text(
        text = stringResource(R.string.chat_list_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        modifier =
            Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
                .testTag(C.Tag.chat_list_title),
    )
    when {
      uiState.isLoading ->
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.testTag(C.Tag.chat_list_loading))
          }
      uiState.errorMsg != null -> ChatListError(errorMsg = uiState.errorMsg, onRetry = onRetry)
      uiState.rows.isEmpty() ->
          Box(
              modifier = Modifier.fillMaxSize().padding(16.dp),
              contentAlignment = Alignment.Center,
          ) {
            Text(
                text = stringResource(R.string.chat_list_empty),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(C.Tag.chat_list_empty),
            )
          }
      else ->
          // Rows touch each other, separated by a thin line (Figma frame 18).
          LazyColumn(modifier = Modifier.fillMaxWidth().testTag(C.Tag.chat_list_list)) {
            items(uiState.rows, key = { it.rentalId }) { row ->
              ConversationRow(
                  row = row,
                  now = now,
                  onClick = { onConversationClick(row.rentalId) },
              )
              HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
          }
    }
  }
}

/** One conversation: the avatar, the other person and the time, the item, the last message. */
@Composable
private fun ConversationRow(row: ChatListRow, now: Instant, onClick: () -> Unit) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .clickable(onClick = onClick)
              .padding(horizontal = 16.dp, vertical = 12.dp)
              .testTag(C.Tag.chat_list_row_ + row.rentalId),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Avatar(initial = initialOf(row.otherPersonName))
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text =
                row.otherPersonName.ifBlank { stringResource(R.string.chat_list_unknown_person) },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag(C.Tag.chat_list_row_name),
        )
        Text(
            text = formatMessageTime(row.lastMessageAt, now),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp).testTag(C.Tag.chat_list_row_time),
        )
      }
      Text(
          text = row.itemTitle,
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.testTag(C.Tag.chat_list_row_item),
      )
      Text(
          text = row.lastMessage.ifBlank { stringResource(R.string.chat_list_no_messages) },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.testTag(C.Tag.chat_list_row_message),
      )
    }
  }
}

/** A circle with the other person's initial. */
@Composable
private fun Avatar(initial: String) {
  Surface(
      shape = CircleShape,
      color = MaterialTheme.colorScheme.surfaceVariant,
      contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(44.dp).testTag(C.Tag.chat_list_row_avatar),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(text = initial, style = MaterialTheme.typography.titleMedium)
    }
  }
}

/** The load error ([errorMsg], or a generic text if it is blank) and a Retry button. */
@Composable
private fun ChatListError(errorMsg: String, onRetry: () -> Unit) {
  Column(
      modifier = Modifier.fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
        text = errorMsg.ifBlank { stringResource(R.string.chat_list_error_fallback) },
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag(C.Tag.chat_list_error),
    )
    Button(onClick = onRetry, modifier = Modifier.testTag(C.Tag.chat_list_retry_button)) {
      Text(stringResource(R.string.chat_list_retry))
    }
  }
}

/** The first letter of [name] in upper case, or "?" when the name is blank. */
internal fun initialOf(name: String): String =
    name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

/**
 * When a message was sent, as seen in [zone]: the time for today's messages ("15:02"), the day
 * otherwise ("13 Oct").
 */
internal fun formatMessageTime(
    at: Instant,
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
  val sent = at.atZone(zone)
  val pattern = if (sent.toLocalDate() == now.atZone(zone).toLocalDate()) "HH:mm" else "d MMM"
  return sent.format(DateTimeFormatter.ofPattern(pattern, locale))
}

@Preview(showBackground = true)
@Composable
private fun ChatListContentPreview() {
  val now = Instant.parse("2026-10-13T15:30:00Z")
  PolyLoopTheme {
    ChatListContent(
        uiState =
            ChatListUiState(
                rows =
                    listOf(
                        ChatListRow(
                            "r1",
                            "Ana",
                            "Camping tent",
                            "See you at 5?",
                            now,
                            RentalStatus.ACCEPTED,
                        ),
                        ChatListRow(
                            "r2",
                            "",
                            "Electric drill",
                            "",
                            now.minusSeconds(86_400 * 3),
                            RentalStatus.REQUESTED,
                        ),
                    ),
                isLoading = false,
            ),
        onConversationClick = {},
        onRetry = {},
        now = now,
    )
  }
}
