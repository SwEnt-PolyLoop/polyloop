// Made with Claude.

package com.swent.polyloop.ui.chatlist

import androidx.lifecycle.ViewModel
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.chat.ChatRepository
import com.swent.polyloop.model.chat.Conversation
import com.swent.polyloop.model.chat.FakeChatRepository
import com.swent.polyloop.model.rental.RentalStatus
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatListViewModelTest {

  private val me = AuthUser(uid = "me", email = "me@epfl.ch", name = "Me")
  private val at = Instant.parse("2026-10-13T10:00:00Z")

  private fun conversation(
      id: String,
      ownerId: String,
      borrowerId: String,
      lastMessageAt: Instant = at,
      status: RentalStatus = RentalStatus.REQUESTED,
  ) =
      Conversation(
          rentalId = id,
          listingTitle = "Item $id",
          ownerId = ownerId,
          ownerName = "Owner of $id",
          borrowerId = borrowerId,
          borrowerName = "Borrower of $id",
          lastMessage = "Last in $id",
          lastMessageAt = lastMessageAt,
          status = status,
          startDate = LocalDate.of(2026, 10, 20),
          endDate = LocalDate.of(2026, 10, 22),
      )

  private val borrowed = conversation("tent", ownerId = "vida", borrowerId = "me")
  private val lent = conversation("drill", ownerId = "me", borrowerId = "loca", at.minusSeconds(60))
  private val someoneElses = conversation("guitar", ownerId = "vida", borrowerId = "loca")

  private lateinit var chat: FakeChatRepository
  private lateinit var auth: FakeAuthRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    chat = FakeChatRepository(currentUid = "me", now = at.plusSeconds(60))
    chat.conversations.value = listOf(borrowed, lent, someoneElses)
    auth = FakeAuthRepository(signedInUser = me)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel(repository: ChatRepository = chat) = ChatListViewModel(repository, auth)

  @Test
  fun initShowsMyConversationsNewestFirst() {
    val state = viewModel().uiState.value

    assertFalse(state.isLoading)
    assertNull(state.errorMsg)
    assertEquals(listOf("tent", "drill"), state.rows.map { it.rentalId })
  }

  @Test
  fun borrowerSeesTheOwnerAndTheLastMessage() {
    assertEquals(
        ChatListRow(
            rentalId = "tent",
            otherPersonName = "Owner of tent",
            itemTitle = "Item tent",
            lastMessage = "Last in tent",
            lastMessageAt = at,
            status = RentalStatus.REQUESTED,
        ),
        viewModel().uiState.value.rows.first(),
    )
  }

  @Test
  fun rowCarriesTheRentalStatus() {
    chat.conversations.value =
        listOf(conversation("tent", "vida", "me", status = RentalStatus.ACCEPTED))

    assertEquals(RentalStatus.ACCEPTED, viewModel().uiState.value.rows.single().status)
  }

  @Test
  fun ownerSeesTheBorrower() {
    assertEquals("Borrower of drill", viewModel().uiState.value.rows[1].otherPersonName)
  }

  @Test
  fun newMessageMovesItsConversationToTheTopWithoutReloading() = runTest {
    val vm = viewModel()

    chat.sendMessage("drill", "Is it still free?")

    val top = vm.uiState.value.rows.first()
    assertEquals("drill", top.rentalId)
    assertEquals("Is it still free?", top.lastMessage)
    assertEquals(at.plusSeconds(60), top.lastMessageAt)
  }

  @Test
  fun blankNameAndNoMessageYetArePassedOnForThePlaceholders() {
    chat.conversations.value = listOf(borrowed.copy(ownerName = "", lastMessage = ""))

    val row = viewModel().uiState.value.rows.single()

    assertEquals("", row.otherPersonName)
    assertEquals("", row.lastMessage)
  }

  @Test
  fun noConversationsGivesAnEmptyListWithoutError() {
    chat.conversations.value = emptyList()

    val state = viewModel().uiState.value

    assertFalse(state.isLoading)
    assertNull(state.errorMsg)
    assertTrue(state.rows.isEmpty())
  }

  @Test
  fun staysLoadingUntilTheConversationsArrive() {
    val silent =
        object : ChatRepository by chat {
          override fun observeConversations(uid: String): Flow<List<Conversation>> =
              MutableSharedFlow()
        }

    val state = viewModel(silent).uiState.value

    assertTrue(state.isLoading)
    assertNull(state.errorMsg)
  }

  @Test
  fun signedOutShowsAnErrorWithoutAskingTheRepository() {
    auth.signedInUser = null
    var asked = false
    val spy =
        object : ChatRepository by chat {
          override fun observeConversations(uid: String): Flow<List<Conversation>> {
            asked = true
            return chat.observeConversations(uid)
          }
        }

    val state = viewModel(spy).uiState.value

    assertFalse(asked)
    assertFalse(state.isLoading)
    assertEquals("", state.errorMsg)
    assertTrue(state.rows.isEmpty())
  }

  @Test
  fun loadingFailureShowsItsMessage() {
    chat.failure = IOException("offline")

    val state = viewModel().uiState.value

    assertFalse(state.isLoading)
    assertEquals("offline", state.errorMsg)
  }

  @Test
  fun failureWithoutAMessageGivesABlankError() {
    chat.failure = IOException()

    assertEquals("", viewModel().uiState.value.errorMsg)
  }

  @Test
  fun retryAfterAFailureLoadsTheConversations() {
    chat.failure = IOException("offline")
    val vm = viewModel()

    chat.failure = null
    vm.retry()

    assertNull(vm.uiState.value.errorMsg)
    assertEquals(listOf("tent", "drill"), vm.uiState.value.rows.map { it.rentalId })
  }

  @Test
  fun errorAfterLoadingKeepsTheLastRows() {
    val breaksLater =
        object : ChatRepository by chat {
          override fun observeConversations(uid: String): Flow<List<Conversation>> = flow {
            emit(listOf(borrowed))
            throw IOException("connection lost")
          }
        }

    val state = viewModel(breaksLater).uiState.value

    assertEquals("connection lost", state.errorMsg)
    assertEquals(listOf("tent"), state.rows.map { it.rentalId })
  }

  @Test
  fun factoryCreatesTheViewModel() {
    val vm = ChatListViewModel.Factory(chat, auth).create(ChatListViewModel::class.java)

    assertEquals(listOf("tent", "drill"), vm.uiState.value.rows.map { it.rentalId })
  }

  @Test(expected = IllegalArgumentException::class)
  fun factoryRejectsOtherViewModels() {
    ChatListViewModel.Factory(chat, auth).create(OtherViewModel::class.java)
  }

  private class OtherViewModel : ViewModel()
}
