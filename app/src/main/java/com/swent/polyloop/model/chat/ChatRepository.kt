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
   * The messages of the rental [rentalId], oldest first (the newest is shown at the bottom). Emits
   * again whenever a message is added. Empty for an unknown rental. The flow fails with the cause
   * if they cannot be loaded.
   */
  fun observeMessages(rentalId: String): Flow<List<Message>>

  /**
   * Sends [text] as the signed-in user, and makes it the rental's last message in the chat list.
   * Fails with an [IllegalArgumentException] if [text] is blank or [rentalId] is invalid, an
   * [IllegalStateException] if nobody is signed in, or the cause if it cannot be written (e.g. the
   * rental does not exist). Nothing is written when it fails.
   */
  suspend fun sendMessage(rentalId: String, text: String): Result<Unit>
}
