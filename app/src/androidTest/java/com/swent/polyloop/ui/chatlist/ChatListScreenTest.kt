// Made with Claude.

package com.swent.polyloop.ui.chatlist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.auth.AuthResult
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.chat.Conversation
import com.swent.polyloop.model.chat.FakeChatRepository
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.theme.PolyLoopTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChatListScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  // The expected times are built with formatMessageTime too, so the tests pass in any time zone.
  private val now = Instant.parse("2026-10-13T12:00:00Z")

  private val tent =
      ChatListRow("tent", "Ana", "Camping tent", "See you at 5?", now, RentalStatus.ACCEPTED)
  private val drill =
      ChatListRow(
          "drill",
          "",
          "Electric drill",
          "",
          now.minusSeconds(3 * 86_400),
          RentalStatus.REQUESTED,
      )

  private var clicked: String? = null
  private var retries = 0

  private fun setContent(uiState: ChatListUiState) {
    composeTestRule.setContent {
      PolyLoopTheme {
        ChatListContent(
            uiState = uiState,
            onConversationClick = { clicked = it },
            onRetry = { retries++ },
            now = now,
        )
      }
    }
  }

  private fun rowNode(rentalId: String, tag: String) =
      composeTestRule.onNode(
          hasTestTag(tag) and hasAnyAncestor(hasTestTag(C.Tag.chat_list_row_ + rentalId)),
          useUnmergedTree = true,
      )

  @Test
  fun loadingShowsOnlyTheSpinner() {
    setContent(ChatListUiState(isLoading = true))

    composeTestRule.onNodeWithTag(C.Tag.chat_list_title).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.chat_list_loading).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.chat_list_list).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.chat_list_empty).assertDoesNotExist()
  }

  @Test
  fun errorShowsItsMessageAndRetryCallsBack() {
    setContent(ChatListUiState(isLoading = false, errorMsg = "offline"))

    composeTestRule.onNodeWithTag(C.Tag.chat_list_error).assertTextEquals("offline")
    composeTestRule.onNodeWithTag(C.Tag.chat_list_retry_button).performClick()
    assertEquals(1, retries)
  }

  @Test
  fun blankErrorShowsTheGenericText() {
    setContent(ChatListUiState(isLoading = false, errorMsg = ""))

    composeTestRule
        .onNodeWithTag(C.Tag.chat_list_error)
        .assertTextEquals(context.getString(R.string.chat_list_error_fallback))
  }

  @Test
  fun noConversationsShowsTheEmptyState() {
    setContent(ChatListUiState(isLoading = false))

    composeTestRule
        .onNodeWithTag(C.Tag.chat_list_empty)
        .assertTextEquals(context.getString(R.string.chat_list_empty))
    composeTestRule.onNodeWithTag(C.Tag.chat_list_list).assertDoesNotExist()
  }

  @Test
  fun rowShowsThePersonTheItemTheLastMessageAndTheTime() {
    setContent(ChatListUiState(rows = listOf(tent), isLoading = false))

    rowNode("tent", C.Tag.chat_list_row_avatar).assertIsDisplayed()
    rowNode("tent", C.Tag.chat_list_row_name).assertTextEquals("Ana")
    rowNode("tent", C.Tag.chat_list_row_item).assertTextEquals("Camping tent")
    rowNode("tent", C.Tag.chat_list_row_message).assertTextEquals("See you at 5?")
    rowNode("tent", C.Tag.chat_list_row_time).assertTextEquals(formatMessageTime(now, now))
  }

  @Test
  fun blankNameAndNoMessageShowPlaceholders() {
    setContent(ChatListUiState(rows = listOf(drill), isLoading = false))

    rowNode("drill", C.Tag.chat_list_row_name)
        .assertTextEquals(context.getString(R.string.chat_list_unknown_person))
    rowNode("drill", C.Tag.chat_list_row_message)
        .assertTextEquals(context.getString(R.string.chat_list_no_messages))
    rowNode("drill", C.Tag.chat_list_row_time)
        .assertTextEquals(formatMessageTime(drill.lastMessageAt, now))
  }

  @Test
  fun tappingARowOpensItsConversation() {
    setContent(ChatListUiState(rows = listOf(tent, drill), isLoading = false))

    composeTestRule.onNodeWithTag(C.Tag.chat_list_row_ + "drill").performClick()

    assertEquals("drill", clicked)
  }

  @Test
  fun screenShowsTheViewModelsConversations() {
    val chat = FakeChatRepository(currentUid = "me")
    chat.conversations.value =
        listOf(
            Conversation(
                "tent",
                "Camping tent",
                "ana",
                "Ana",
                "me",
                "Me",
                "Hi",
                now,
                RentalStatus.ACCEPTED,
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

    val viewModel = ChatListViewModel(chat, auth)

    composeTestRule.setContent {
      PolyLoopTheme { ChatListScreen(viewModel, onConversationClick = { clicked = it }) }
    }

    rowNode("tent", C.Tag.chat_list_row_name).assertTextEquals("Ana")
    composeTestRule.onNodeWithTag(C.Tag.chat_list_row_ + "tent").performClick()
    assertEquals("tent", clicked)
  }
}
