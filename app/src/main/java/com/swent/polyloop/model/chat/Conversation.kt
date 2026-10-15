// Made with Claude.

package com.swent.polyloop.model.chat

import com.swent.polyloop.model.rental.RentalStatus
import java.time.Instant
import java.time.LocalDate

/**
 * A rental's conversation, as shown in the chat list and the chat header. Each rental has exactly
 * one conversation, so it is read from the rental's document, which carries the names, the dates
 * and the last message (see "Firestore schema" in docs/ARCHITECTURE.md).
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
    val status: RentalStatus,
    val startDate: LocalDate,
    val endDate: LocalDate, // the return date; both days are part of the rental
) {
  /** The name of the person [myUid] is talking to. */
  fun otherPersonName(myUid: String): String = if (myUid == ownerId) borrowerName else ownerName
}
