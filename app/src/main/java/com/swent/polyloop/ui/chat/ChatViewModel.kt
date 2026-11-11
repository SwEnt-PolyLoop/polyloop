// Made with Claude.

package com.swent.polyloop.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.chat.ChatRepository
import com.swent.polyloop.model.chat.Message
import com.swent.polyloop.model.chat.MessageType
import com.swent.polyloop.model.connectivity.ConnectivityRepository
import com.swent.polyloop.model.rental.Rental
import com.swent.polyloop.model.rental.RentalRepository
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.ui.createlisting.isWholeNumberAboveZero
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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
 * The deposit offer waiting for an answer, as seen by the signed-in user.
 *
 * @property amount the deposit offered, in PolyPoints.
 * @property previousAmount on a counteroffer, the amount the lender offered first.
 * @property isMine true when the signed-in user wrote it: they then wait, with no action.
 * @property isCounter true for the borrower's counteroffer.
 * @property expiresAt when it stops being valid.
 * @property overlappingRequests how many other requests accepting it would decline; only counted
 *   for the lender, who is the one to see them.
 */
data class ChatOffer(
    val amount: Int,
    val previousAmount: Int?,
    val isMine: Boolean,
    val isCounter: Boolean,
    val expiresAt: Instant,
    val overlappingRequests: Int,
) {
  /** True when the signed-in user can answer with a counteroffer: only once, on the first offer. */
  val canCounter: Boolean
    get() = !isMine && !isCounter
}

/**
 * State of the Chat screen. [messages] are oldest first (the newest is shown at the bottom).
 *
 * [errorMsg] is non-null when the chat cannot be loaded (or nobody is signed in, or the rental does
 * not exist); it is blank when there is no message to show, and the screen then shows a generic
 * text. [draft] is the text being typed: Send clears it right away, and puts it back if the message
 * could not be sent ([sendFailed]), so pressing Send again retries it.
 *
 * [offer] is the deposit offer waiting for an answer, if any. [isCountering] is true while the
 * borrower writes a counteroffer, whose amount is [counterDraft]. [isAnswering] is true while an
 * answer is being sent. [offerError] is non-null when an answer failed; it is blank when the
 * failure carried no message. [isOffline] is true while the phone has no connection, which disables
 * the answers.
 *
 * [pickupDate] is the rental's first day while it is accepted, and null otherwise; the screen shows
 * "Start pickup" when it is today.
 */
data class ChatUiState(
    val header: ChatHeader? = null,
    val messages: List<ChatMessageRow> = emptyList(),
    val draft: String = "",
    val sendFailed: Boolean = false,
    val isLoading: Boolean = true,
    val errorMsg: String? = null,
    val offer: ChatOffer? = null,
    val isCountering: Boolean = false,
    val counterDraft: String = "",
    val isAnswering: Boolean = false,
    val offerError: String? = null,
    val isOffline: Boolean = false,
    val pickupDate: LocalDate? = null,
) {
  /** True when Send can be pressed: there is some text. */
  val canSend: Boolean
    get() = draft.isNotBlank()

  /** True when the offer can be accepted, declined or countered. */
  val canAnswerOffer: Boolean
    get() = offer != null && !offer.isMine && !isOffline && !isAnswering

  /** True when the counteroffer can be sent: a whole amount from 1 to the cap. */
  val canSendCounter: Boolean
    get() = canAnswerOffer && offer?.canCounter == true && counterDraft.isWholeNumberAboveZero()
}

/**
 * The conversation of one rental: its header, the messages live, sending new ones as the signed-in
 * user, and answering the deposit offer waiting on the rental.
 *
 * @param rentalId the rental whose conversation this is.
 * @param chatRepository reads and sends the messages.
 * @param authRepository tells who the signed-in user is.
 * @param rentalRepository gives the offer and sends the answers.
 * @param connectivityRepository tells whether the phone is online.
 */
class ChatViewModel(
    private val rentalId: String,
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val rentalRepository: RentalRepository,
    private val connectivityRepository: ConnectivityRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ChatUiState())
  val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

  private var observing: Job? = null

  init {
    // Apart from the chat job, so that retry() neither stops nor duplicates it.
    viewModelScope.launch {
      connectivityRepository.isOnline.collect { online ->
        _uiState.update { it.copy(isOffline = !online) }
      }
    }
    observe()
  }

  /** Loads the chat again, e.g. after an error. */
  fun retry() = observe()

  /** Updates the text being typed; typing again hides the "not sent" error. */
  fun onDraftChange(text: String) = _uiState.update { it.copy(draft = text, sendFailed = false) }

  /**
   * Sends the text being typed, unless it is blank. The field is cleared right away: Firestore
   * shows the message at once but only confirms it when the server answers, which never happens
   * offline, so waiting would leave the text in the field and block the next message. If sending
   * fails, the text comes back, unless the user has started typing a new one.
   */
  fun send() {
    val text = _uiState.value.draft
    if (text.isBlank()) return
    _uiState.update { it.copy(draft = "", sendFailed = false) }
    viewModelScope.launch {
      if (chatRepository.sendMessage(rentalId, text).isFailure) {
        _uiState.update { it.copy(sendFailed = true, draft = it.draft.ifBlank { text }) }
      }
    }
  }

  /** Opens the counteroffer form, empty. */
  fun startCounter() {
    if (_uiState.value.offer?.canCounter != true) return
    _uiState.update { it.copy(isCountering = true, counterDraft = "", offerError = null) }
  }

  /** Closes the counteroffer form without sending anything. */
  fun cancelCounter() = _uiState.update { it.copy(isCountering = false, offerError = null) }

  fun onCounterDraftChange(amount: String) = _uiState.update { it.copy(counterDraft = amount) }

  /** Accepts the offer; does nothing unless [ChatUiState.canAnswerOffer] is true. */
  fun acceptOffer() {
    if (_uiState.value.canAnswerOffer) answer { rentalRepository.acceptOffer(rentalId) }
  }

  /** Declines the offer; does nothing unless [ChatUiState.canAnswerOffer] is true. */
  fun declineOffer() {
    if (_uiState.value.canAnswerOffer) answer { rentalRepository.declineOffer(rentalId) }
  }

  /** Sends the counteroffer; does nothing unless [ChatUiState.canSendCounter] is true. */
  fun sendCounter() {
    val state = _uiState.value
    if (!state.canSendCounter) return
    answer { rentalRepository.counterOffer(rentalId, state.counterDraft.toInt()) }
  }

  /**
   * Sends an answer. On success the form closes, and the card follows the rental, which the Cloud
   * Function has changed. On failure the error shows and the form keeps its content.
   */
  private fun answer(call: suspend () -> Result<Unit>) {
    _uiState.update { it.copy(isAnswering = true, offerError = null) }
    viewModelScope.launch {
      call()
          .onSuccess { _uiState.update { it.copy(isAnswering = false, isCountering = false) } }
          .onFailure { e ->
            _uiState.update { it.copy(isAnswering = false, offerError = e.message ?: "") }
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
      launch { observeOffer(uid) }
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
            markAsRead()
          }
    }
  }

  /**
   * Saves that the user has seen the messages on screen, each time they change while the chat is
   * open, so a message received meanwhile does not leave a red dot in the chat list. Launched on
   * its own, since offline the save waits up to 10 seconds and must not hold back the next
   * messages. A failure is ignored: it only leaves the dot, which is not worth an error on the
   * chat.
   */
  private fun markAsRead() {
    viewModelScope.launch { chatRepository.markAsRead(rentalId) }
  }

  /**
   * Follows the offer and the pickup date of the rental. Without them the conversation still works,
   * so a read error only hides the card and the pickup. The form closes if the offer it answers is
   * gone.
   */
  private suspend fun observeOffer(uid: String) {
    combine(
            rentalRepository.observeRental(rentalId).catch { emit(null) },
            rentalRepository.observeRentalsAsLender(uid).catch { emit(emptyList()) },
        ) { rental, lent ->
          rental to rental?.toChatOffer(uid, lent)
        }
        .collect { (rental, offer) ->
          _uiState.update {
            it.copy(
                offer = offer,
                isCountering = it.isCountering && offer?.canCounter == true,
                pickupDate = rental?.startDate?.takeIf { rental.status == RentalStatus.ACCEPTED },
            )
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
      private val rentalRepository: RentalRepository,
      private val connectivityRepository: ConnectivityRepository,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(ChatViewModel::class.java)) {
        "Unknown ViewModel class: ${modelClass.name}"
      }
      @Suppress("UNCHECKED_CAST")
      return ChatViewModel(
          rentalId,
          chatRepository,
          authRepository,
          rentalRepository,
          connectivityRepository,
      )
          as T
    }
  }

  private companion object {
    /** The offer of this rental for the user [uid], who lends the rentals [lent]. */
    fun Rental.toChatOffer(uid: String, lent: List<Rental>): ChatOffer? = offer?.let {
      ChatOffer(
          amount = it.amount,
          previousAmount = it.previousAmount,
          isMine = it.authorId == uid,
          isCounter = it.isCounter,
          expiresAt = it.expiresAt,
          overlappingRequests = lent.count { other -> other.isDeclinedByAccepting(this) },
      )
    }

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
