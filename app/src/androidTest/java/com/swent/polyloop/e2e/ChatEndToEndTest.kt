// Made with Claude.

package com.swent.polyloop.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.swent.polyloop.model.chat.ChatFields
import com.swent.polyloop.model.chat.MessageType
import com.swent.polyloop.model.rental.RentalFields
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.navigation.Screen
import com.swent.polyloop.utils.FirebaseEmulator
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Test
import org.junit.runner.RunWith

/** The conversation of a rental, between the signed-in borrower and the lender. */
@RunWith(AndroidJUnit4::class)
class ChatEndToEndTest : EndToEndTestBase() {

  @Test
  fun messagesGoBothWaysAndTheChatListMarksAnUnreadAnswer() {
    seedProfile(lenderId, LENDER_NAME)
    val listing = seedListing()
    val uid = signInWithoutTheUi()
    val rentalId =
        seedRental(
            listing,
            uid,
            USER_NAME,
            LENDER_NAME,
            firstDay,
            firstDay.plusDays(1),
            RentalStatus.REQUESTED,
        )
    setApp()
    val conversation = C.Tag.chat_list_row_ + rentalId

    openFromMenu(Screen.ChatList)
    waitForTag(conversation)
    composeTestRule.onNodeWithTag(conversation).performClick()
    waitForText(C.Tag.chat_header_item, listing.title)

    // Sending: the message shows in the conversation, and the field empties.
    composeTestRule.onNodeWithTag(C.Tag.chat_input).performTextInput(SENT)
    composeTestRule.onNodeWithTag(C.Tag.chat_send_button).performClick()
    waitForMessage(SENT)
    composeTestRule.onNodeWithTag(C.Tag.chat_send_button).assertIsNotEnabled()

    // The chat list shows the message the app sent as the last one, without a red dot: the user's
    // own message is never unread.
    pressBack()
    assertRoute(Screen.ChatList)
    waitFor(lastMessageOf(conversation, SENT))
    unreadDot(conversation).assertDoesNotExist()

    // The lender answers from their phone, whose app writes the message and the rental's last
    // message as this one does: the chat list shows it with a red dot. It is sent just after the
    // user last read the chat, by the server's clock, which may differ from the phone's.
    val sentAt = timestamp(readTimeOfSent(rentalId, uid).plusMillis(1))
    FirebaseEmulator.createDocumentAsAdmin(
        "${RentalFields.COLLECTION}/$rentalId/${ChatFields.MESSAGES}/${UUID.randomUUID()}",
        mapOf(
            ChatFields.SENDER_ID to lenderId,
            ChatFields.TEXT to RECEIVED,
            ChatFields.SENT_AT to sentAt,
            ChatFields.TYPE to MessageType.USER.name,
        ),
    )
    FirebaseEmulator.updateDocumentAsAdmin(
        "${RentalFields.COLLECTION}/$rentalId",
        mapOf(RentalFields.LAST_MESSAGE to RECEIVED, RentalFields.LAST_MESSAGE_AT to sentAt),
    )
    waitFor(lastMessageOf(conversation, RECEIVED))
    waitFor(hasTestTag(C.Tag.chat_list_row_unread) and hasAnyAncestor(hasTestTag(conversation)))

    // Opening the chat shows both messages and marks it read: back on the list, the dot is gone.
    composeTestRule.onNodeWithTag(conversation).performClick()
    waitForMessage(SENT)
    waitForMessage(RECEIVED)
    pressBack()
    assertRoute(Screen.ChatList)
    // The row first, so a list still loading cannot pass for one without a dot.
    waitFor(lastMessageOf(conversation, RECEIVED))
    composeTestRule.waitUntil(TIMEOUT_MS) {
      composeTestRule
          .onAllNodes(
              hasTestTag(C.Tag.chat_list_row_unread) and hasAnyAncestor(hasTestTag(conversation)),
              useUnmergedTree = true,
          )
          .fetchSemanticsNodes()
          .isEmpty()
    }
  }

  /** The red dot of the chat list row [rowTag]. */
  private fun unreadDot(rowTag: String) = inNode(rowTag, C.Tag.chat_list_row_unread)

  /**
   * When [uid] last read the chat of [rentalId], as the server saved it with the message [SENT].
   * The chat list shows that message from the phone's cache before the server confirms it, so this
   * waits for the server to have both.
   */
  private fun readTimeOfSent(rentalId: String, uid: String): Instant {
    var readAt: Instant? = null
    composeTestRule.waitUntil(TIMEOUT_MS) {
      val rental = runBlocking {
        Firebase.firestore
            .collection(RentalFields.COLLECTION)
            .document(rentalId)
            .get(Source.SERVER)
            .await()
      }
      val readTimes = rental.get(ChatFields.LAST_READ_AT) as? Map<*, *>
      readAt =
          (readTimes?.get(uid) as? Timestamp)?.toInstant()?.takeIf {
            rental.getString(RentalFields.LAST_MESSAGE) == SENT
          }
      readAt != null
    }
    return readAt!!
  }

  /** The last message [text] shown on the chat list row [rowTag]. */
  private fun lastMessageOf(rowTag: String, text: String) =
      hasTestTag(C.Tag.chat_list_row_message) and
          hasText(text) and
          hasAnyAncestor(hasTestTag(rowTag))

  private fun waitForMessage(text: String) =
      waitFor(hasText(text) and hasAnyAncestor(hasTestTag(C.Tag.chat_messages)))

  private companion object {
    const val SENT = "Can we meet at the Satellite?"
    const val RECEIVED = "Sure, see you there."
  }
}
