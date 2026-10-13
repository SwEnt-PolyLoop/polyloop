// Made with Claude.

package com.swent.polyloop.ui.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.chat.ChatRepository
import com.swent.polyloop.model.chat.Conversation
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One row of the chat list, ready to display.
 *
 * @property otherPersonName the person the user talks to; "" when the name is missing, and the
 *   screen then shows a placeholder.
 * @property lastMessage "" until the first message is sent; the screen then shows a placeholder.
 * @property lastMessageAt when the last message was sent, or when the rental was created.
 */
data class ChatListRow(
    val rentalId: String,
    val otherPersonName: String,
    val itemTitle: String,
    val lastMessage: String,
    val lastMessageAt: Instant,
)

/**
 * State of the Chat list screen. [rows] are newest first. On an error the last rows are kept.
 * [errorMsg] is non-null when the conversations cannot be loaded (or nobody is signed in); it is
 * blank when there is no message to show, and the screen then shows a generic text.
 */
data class ChatListUiState(
    val rows: List<ChatListRow> = emptyList(),
    val isLoading: Boolean = true,
    val errorMsg: String? = null,
)

/**
 * Shows the signed-in user's conversations, one per rental. The list follows the repository live,
 * so a new message moves its conversation to the top without reloading.
 */
class ChatListViewModel(
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ChatListUiState())
  val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

  private var observing: Job? = null

  init {
    observe()
  }

  /** Starts following the conversations again, e.g. after an error. */
  fun retry() = observe()

  private fun observe() {
    observing?.cancel()
    _uiState.update { it.copy(isLoading = true, errorMsg = null) }
    val uid = authRepository.getCurrentUser()?.uid
    if (uid == null) {
      _uiState.update { it.copy(rows = emptyList(), isLoading = false, errorMsg = "") }
      return
    }
    observing = viewModelScope.launch {
      chatRepository
          .observeConversations(uid)
          .map { conversations -> conversations.map { it.toRow(uid) } }
          .catch { e -> _uiState.update { it.copy(isLoading = false, errorMsg = e.message ?: "") } }
          .collect { rows ->
            _uiState.update { it.copy(rows = rows, isLoading = false, errorMsg = null) }
          }
    }
  }

  /** Builds the ViewModel with its dependencies (manual constructor injection). */
  class Factory(
      private val chatRepository: ChatRepository,
      private val authRepository: AuthRepository,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(ChatListViewModel::class.java)) {
        "Unknown ViewModel class: ${modelClass.name}"
      }
      @Suppress("UNCHECKED_CAST")
      return ChatListViewModel(chatRepository, authRepository) as T
    }
  }

  private companion object {
    fun Conversation.toRow(uid: String) =
        ChatListRow(
            rentalId = rentalId,
            otherPersonName = otherPersonName(uid),
            itemTitle = listingTitle,
            lastMessage = lastMessage,
            lastMessageAt = lastMessageAt,
        )
  }
}
