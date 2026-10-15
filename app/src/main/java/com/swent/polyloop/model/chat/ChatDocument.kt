// Made with Claude.

package com.swent.polyloop.model.chat

import com.google.firebase.Timestamp
import com.swent.polyloop.model.rental.RentalStatus
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Firestore names for chat, from the schema in #94, kept in one place so they are easy to change.
 */
internal object ChatFields {
  // rentals/{rentalId}: the fields the chat list reads and sendMessage updates
  const val RENTALS = "rentals"
  const val LISTING_TITLE = "listingTitle"
  const val OWNER_ID = "ownerId"
  const val OWNER_NAME = "ownerName"
  const val BORROWER_ID = "borrowerId"
  const val BORROWER_NAME = "borrowerName"
  const val PARTICIPANTS = "participants"
  const val LAST_MESSAGE = "lastMessage"
  const val LAST_MESSAGE_AT = "lastMessageAt"
  const val STATUS = "status"
  const val START_DATE = "startDate"
  const val END_DATE = "endDate"

  // rentals/{rentalId}/messages/{messageId}
  const val MESSAGES = "messages"
  const val SENDER_ID = "senderId"
  const val TEXT = "text"
  const val SENT_AT = "sentAt"
  const val TYPE = "type"
}

/**
 * Builds a [Message] from a Firestore document, or returns null if a field is missing or wrong. A
 * blank text counts as wrong: the app never sends one, so it is hidden instead of shown empty.
 */
internal fun messageFromDocument(id: String, data: Map<String, Any?>): Message? {
  return Message(
      id = id,
      senderId = data[ChatFields.SENDER_ID] as? String ?: return null,
      text = (data[ChatFields.TEXT] as? String)?.takeIf { it.isNotBlank() } ?: return null,
      sentAt = instantOrNull(data[ChatFields.SENT_AT]) ?: return null,
      type = MessageType.entries.firstOrNull { it.name == data[ChatFields.TYPE] } ?: return null,
  )
}

/**
 * Builds a [Conversation] from a rental document, or returns null if a field is missing or wrong.
 * The last message defaults to empty, since a new rental has no message yet.
 */
internal fun conversationFromDocument(id: String, data: Map<String, Any?>): Conversation? {
  return Conversation(
      rentalId = id,
      listingTitle = data[ChatFields.LISTING_TITLE] as? String ?: return null,
      ownerId = data[ChatFields.OWNER_ID] as? String ?: return null,
      ownerName = data[ChatFields.OWNER_NAME] as? String ?: return null,
      borrowerId = data[ChatFields.BORROWER_ID] as? String ?: return null,
      borrowerName = data[ChatFields.BORROWER_NAME] as? String ?: return null,
      lastMessage = data[ChatFields.LAST_MESSAGE] as? String ?: "",
      lastMessageAt = instantOrNull(data[ChatFields.LAST_MESSAGE_AT]) ?: return null,
      status =
          RentalStatus.entries.firstOrNull { it.name == data[ChatFields.STATUS] } ?: return null,
      startDate = dateOrNull(data[ChatFields.START_DATE]) ?: return null,
      endDate = dateOrNull(data[ChatFields.END_DATE]) ?: return null,
  )
}

/** Firestore returns its date-times as [Timestamp]; anything else (e.g. text) is rejected. */
private fun instantOrNull(value: Any?): Instant? = (value as? Timestamp)?.toInstant()

/** Dates without a time are stored as `YYYY-MM-DD` text; anything else is rejected. */
private fun dateOrNull(value: Any?): LocalDate? =
    try {
      (value as? String)?.let(LocalDate::parse)
    } catch (e: DateTimeParseException) {
      null
    }
