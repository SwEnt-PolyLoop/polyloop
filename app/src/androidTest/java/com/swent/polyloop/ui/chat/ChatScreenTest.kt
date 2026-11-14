// Made with Claude.

package com.swent.polyloop.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.auth.AuthResult
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.chat.Conversation
import com.swent.polyloop.model.chat.FakeChatRepository
import com.swent.polyloop.model.chat.Message
import com.swent.polyloop.model.chat.MessageType
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.rental.FakeRentalRepository
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
  private var pickupClicks = 0
  private var reviewRequestClicks = 0

  // The day the screen takes as today; a test can change it after setContent and the screen
  // follows.
  private var today by mutableStateOf(start)

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
            today = today,
            onStartPickupClick = { pickupClicks++ },
            onReviewRequestClick = { reviewRequestClicks++ },
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

  // ---------- keyboard ----------

  @Test
  fun theNewestMessageStaysInViewWhenTheScreenGetsShorter() {
    val many =
        List(15) {
          ChatMessageRow(
              "n$it",
              "Message $it",
              now,
              if (it % 2 == 0) MessageKind.THEIRS else MessageKind.MINE,
          )
        }
    var height by mutableStateOf(800.dp)
    composeTestRule.setContent {
      PolyLoopTheme {
        Box(Modifier.height(height)) {
          ChatContent(
              uiState = loaded.copy(messages = many),
              onDraftChange = {},
              onSend = {},
              onRetry = {},
              onRentalDetailsClick = {},
              now = now,
          )
        }
      }
    }
    composeTestRule.onNodeWithTag(C.Tag.chat_message_ + "n14").assertIsDisplayed()

    height = 400.dp // as when the keyboard opens

    composeTestRule.onNodeWithTag(C.Tag.chat_message_ + "n14").assertIsDisplayed()
  }

  // ---------- review request ----------

  @Test
  fun theLenderOfARequestWithNoOfferIsLedToReviewRequest() {
    setContent(loaded.copy(canReviewRequest = true))

    composeTestRule.onNodeWithTag(C.Tag.chat_review_request_bar).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(C.Tag.chat_review_request_button)
        .assertTextEquals(context.getString(R.string.my_rentals_review_request))
        .performClick()
    assertEquals(1, reviewRequestClicks)
  }

  @Test
  fun theReviewRequestBarIsHiddenOtherwiseAndWhileLoading() {
    var state by mutableStateOf(loaded)
    composeTestRule.setContent {
      PolyLoopTheme {
        ChatContent(
            uiState = state,
            onDraftChange = {},
            onSend = {},
            onRetry = {},
            onRentalDetailsClick = {},
            now = now,
            today = today,
        )
      }
    }
    composeTestRule.onNodeWithTag(C.Tag.chat_review_request_bar).assertDoesNotExist()

    state = ChatUiState(canReviewRequest = true)
    composeTestRule.onNodeWithTag(C.Tag.chat_review_request_bar).assertDoesNotExist()
  }

  // ---------- pickup ----------

  @Test
  fun onThePickupDayTheBarShowsAndItsButtonStartsThePickup() {
    setContent(loaded.copy(pickupDate = start))

    composeTestRule.onNodeWithTag(C.Tag.chat_pickup_bar).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(C.Tag.chat_start_pickup_button)
        .assertTextEquals(context.getString(R.string.chat_start_pickup))
        .performClick()
    assertEquals(1, pickupClicks)
  }

  @Test
  fun theBarShowsOnlyOnThePickupDayItself() {
    today = start.minusDays(1)
    setContent(loaded.copy(pickupDate = start))
    composeTestRule.onNodeWithTag(C.Tag.chat_pickup_bar).assertDoesNotExist()

    today = start.plusDays(1)
    composeTestRule.onNodeWithTag(C.Tag.chat_pickup_bar).assertDoesNotExist()

    today = start
    composeTestRule.onNodeWithTag(C.Tag.chat_pickup_bar).assertIsDisplayed()
  }

  @Test
  fun aRentalThatIsNotAcceptedHasNoBar() {
    setContent(loaded)

    composeTestRule.onNodeWithTag(C.Tag.chat_pickup_bar).assertDoesNotExist()
  }

  @Test
  fun theBarIsHiddenWhileTheChatLoads() {
    setContent(loaded.copy(pickupDate = start, isLoading = true))

    composeTestRule.onNodeWithTag(C.Tag.chat_loading).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.chat_pickup_bar).assertDoesNotExist()
  }

  @Test
  fun theBarIsHiddenWhenTheChatCannotBeLoaded() {
    setContent(loaded.copy(pickupDate = start, errorMsg = "Network error"))

    composeTestRule.onNodeWithTag(C.Tag.chat_error).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.chat_pickup_bar).assertDoesNotExist()
  }

  @Test
  fun theStartPickupButtonStaysEnabledOfflineUnderTheHeader() {
    setContent(loaded.copy(pickupDate = start, isOffline = true))

    composeTestRule.onNodeWithTag(C.Tag.chat_start_pickup_button).assertIsEnabled()
    assertTrue(bounds(C.Tag.chat_pickup_bar).top >= bounds(C.Tag.chat_header_item).bottom)
    assertTrue(bounds(C.Tag.chat_pickup_bar).bottom <= bounds(C.Tag.chat_messages).top)
  }

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
  fun typingAndSendCallBack() {
    // Holds the draft like the ViewModel does, so the field shows what is typed.
    composeTestRule.setContent {
      var draft by remember { mutableStateOf("") }
      PolyLoopTheme {
        ChatContent(
            uiState = loaded.copy(draft = draft),
            onDraftChange = {
              draft = it
              drafts += it
            },
            onSend = { sends++ },
            onRetry = {},
            onRentalDetailsClick = {},
            now = now,
        )
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.chat_input).performTextInput("Hello")
    composeTestRule.onNodeWithTag(C.Tag.chat_send_button).assertIsEnabled().performClick()

    assertEquals("Hello", drafts.last())
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

  // I borrow Ana's tent.
  private val tent =
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

  private fun realViewModel(chat: FakeChatRepository): ChatViewModel {
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
    return ChatViewModel("tent", chat, auth, FakeRentalRepository(), FakeConnectivityRepository())
  }

  @Test
  fun sentMessageAppearsOnTheRight() {
    val chat = FakeChatRepository(currentUid = "me", now = now)
    chat.conversations = listOf(tent)
    val viewModel = realViewModel(chat)

    composeTestRule.setContent { PolyLoopTheme { ChatScreen(viewModel) } }
    composeTestRule.onNodeWithTag(C.Tag.chat_input).performTextInput("See you at 5?")
    composeTestRule.onNodeWithTag(C.Tag.chat_send_button).performClick()

    inMessage("m1", C.Tag.chat_message_text).assertTextEquals("See you at 5?")
    assertEquals("", viewModel.uiState.value.draft)
    composeTestRule.onNodeWithTag(C.Tag.chat_send_button).assertIsNotEnabled()
  }

  @Test
  fun chatIsMarkedAsReadOnlyWhileItIsVisible() {
    val hi = Message("m1", "ana", "Hi", now, MessageType.USER)
    val chat = FakeChatRepository(currentUid = "me", now = now)
    chat.conversations = listOf(tent)
    chat.messages = mapOf("tent" to listOf(hi))
    val viewModel = realViewModel(chat)
    val owner =
        object : LifecycleOwner {
          override val lifecycle = LifecycleRegistry(this)
        }
    composeTestRule.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
    composeTestRule.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides owner) {
        PolyLoopTheme { ChatScreen(viewModel) }
      }
    }
    composeTestRule.runOnIdle { assertEquals(1, chat.markAsReadCalls) }

    // Hidden, like the app in the background: Ana's new message stays unread.
    composeTestRule.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.CREATED }
    composeTestRule.runOnIdle {
      chat.messages =
          mapOf("tent" to listOf(hi, Message("m2", "ana", "Ok?", now, MessageType.USER)))
    }
    composeTestRule.runOnIdle { assertEquals(1, chat.markAsReadCalls) }

    // Visible again: the user now sees it.
    composeTestRule.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
    composeTestRule.runOnIdle { assertEquals(2, chat.markAsReadCalls) }
  }
}
