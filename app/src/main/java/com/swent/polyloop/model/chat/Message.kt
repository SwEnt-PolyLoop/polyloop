// Made with Claude.

package com.swent.polyloop.model.chat

import java.time.Instant

/** One message in a rental's conversation. */
data class Message(
    val id: String,
    val senderId: String,
    val text: String,
    val sentAt: Instant,
    val type: MessageType,
)

enum class MessageType {
  /** Written by the lender or the borrower in the chat. */
  USER,
  /** Written by a Cloud Function, e.g. when the rental is accepted (later sprint). */
  SYSTEM,
}
