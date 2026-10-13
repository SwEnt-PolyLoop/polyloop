// Made with Claude.

package com.swent.polyloop.ui.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.auth.AuthResult
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.chat.Conversation
import com.swent.polyloop.model.chat.FakeChatRepository
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.chatlist.formatMessageTime
import com.swent.polyloop.ui.listingdetail.formatRangeEnds
import com.swent.polyloop.ui.theme.PolyLoopTheme
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  // The expected times are built with formatMessageTime too, so the tests pass in any time zone.
  private val now = Instant.parse("2026-10-13T12:00:00Z")
  private val start = LocalDate.of(2026, 10, 20)
  private val end = LocalDate.of(2026, 10, 22)

  private val header = ChatHeader("Ana", "Camping tent", start, end)
  private val theirs = ChatMessageRow("m1", "Hi! When do you need it?", now, MessageKind.THEIRS)
  private val mine = ChatMessageRow("m2", "Friday", now, MessageKind.MINE)
  private val system = ChatMessageRow("m3", "Request sent", now, MessageKind.SYSTEM)

  private val loaded =
      ChatUiState(header = header, messages = listOf(theirs, mine), isLoading = false)

  private var drafts = mutableListOf<String>()
  private var sends = 0
  private var retries = 0
  private var rentalDetailsClicks = 0

  private fun setContent(uiState: ChatUiState) {
    composeTestRule.setContent {
      PolyLoopTheme {
        ChatContent(
            uiState = uiState,
            onDraftChange = { drafts += it },
            onSend = { sends++ },
            onRetry = { retries++ },
            onRentalDetailsClick = { rentalDetailsClicks++ },
            now = now,
        )
      }
    }
  }

  private fun inMessage(id: String, tag: String) =
      composeTestRule.onNode(
          hasTestTag(tag) and hasAnyAncestor(hasTestTag(C.Tag.chat_message_ + id)),
          useUnmergedTree = true,
      )

  private fun bounds(tag: String) =
      composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

  // ---------- states ----------

  @Test
  fun loadingShowsTheSpinnerWithoutTheInputBar() {
    setContent(ChatUiState(header = header, isLoading = true))

    composeTestRule.onNodeWithTag(C.Tag.chat_loading).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.chat_input).assertDoesNotExist()
  }

  @Test
  fun errorShowsItsMessageAndRetryWithoutTheInputBar() {
    setContent(ChatUiState(isLoading = false, errorMsg = "offline"))

    composeTestRule.onNodeWithTag(C.Tag.chat_error).assertTextEquals("offline")
    composeTestRule.onNodeWithTag(C.Tag.chat_input).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.chat_retry_button).performClick()
    assertEquals(1, retries)
  }

  @Test
  fun blankErrorShowsTheGenericText() {
    setContent(ChatUiState(isLoading = false, errorMsg = ""))

    composeTestRule
        .onNodeWithTag(C.Tag.chat_error)
        .assertTextEquals(context.getString(R.string.chat_error_fallback))
  }

  @Test
  fun chatWithoutMessagesShowsTheEmptyStateAndTheInputBar() {
    setContent(ChatUiState(header = header, isLoading = false))

    composeTestRule
        .onNodeWithTag(C.Tag.chat_empty)
        .assertTextEquals(context.getString(R.string.chat_empty))
    composeTestRule.onNodeWithTag(C.Tag.chat_input).assertIsDisplayed()
  }

  // ---------- header ----------

  @Test
  fun headerShowsThePersonTheDatesAndTheItem() {
    setContent(loaded)

    val (from, to) = formatRangeEnds(start, end)
    composeTestRule.onNodeWithTag(C.Tag.chat_header_name).assertTextEquals("Ana")
    composeTestRule
        .onNodeWithTag(C.Tag.chat_header_dates)
        .assertTextEquals(context.getString(R.string.chat_date_range, from, to))
    composeTestRule.onNodeWithTag(C.Tag.chat_header_item).assertTextEquals("Camping tent")
  }

  @Test
  fun blankNameShowsThePlaceholder() {
    setContent(loaded.copy(header = header.copy(otherPersonName = "")))

    composeTestRule
        .onNodeWithTag(C.Tag.chat_header_name)
        .assertTextEquals(context.getString(R.string.chat_unknown_person))
  }

  @Test
  fun rentalDetailsCallsBack() {
    setContent(loaded)

    composeTestRule
        .onNodeWithTag(C.Tag.chat_rental_details)
        .assertTextEquals(context.getString(R.string.chat_rental_details))
        .performClick()
    assertEquals(1, rentalDetailsClicks)
  }

  // ---------- messages ----------

  @Test
  fun messageShowsItsTextAndTime() {
    setContent(loaded)

    inMessage("m1", C.Tag.chat_message_text).assertTextEquals("Hi! When do you need it?")
    inMessage("m1", C.Tag.chat_message_time).assertTextEquals(formatMessageTime(now, now))
  }

  @Test
  fun myMessagesAreOnTheRightAndTheirsOnTheLeft() {
    setContent(loaded)

    val list = bounds(C.Tag.chat_messages)
    val theirsBubble = bounds(C.Tag.chat_message_ + "m1")
    val mineBubble = bounds(C.Tag.chat_message_ + "m2")
    // Each bubble touches its side of the list (inside its 16 dp padding).
    assertTrue(theirsBubble.left - list.left < list.right - theirsBubble.right)
    assertTrue(list.right - mineBubble.right < mineBubble.left - list.left)
  }

  @Test
  fun systemMessageShowsItsTextAndTimeCentred() {
    setContent(loaded.copy(messages = listOf(system)))

    composeTestRule
        .onNodeWithTag(C.Tag.chat_message_ + "m3")
        .assertTextEquals(
            context.getString(
                R.string.chat_system_message,
                "Request sent",
                formatMessageTime(now, now),
            )
        )
  }

  @Test
  fun chatOpensOnTheNewestMessage() {
    val many = List(40) { ChatMessageRow("m$it", "Message $it", now, MessageKind.THEIRS) }
    setContent(loaded.copy(messages = many))

    composeTestRule.onNodeWithTag(C.Tag.chat_message_ + "m39").assertIsDisplayed()
  }

  // ---------- typing and sending ----------

  @Test
  fun sendIsDisabledWhileTheTextIsBlank() {
    setContent(loaded.copy(draft = "   "))

    composeTestRule.onNodeWithTag(C.Tag.chat_send_button).assertIsNotEnabled()
  }

  @Test
  fun sendIsDisabledWhileAMessageIsOnItsWay() {
    setContent(loaded.copy(draft = "Hello", isSending = true))

    composeTestRule.onNodeWithTag(C.Tag.chat_send_button).assertIsNotEnabled()
  }

  @Test
  fun typingAndSendCallBack() {
    setContent(loaded.copy(draft = "Hello"))

    composeTestRule.onNodeWithTag(C.Tag.chat_input).performTextInput("!")
    composeTestRule.onNodeWithTag(C.Tag.chat_send_button).assertIsEnabled().performClick()

    assertEquals("Hello!", drafts.last())
    assertEquals(1, sends)
  }

  @Test
  fun failedSendShowsTheError() {
    setContent(loaded.copy(draft = "Hello", sendFailed = true))

    composeTestRule
        .onNodeWithTag(C.Tag.chat_send_failed)
        .assertTextEquals(context.getString(R.string.chat_send_failed))
  }

  @Test
  fun noErrorUnderTheInputBarByDefault() {
    setContent(loaded)

    composeTestRule.onNodeWithTag(C.Tag.chat_send_failed).assertDoesNotExist()
  }

  // ---------- with the real ViewModel ----------

  @Test
  fun sentMessageAppearsOnTheRight() {
    val chat = FakeChatRepository(currentUid = "me", now = now)
    chat.conversations.value =
        listOf(
            Conversation(
                "tent",
                "Camping tent",
                "ana",
                "Ana",
                "me",
                "Me",
                "",
                now,
                RentalStatus.REQUESTED,
                start,
                end,
            )
        )
    val auth =
        object : AuthRepository {
          override suspend fun signUp(name: String, email: String, password: String) =
              AuthResult.Success(Unit)

          override suspend fun signIn(email: String, password: String): AuthResult<AuthUser> =
              AuthResult.Success(AuthUser("me", email, "Me"))

          override suspend fun resendVerificationEmail(email: String, password: String) =
              AuthResult.Success(Unit)

          override fun getCurrentUser() = AuthUser("me", "me@epfl.ch", "Me")

          override fun signOut() = Unit
        }
    val viewModel = ChatViewModel("tent", chat, auth)

    composeTestRule.setContent { PolyLoopTheme { ChatScreen(viewModel) } }
    composeTestRule.onNodeWithTag(C.Tag.chat_input).performTextInput("See you at 5?")
    composeTestRule.onNodeWithTag(C.Tag.chat_send_button).performClick()

    inMessage("m1", C.Tag.chat_message_text).assertTextEquals("See you at 5?")
    assertEquals("", viewModel.uiState.value.draft)
    composeTestRule.onNodeWithTag(C.Tag.chat_send_button).assertIsNotEnabled()
  }
}
