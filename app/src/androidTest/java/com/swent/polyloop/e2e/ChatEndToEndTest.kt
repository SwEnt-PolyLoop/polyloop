// Made with Claude.

package com.swent.polyloop.e2e

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.model.chat.ChatFields
import com.swent.polyloop.model.chat.MessageType
import com.swent.polyloop.model.rental.RentalFields
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.navigation.Screen
import com.swent.polyloop.utils.FirebaseEmulator
import java.time.Instant
import java.util.UUID
import org.junit.Test
import org.junit.runner.RunWith

/** The conversation of a rental, between the signed-in borrower and the lender. */
@RunWith(AndroidJUnit4::class)
class ChatEndToEndTest : EndToEndTestBase() {

  @Test
  fun messagesGoBothWaysAndTheChatListShowsTheLastOne() {
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

    // The chat list shows the message the app sent as the last one.
    pressBack()
    assertRoute(Screen.ChatList)
    waitFor(lastMessageOf(conversation, SENT))

    // The lender answers from their phone, whose app writes the message and the rental's last
    // message as this one does: both the chat list and the open chat follow.
    val sentAt = timestamp(Instant.now())
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
    composeTestRule.onNodeWithTag(conversation).performClick()
    waitForMessage(SENT)
    waitForMessage(RECEIVED)
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
