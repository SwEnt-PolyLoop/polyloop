// Made with Claude.

package com.swent.polyloop.model.chat

import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [ChatRepository] for tests and previews; the app itself never uses it. It lives in
 * `main` so that unit tests, Compose tests and `@Preview`s can all share it.
 *
 * Tests change [conversations] and [messages] to make the flows emit, like Firestore would.
 * [sendMessage] behaves like the real repository: it checks the text, the signed-in user and that
 * the rental exists, then adds the message and updates the conversation.
 */
class FakeChatRepository(
    // The signed-in user who sends messages; null when nobody is signed in.
    var currentUid: String? = "u1",
    // When set, the flows and sendMessage fail with this error.
    var failure: Throwable? = null,
    // When set, sendMessage waits until it is completed, so tests can see the "sending" state.
    var gate: CompletableDeferred<Unit>? = null,
    // The time given to the next sent message.
    var now: Instant = Instant.parse("2026-10-13T15:00:00Z"),
) : ChatRepository {

  // Every rental's conversation, in any order.
  val conversations = MutableStateFlow<List<Conversation>>(emptyList())

  // The messages of each rental, by rental id, oldest first.
  val messages = MutableStateFlow<Map<String, List<Message>>>(emptyMap())

  // How many times sendMessage was called, including the calls that failed.
  var sendMessageCalls = 0
    private set

  override fun observeConversations(uid: String): Flow<List<Conversation>> =
      failing()
          ?: conversations.map { all ->
            all.filter { it.ownerId == uid || it.borrowerId == uid }
                .sortedByDescending { it.lastMessageAt }
          }

  override suspend fun getConversation(rentalId: String): Result<Conversation?> =
      failure?.let { Result.failure(it) }
          ?: Result.success(conversations.value.find { it.rentalId == rentalId })

  override fun observeMessages(rentalId: String): Flow<List<Message>> =
      failing() ?: messages.map { it[rentalId].orEmpty() }

  override suspend fun sendMessage(rentalId: String, text: String): Result<Unit> {
    sendMessageCalls++
    gate?.await()
    failure?.let {
      return Result.failure(it)
    }
    if (text.isBlank()) return Result.failure(IllegalArgumentException("Blank message"))
    val uid = currentUid ?: return Result.failure(IllegalStateException("Nobody is signed in"))
    // Like Firestore, which refuses to update a rental that does not exist.
    if (conversations.value.none { it.rentalId == rentalId }) {
      return Result.failure(NoSuchElementException("No rental $rentalId"))
    }

    messages.update { all ->
      val sent = all[rentalId].orEmpty()
      val message = Message("m${sent.size + 1}", uid, text, now, MessageType.USER)
      all + (rentalId to sent + message)
    }
    conversations.update { all ->
      all.map {
        if (it.rentalId == rentalId) it.copy(lastMessage = text, lastMessageAt = now) else it
      }
    }
    return Result.success(Unit)
  }

  /** A flow that fails with [failure], or null when no failure is set. */
  private fun <T> failing(): Flow<T>? = failure?.let { error -> flow { throw error } }
}
