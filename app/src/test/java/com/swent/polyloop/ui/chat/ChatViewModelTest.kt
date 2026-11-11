// Made with Claude.

package com.swent.polyloop.ui.chat

import androidx.lifecycle.ViewModel
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.chat.ChatRepository
import com.swent.polyloop.model.chat.Conversation
import com.swent.polyloop.model.chat.FakeChatRepository
import com.swent.polyloop.model.chat.Message
import com.swent.polyloop.model.chat.MessageType
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.rental.FakeRentalRepository
import com.swent.polyloop.model.rental.RentalStatus
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

  private val me = AuthUser(uid = "me", email = "me@epfl.ch", name = "Me")
  private val at = Instant.parse("2026-10-13T10:00:00Z")

  // I borrow Ana's tent.
  private val tent =
      Conversation(
          rentalId = "tent",
          listingTitle = "Camping tent",
          ownerId = "ana",
          ownerName = "Ana",
          borrowerId = "me",
          borrowerName = "Me",
          lastMessage = "",
          lastMessageAt = at,
          status = RentalStatus.REQUESTED,
          startDate = LocalDate.of(2026, 10, 20),
          endDate = LocalDate.of(2026, 10, 22),
      )

  private fun message(id: String, sender: String, text: String, type: MessageType) =
      Message(id, sender, text, at.plusSeconds(id.removePrefix("m").toLong()), type)

  private val fromAna = message("m1", "ana", "Hi! When do you need it?", MessageType.USER)
  private val fromMe = message("m2", "me", "Friday, if that's ok", MessageType.USER)
  private val fromApp = message("m3", "system", "Request sent", MessageType.SYSTEM)

  private lateinit var chat: FakeChatRepository
  private lateinit var auth: FakeAuthRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    chat = FakeChatRepository(currentUid = "me", now = at.plusSeconds(60))
    chat.conversations = listOf(tent)
    chat.messages = mapOf("tent" to listOf(fromAna, fromMe, fromApp))
    auth = FakeAuthRepository(signedInUser = me)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel(
      repository: ChatRepository = chat,
      rentalId: String = "tent",
      connectivity: FakeConnectivityRepository = FakeConnectivityRepository(),
  ) = ChatViewModel(rentalId, repository, auth, FakeRentalRepository(), connectivity)

  // ---------- loading ----------

  @Test
  fun headerShowsTheOwnerTheItemAndTheDatesToTheBorrower() {
    val state = viewModel().uiState.value

    assertFalse(state.isLoading)
    assertNull(state.errorMsg)
    assertEquals(
        ChatHeader("Ana", "Camping tent", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22)),
        state.header,
    )
  }

  @Test
  fun headerShowsTheBorrowerToTheOwner() {
    auth.signedInUser = AuthUser("ana", "ana@epfl.ch", "Ana")

    assertEquals("Me", viewModel().uiState.value.header?.otherPersonName)
  }

  @Test
  fun messagesAreMineTheirsOrSystemInOrder() {
    assertEquals(
        listOf(
            ChatMessageRow("m1", "Hi! When do you need it?", fromAna.sentAt, MessageKind.THEIRS),
            ChatMessageRow("m2", "Friday, if that's ok", fromMe.sentAt, MessageKind.MINE),
            ChatMessageRow("m3", "Request sent", fromApp.sentAt, MessageKind.SYSTEM),
        ),
        viewModel().uiState.value.messages,
    )
  }

  @Test
  fun systemMessageIsSystemEvenWithMyId() {
    chat.messages = mapOf("tent" to listOf(message("m1", "me", "Accepted", MessageType.SYSTEM)))

    assertEquals(MessageKind.SYSTEM, viewModel().uiState.value.messages.single().kind)
  }

  @Test
  fun newMessageAppearsWithoutReloading() {
    val vm = viewModel()
    val fromAnaAgain = message("m4", "ana", "Great", MessageType.USER)

    chat.messages = mapOf("tent" to listOf(fromAna, fromMe, fromApp, fromAnaAgain))

    assertEquals("Great", vm.uiState.value.messages.last().text)
  }

  @Test
  fun chatWithoutMessagesIsEmptyWithoutError() {
    chat.messages = emptyMap()

    val state = viewModel().uiState.value

    assertFalse(state.isLoading)
    assertNull(state.errorMsg)
    assertTrue(state.messages.isEmpty())
  }

  @Test
  fun staysLoadingUntilTheMessagesArrive() {
    val silent =
        object : ChatRepository by chat {
          override fun observeMessages(rentalId: String): Flow<List<Message>> = MutableSharedFlow()
        }

    val state = viewModel(silent).uiState.value

    assertTrue(state.isLoading)
    assertEquals("Camping tent", state.header?.itemTitle)
  }

  // ---------- errors ----------

  @Test
  fun signedOutShowsAnErrorWithoutLoading() {
    auth.signedInUser = null

    val state = viewModel().uiState.value

    assertFalse(state.isLoading)
    assertEquals("", state.errorMsg)
    assertNull(state.header)
  }

  @Test
  fun unknownRentalShowsAnError() {
    val state = viewModel(rentalId = "nope").uiState.value

    assertFalse(state.isLoading)
    assertEquals("", state.errorMsg)
    assertNull(state.header)
  }

  @Test
  fun headerFailureShowsItsMessage() {
    chat.failure = IOException("offline")

    val state = viewModel().uiState.value

    assertFalse(state.isLoading)
    assertEquals("offline", state.errorMsg)
  }

  @Test
  fun messagesFailureShowsItsMessageAndKeepsTheHeader() {
    val breaks =
        object : ChatRepository by chat {
          override fun observeMessages(rentalId: String): Flow<List<Message>> = flow {
            throw IOException("connection lost")
          }
        }

    val state = viewModel(breaks).uiState.value

    assertEquals("connection lost", state.errorMsg)
    assertEquals("Camping tent", state.header?.itemTitle)
  }

  @Test
  fun retryAfterAFailureLoadsTheChat() {
    chat.failure = IOException("offline")
    val vm = viewModel()

    chat.failure = null
    vm.retry()

    assertNull(vm.uiState.value.errorMsg)
    assertEquals(3, vm.uiState.value.messages.size)
  }

  // ---------- typing and sending ----------

  @Test
  fun typingUpdatesTheDraft() {
    val vm = viewModel()

    vm.onDraftChange("Hello")

    assertEquals("Hello", vm.uiState.value.draft)
    assertTrue(vm.uiState.value.canSend)
  }

  @Test
  fun sendingClearsTheDraftAndShowsTheMessage() {
    val vm = viewModel()
    vm.onDraftChange("See you at 5?")

    vm.send()

    val state = vm.uiState.value
    assertEquals("", state.draft)
    assertFalse(state.sendFailed)
    assertEquals(
        ChatMessageRow("m4", "See you at 5?", chat.now, MessageKind.MINE),
        state.messages.last(),
    )
  }

  @Test
  fun blankDraftIsNotSent() {
    val vm = viewModel()
    for (text in listOf("", "   ", "\n\t")) {
      vm.onDraftChange(text)
      assertFalse(vm.uiState.value.canSend)
      vm.send()
    }

    assertEquals(0, chat.sendMessageCalls)
  }

  @Test
  fun failedSendKeepsTheTextAndSaysSo() {
    val vm = viewModel()
    vm.onDraftChange("Hello")
    chat.failure = IOException("offline")

    vm.send()

    val state = vm.uiState.value
    assertEquals("Hello", state.draft)
    assertTrue(state.sendFailed)
    assertTrue(state.canSend) // pressing Send again retries
  }

  @Test
  fun sendingAgainAfterAFailureRetries() {
    val vm = viewModel()
    vm.onDraftChange("Hello")
    chat.failure = IOException("offline")
    vm.send()

    chat.failure = null
    vm.send()

    assertFalse(vm.uiState.value.sendFailed)
    assertEquals("", vm.uiState.value.draft)
    assertEquals(2, chat.sendMessageCalls)
  }

  @Test
  fun typingAfterAFailureHidesTheError() {
    val vm = viewModel()
    vm.onDraftChange("Hello")
    chat.failure = IOException("offline")
    vm.send()

    vm.onDraftChange("Hello!")

    assertFalse(vm.uiState.value.sendFailed)
  }

  @Test
  fun fieldIsClearedBeforeTheServerConfirms() {
    // The gate holds sendMessage like a slow or missing network holds Firestore's confirmation.
    val gate = CompletableDeferred<Unit>()
    chat.gate = gate
    val vm = viewModel()
    vm.onDraftChange("Hello")

    vm.send()

    assertEquals("", vm.uiState.value.draft)
    assertFalse(vm.uiState.value.sendFailed)
    assertEquals(1, chat.sendMessageCalls)
    gate.complete(Unit)
  }

  @Test
  fun aSecondMessageCanBeSentBeforeTheFirstIsConfirmed() {
    val gate = CompletableDeferred<Unit>()
    chat.gate = gate
    val vm = viewModel()
    vm.onDraftChange("See you at 5?")
    vm.send()

    vm.onDraftChange("Is that ok?")
    assertTrue(vm.uiState.value.canSend)
    vm.send()
    gate.complete(Unit)

    assertEquals(2, chat.sendMessageCalls)
    assertEquals(
        listOf("See you at 5?", "Is that ok?"),
        vm.uiState.value.messages.takeLast(2).map { it.text },
    )
  }

  @Test
  fun failureDoesNotOverwriteTextTypedMeanwhile() {
    val gate = CompletableDeferred<Unit>()
    chat.gate = gate
    val vm = viewModel()
    vm.onDraftChange("Hello")
    vm.send()

    vm.onDraftChange("And another thing")
    chat.failure = IOException("offline")
    gate.complete(Unit)

    assertEquals("And another thing", vm.uiState.value.draft)
    assertTrue(vm.uiState.value.sendFailed)
  }

  // ---------- marking as read ----------

  private val great = message("m4", "ana", "Great", MessageType.USER)

  @Test
  fun openingTheChatMarksItAsRead() {
    viewModel().onStarted()

    assertEquals(1, chat.markAsReadCalls)
    assertEquals(chat.now, chat.conversations.single().lastReadAt["me"])
  }

  @Test
  fun chatIsNotMarkedAsReadBeforeTheScreenIsVisible() {
    viewModel()

    assertEquals(0, chat.markAsReadCalls)
  }

  @Test
  fun newMessageWhileTheChatIsOpenMarksItAgain() {
    viewModel().onStarted()

    chat.messages = mapOf("tent" to listOf(fromAna, great))

    assertEquals(2, chat.markAsReadCalls)
  }

  @Test
  fun newMessageWhileTheChatIsHiddenStaysUnread() {
    val vm = viewModel()
    vm.onStarted()
    vm.onStopped() // e.g. Rental detail opened on top of the chat

    chat.messages = mapOf("tent" to listOf(fromAna, great))

    assertEquals(1, chat.markAsReadCalls)
  }

  @Test
  fun comingBackToTheChatMarksTheMessagesReceivedMeanwhile() {
    val vm = viewModel()
    vm.onStarted()
    vm.onStopped()
    chat.messages = mapOf("tent" to listOf(fromAna, great))

    vm.onStarted()

    assertEquals(2, chat.markAsReadCalls)
  }

  @Test
  fun comingBackWithoutNewMessagesDoesNotMarkAgain() {
    val vm = viewModel()
    vm.onStarted()
    vm.onStopped()

    vm.onStarted()

    assertEquals(1, chat.markAsReadCalls)
  }

  @Test
  fun sameNewestMessageConfirmedByTheServerIsNotMarkedAgain() {
    viewModel().onStarted()

    // Firestore sends the list again when the server confirms a message's time.
    chat.messages =
        mapOf("tent" to listOf(fromAna, fromMe, fromApp.copy(sentAt = at.plusSeconds(9))))

    assertEquals(1, chat.markAsReadCalls)
  }

  @Test
  fun ownNewestMessageIsNotMarked() {
    chat.messages = mapOf("tent" to listOf(fromAna, fromMe))

    viewModel().onStarted()

    assertEquals(0, chat.markAsReadCalls)
  }

  @Test
  fun sendingAMessageDoesNotMarkTheChatAgain() {
    val vm = viewModel()
    vm.onStarted()

    vm.onDraftChange("See you then")
    vm.send()

    assertEquals("See you then", vm.uiState.value.messages.last().text)
    assertEquals(1, chat.markAsReadCalls)
  }

  @Test
  fun chatOpenedOfflineIsNotMarkedAsRead() {
    viewModel(connectivity = FakeConnectivityRepository(online = false)).onStarted()

    assertEquals(0, chat.markAsReadCalls)
  }

  @Test
  fun reconnectingWithTheChatOpenMarksItAsRead() {
    val connectivity = FakeConnectivityRepository(online = false)
    viewModel(connectivity = connectivity).onStarted()

    connectivity.setOnline(true)

    assertEquals(1, chat.markAsReadCalls)
  }

  @Test
  fun reconnectingWhileTheChatIsHiddenDoesNotMarkIt() {
    val connectivity = FakeConnectivityRepository(online = false)
    val vm = viewModel(connectivity = connectivity)
    vm.onStarted()
    vm.onStopped()

    connectivity.setOnline(true)

    assertEquals(0, chat.markAsReadCalls)
  }

  @Test
  fun chatWithoutMessagesIsNotMarkedAsRead() {
    chat.messages = emptyMap() // e.g. offline, with no message in the cache

    viewModel().onStarted()

    assertEquals(0, chat.markAsReadCalls)
  }

  @Test
  fun chatStillLoadingIsNotMarkedAsRead() {
    val loading =
        object : ChatRepository by chat {
          override fun observeMessages(rentalId: String): Flow<List<Message>> = MutableSharedFlow()
        }

    viewModel(loading).onStarted()

    assertEquals(0, chat.markAsReadCalls)
  }

  @Test
  fun chatThatCannotBeShownIsNotMarkedAsRead() {
    auth.signedInUser = null
    viewModel().onStarted()
    auth.signedInUser = me
    viewModel(rentalId = "nope").onStarted()
    chat.failure = IOException("offline")
    viewModel().onStarted()

    assertEquals(0, chat.markAsReadCalls)
  }

  @Test
  fun failedMarkAsReadShowsNoError() {
    val failing =
        object : ChatRepository by chat {
          override suspend fun markAsRead(rentalId: String) =
              Result.failure<Unit>(IOException("offline"))
        }

    val vm = viewModel(failing)
    vm.onStarted()
    val state = vm.uiState.value

    assertNull(state.errorMsg)
    assertEquals(3, state.messages.size)
  }

  @Test
  fun unconfirmedMarkAsReadDoesNotHoldBackNewMessages() {
    val neverConfirmed =
        object : ChatRepository by chat {
          override suspend fun markAsRead(rentalId: String): Result<Unit> {
            CompletableDeferred<Unit>().await() // offline: the server never answers
            return Result.success(Unit)
          }
        }
    val vm = viewModel(neverConfirmed)
    vm.onStarted()

    chat.messages = mapOf("tent" to listOf(fromAna, great))

    assertEquals("Great", vm.uiState.value.messages.last().text)
  }

  // ---------- factory ----------

  @Test
  fun factoryCreatesTheViewModelForTheRental() {
    val vm =
        ChatViewModel.Factory(
                "tent",
                chat,
                auth,
                FakeRentalRepository(),
                FakeConnectivityRepository(),
            )
            .create(ChatViewModel::class.java)

    assertEquals("Camping tent", vm.uiState.value.header?.itemTitle)
  }

  @Test(expected = IllegalArgumentException::class)
  fun factoryRejectsOtherViewModels() {
    ChatViewModel.Factory("tent", chat, auth, FakeRentalRepository(), FakeConnectivityRepository())
        .create(OtherViewModel::class.java)
  }

  private class OtherViewModel : ViewModel()
}
