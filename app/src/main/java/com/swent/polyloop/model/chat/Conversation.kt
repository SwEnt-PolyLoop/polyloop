// Made with Claude.

package com.swent.polyloop.model.chat

import java.time.Instant

/**
 * One row of the chat list. Each rental has exactly one conversation, so it is read from the
 * rental's document, which carries the names and the last message (see #94).
 */
data class Conversation(
    val rentalId: String,
    val listingTitle: String,
    val ownerId: String,
    val ownerName: String,
    val borrowerId: String,
    val borrowerName: String,
    val lastMessage: String, // empty until the first message is sent
    val lastMessageAt: Instant, // the rental's creation time until the first message is sent
) {
  /** The name of the person [myUid] is talking to. */
  fun otherPersonName(myUid: String): String = if (myUid == ownerId) borrowerName else ownerName
}
