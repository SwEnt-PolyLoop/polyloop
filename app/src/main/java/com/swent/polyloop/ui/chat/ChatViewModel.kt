// Made with Claude.

package com.swent.polyloop.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.chat.ChatRepository
import com.swent.polyloop.model.chat.Message
import com.swent.polyloop.model.chat.MessageType
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Who a message comes from, which decides where and how it is drawn. */
enum class MessageKind {
  /** Sent by the signed-in user: on the right. */
  MINE,
  /** Sent by the other person: on the left. */
  THEIRS,
  /** Written by the app, e.g. when the rental is accepted (later sprint): centred. */
  SYSTEM,
}

/** One message, ready to display. */
data class ChatMessageRow(
    val id: String,
    val text: String,
    val sentAt: Instant,
    val kind: MessageKind,
)

/**
 * The header of the chat.
 *
 * @property otherPersonName "" when the name is missing; the screen then shows a placeholder.
 * @property endDate the return date; both days are part of the rental.
 */
data class ChatHeader(
    val otherPersonName: String,
    val itemTitle: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
)

/**
 * State of the Chat screen. [messages] are oldest first (the newest is shown at the bottom).
 *
 * [errorMsg] is non-null when the chat cannot be loaded (or nobody is signed in, or the rental does
 * not exist); it is blank when there is no message to show, and the screen then shows a generic
 * text. [draft] is the text being typed: it is cleared only once it is sent, so after a failed send
 * ([sendFailed]) pressing Send again retries it.
 */
data class ChatUiState(
    val header: ChatHeader? = null,
    val messages: List<ChatMessageRow> = emptyList(),
    val draft: String = "",
    val isSending: Boolean = false,
    val sendFailed: Boolean = false,
    val isLoading: Boolean = true,
    val errorMsg: String? = null,
) {
  /** True when Send can be pressed: some text, and no message already on its way. */
  val canSend: Boolean
    get() = draft.isNotBlank() && !isSending
}

/**
 * The conversation of one rental: its header, the messages live, and sending new ones as the
 * signed-in user.
 */
class ChatViewModel(
    private val rentalId: String,
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ChatUiState())
  val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

  private var observing: Job? = null

  init {
    observe()
  }

  /** Loads the chat again, e.g. after an error. */
  fun retry() = observe()

  /** Updates the text being typed; typing again hides the "not sent" error. */
  fun onDraftChange(text: String) = _uiState.update { it.copy(draft = text, sendFailed = false) }

  /** Sends the text being typed, unless it is blank or a message is already on its way. */
  fun send() {
    val state = _uiState.value
    if (!state.canSend) return
    val text = state.draft
    _uiState.update { it.copy(isSending = true, sendFailed = false) }
    viewModelScope.launch {
      val sent = chatRepository.sendMessage(rentalId, text).isSuccess
      _uiState.update {
        when {
          !sent -> it.copy(isSending = false, sendFailed = true)
          // Keep what the user typed while the message was on its way.
          it.draft == text -> it.copy(isSending = false, draft = "")
          else -> it.copy(isSending = false)
        }
      }
    }
  }

  private fun observe() {
    observing?.cancel()
    _uiState.update { it.copy(isLoading = true, errorMsg = null) }
    val uid = authRepository.getCurrentUser()?.uid
    if (uid == null) {
      showError("")
      return
    }
    observing = viewModelScope.launch {
      val conversation =
          chatRepository.getConversation(rentalId).getOrElse {
            showError(it.message ?: "")
            return@launch
          }
      if (conversation == null) {
        showError("")
        return@launch
      }
      _uiState.update {
        it.copy(
            header =
                ChatHeader(
                    otherPersonName = conversation.otherPersonName(uid),
                    itemTitle = conversation.listingTitle,
                    startDate = conversation.startDate,
                    endDate = conversation.endDate,
                )
        )
      }
      chatRepository
          .observeMessages(rentalId)
          .map { messages -> messages.map { it.toRow(uid) } }
          .catch { e -> showError(e.message ?: "") }
          .collect { rows ->
            _uiState.update { it.copy(messages = rows, isLoading = false, errorMsg = null) }
          }
    }
  }

  private fun showError(message: String) = _uiState.update {
    it.copy(isLoading = false, errorMsg = message)
  }

  /** Builds the ViewModel with its dependencies (manual constructor injection). */
  class Factory(
      private val rentalId: String,
      private val chatRepository: ChatRepository,
      private val authRepository: AuthRepository,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(ChatViewModel::class.java)) {
        "Unknown ViewModel class: ${modelClass.name}"
      }
      @Suppress("UNCHECKED_CAST")
      return ChatViewModel(rentalId, chatRepository, authRepository) as T
    }
  }

  private companion object {
    fun Message.toRow(uid: String) =
        ChatMessageRow(
            id = id,
            text = text,
            sentAt = sentAt,
            kind =
                when {
                  type == MessageType.SYSTEM -> MessageKind.SYSTEM
                  senderId == uid -> MessageKind.MINE
                  else -> MessageKind.THEIRS
                },
        )
  }
}
