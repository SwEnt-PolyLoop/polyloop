// Made with Claude.

package com.swent.polyloop.model.chat

import com.google.firebase.Timestamp
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatDocumentTest {

  private val sentAt = Instant.parse("2026-10-13T15:02:00Z")
  private val timestamp = Timestamp(sentAt.epochSecond, sentAt.nano)

  // ---------- messageFromDocument ----------

  private val messageData: Map<String, Any?> =
      mapOf(
          ChatFields.SENDER_ID to "u1",
          ChatFields.TEXT to "See you at 5?",
          ChatFields.SENT_AT to timestamp,
          ChatFields.TYPE to "USER",
      )

  private val expectedMessage = Message("m1", "u1", "See you at 5?", sentAt, MessageType.USER)

  private fun message(data: Map<String, Any?>) = messageFromDocument("m1", data)

  @Test fun validMessageIsMapped() = assertEquals(expectedMessage, message(messageData))

  @Test
  fun systemMessageIsMapped() =
      assertEquals(
          expectedMessage.copy(type = MessageType.SYSTEM),
          message(messageData + (ChatFields.TYPE to "SYSTEM")),
      )

  @Test
  fun messageWithoutARequiredFieldIsRejected() {
    for (field in messageData.keys) {
      assertNull("Missing $field", message(messageData - field))
      assertNull("Null $field", message(messageData + (field to null)))
    }
  }

  @Test
  fun unknownMessageTypeIsRejected() = assertNull(message(messageData + (ChatFields.TYPE to "BOT")))

  @Test
  fun lowercaseMessageTypeIsRejected() =
      assertNull(message(messageData + (ChatFields.TYPE to "user")))

  @Test
  fun sentAtStoredAsTextIsRejected() =
      assertNull(message(messageData + (ChatFields.SENT_AT to "2026-10-13T15:02:00Z")))

  @Test
  fun sentAtStoredAsNumberIsRejected() =
      assertNull(message(messageData + (ChatFields.SENT_AT to 1_760_367_720_000L)))

  @Test fun nonTextMessageIsRejected() = assertNull(message(messageData + (ChatFields.TEXT to 42L)))

  @Test fun emptyTextIsRejected() = assertNull(message(messageData + (ChatFields.TEXT to "")))

  @Test
  fun whitespaceOnlyTextIsRejected() =
      assertNull(message(messageData + (ChatFields.TEXT to " \n\t ")))

  @Test
  fun textWithSurroundingSpacesIsKeptAsIs() =
      assertEquals(
          expectedMessage.copy(text = "  hi  "),
          message(messageData + (ChatFields.TEXT to "  hi  ")),
      )

  @Test
  fun subSecondPrecisionIsKept() {
    val precise = Instant.parse("2026-10-13T15:02:00.123456789Z")
    assertEquals(
        precise,
        message(messageData + (ChatFields.SENT_AT to Timestamp(precise.epochSecond, precise.nano)))
            ?.sentAt,
    )
  }

  @Test
  fun extraFieldsAreIgnored() =
      assertEquals(expectedMessage, message(messageData + ("reactions" to listOf("+1"))))

  // ---------- conversationFromDocument ----------

  private val rentalData: Map<String, Any?> =
      mapOf(
          ChatFields.LISTING_TITLE to "Camping tent",
          ChatFields.OWNER_ID to "u1",
          ChatFields.OWNER_NAME to "Ana",
          ChatFields.BORROWER_ID to "u2",
          ChatFields.BORROWER_NAME to "Ben",
          ChatFields.PARTICIPANTS to listOf("u1", "u2"),
          ChatFields.LAST_MESSAGE to "See you at 5?",
          ChatFields.LAST_MESSAGE_AT to timestamp,
          // Rental fields the chat list does not use
          "status" to "REQUESTED",
          "pricePerDay" to 15L,
      )

  private val expectedConversation =
      Conversation("r1", "Camping tent", "u1", "Ana", "u2", "Ben", "See you at 5?", sentAt)

  private fun conversation(data: Map<String, Any?>) = conversationFromDocument("r1", data)

  @Test fun validRentalIsMapped() = assertEquals(expectedConversation, conversation(rentalData))

  @Test
  fun rentalWithoutARequiredFieldIsRejected() {
    val required =
        listOf(
            ChatFields.LISTING_TITLE,
            ChatFields.OWNER_ID,
            ChatFields.OWNER_NAME,
            ChatFields.BORROWER_ID,
            ChatFields.BORROWER_NAME,
            ChatFields.LAST_MESSAGE_AT,
        )
    for (field in required) {
      assertNull("Missing $field", conversation(rentalData - field))
      assertNull("Null $field", conversation(rentalData + (field to null)))
    }
  }

  @Test
  fun rentalWithoutAMessageYetHasAnEmptyLastMessage() =
      assertEquals(
          expectedConversation.copy(lastMessage = ""),
          conversation(rentalData - ChatFields.LAST_MESSAGE),
      )

  @Test
  fun nonTextLastMessageIsTreatedAsEmpty() =
      assertEquals(
          expectedConversation.copy(lastMessage = ""),
          conversation(rentalData + (ChatFields.LAST_MESSAGE to 3L)),
      )

  @Test
  fun lastMessageAtStoredAsTextIsRejected() =
      assertNull(conversation(rentalData + (ChatFields.LAST_MESSAGE_AT to "2026-10-13")))

  @Test
  fun nonTextNameIsRejected() =
      assertNull(conversation(rentalData + (ChatFields.OWNER_NAME to listOf("Ana"))))

  @Test
  fun participantsAreNotNeededToMapTheRow() =
      assertEquals(expectedConversation, conversation(rentalData - ChatFields.PARTICIPANTS))
}
