// Made with Claude.

package com.swent.polyloop.model.chat

import kotlinx.coroutines.flow.Flow

/** The conversations of rentals: each rental has exactly one, between its lender and borrower. */
interface ChatRepository {

  /**
   * The conversations of every rental [uid] takes part in, newest [Conversation.lastMessageAt]
   * first. Emits again whenever one changes. The flow fails with the cause if they cannot be
   * loaded.
   */
  fun observeConversations(uid: String): Flow<List<Conversation>>

  /**
   * The conversation of the rental [rentalId], read once, e.g. for the chat header. Null if there
   * is no such rental (including an invalid id) or its document cannot be read as a conversation.
   * Fails with the cause if it cannot be loaded.
   */
  suspend fun getConversation(rentalId: String): Result<Conversation?>

  /**
   * The messages of the rental [rentalId], oldest first (the newest is shown at the bottom). Emits
   * again whenever a message is added. Empty for an unknown rental. The flow fails with the cause
   * if they cannot be loaded.
   */
  fun observeMessages(rentalId: String): Flow<List<Message>>

  /**
   * Sends [text] as the signed-in user, and makes it the rental's last message in the chat list.
   * Fails with an [IllegalArgumentException] if [text] is blank or [rentalId] is invalid, an
   * [IllegalStateException] if nobody is signed in, or the cause if it cannot be written (e.g. the
   * rental does not exist). Nothing is written when it fails. If the server does not confirm the
   * write within 10 seconds (offline), the message stays queued and is sent later, so the result is
   * a success: a message the server rejects afterwards is not reported. The team has not yet
   * decided whether this is the right behaviour.
   */
  suspend fun sendMessage(rentalId: String, text: String): Result<Unit>
}
