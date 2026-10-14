// Made with Claude.

package com.swent.polyloop.model.chat

import com.swent.polyloop.model.rental.RentalStatus
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FakeChatRepositoryTest {

  private val repository = FakeChatRepository(currentUid = "u1")

  private fun conversation(rentalId: String, owner: String, borrower: String, at: String) =
      Conversation(
          rentalId = rentalId,
          listingTitle = "Item $rentalId",
          ownerId = owner,
          ownerName = "Name $owner",
          borrowerId = borrower,
          borrowerName = "Name $borrower",
          lastMessage = "",
          lastMessageAt = Instant.parse(at),
          status = RentalStatus.REQUESTED,
      )

  private val older = conversation("r1", owner = "u1", borrower = "u2", at = "2026-10-10T10:00:00Z")
  private val newer = conversation("r2", owner = "u3", borrower = "u1", at = "2026-10-12T10:00:00Z")
  private val notMine =
      conversation("r3", owner = "u2", borrower = "u3", at = "2026-10-13T10:00:00Z")

  // ---------- observeConversations ----------

  @Test
  fun conversationsAreTheUsersOwnNewestFirst() = runTest {
    repository.conversations.value = listOf(older, notMine, newer)
    assertEquals(listOf(newer, older), repository.observeConversations("u1").first())
  }

  @Test
  fun userWithoutRentalsHasNoConversations() = runTest {
    repository.conversations.value = listOf(older, newer)
    assertEquals(emptyList<Conversation>(), repository.observeConversations("u9").first())
  }

  // ---------- observeMessages ----------

  @Test
  fun messagesAreOnlyThoseOfTheRental() = runTest {
    val m1 = Message("m1", "u1", "Hi", Instant.EPOCH, MessageType.USER)
    val m2 = Message("m1", "u2", "Other chat", Instant.EPOCH, MessageType.USER)
    repository.messages.value = mapOf("r1" to listOf(m1), "r2" to listOf(m2))
    assertEquals(listOf(m1), repository.observeMessages("r1").first())
  }

  @Test
  fun unknownRentalHasNoMessages() = runTest {
    assertEquals(emptyList<Message>(), repository.observeMessages("nope").first())
  }

  // ---------- sendMessage ----------

  @Test
  fun sentMessageIsAddedAsTheSignedInUser() = runTest {
    repository.conversations.value = listOf(older)

    assertTrue(repository.sendMessage("r1", "See you at 5?").isSuccess)
    repository.sendMessage("r1", "Ok")

    assertEquals(
        listOf(
            Message("m1", "u1", "See you at 5?", repository.now, MessageType.USER),
            Message("m2", "u1", "Ok", repository.now, MessageType.USER),
        ),
        repository.observeMessages("r1").first(),
    )
  }

  @Test
  fun sentMessageBecomesTheLastMessageAndMovesTheConversationToTheTop() = runTest {
    repository.conversations.value = listOf(older, newer)
    repository.now = Instant.parse("2026-10-13T12:00:00Z")

    repository.sendMessage("r1", "Still available?")

    val conversations = repository.observeConversations("u1").first()
    assertEquals(listOf("r1", "r2"), conversations.map { it.rentalId })
    assertEquals("Still available?", conversations[0].lastMessage)
    assertEquals(repository.now, conversations[0].lastMessageAt)
    assertEquals(newer, conversations[1])
  }

  @Test
  fun blankMessagesAreRefusedAndNotStored() = runTest {
    for (text in listOf("", "   ", "\n\t")) {
      val result = repository.sendMessage("r1", text)
      assertTrue("'$text'", result.exceptionOrNull() is IllegalArgumentException)
    }
    assertEquals(emptyList<Message>(), repository.observeMessages("r1").first())
  }

  @Test
  fun sendingWhileSignedOutIsRefusedAndNotStored() = runTest {
    repository.currentUid = null
    val result = repository.sendMessage("r1", "Hi")
    assertTrue(result.exceptionOrNull() is IllegalStateException)
    assertEquals(emptyList<Message>(), repository.observeMessages("r1").first())
  }

  @Test
  fun sendingToAnUnknownRentalIsRefusedAndNotStored() = runTest {
    repository.conversations.value = listOf(older)

    val result = repository.sendMessage("nope", "Hi")

    assertTrue(result.exceptionOrNull() is NoSuchElementException)
    assertEquals(emptyList<Message>(), repository.observeMessages("nope").first())
    assertEquals(older, repository.observeConversations("u1").first().single())
  }

  @Test
  fun failureFailsSendingAndBothFlows() = runTest {
    val error = IOException("offline")
    repository.failure = error

    assertSame(error, repository.sendMessage("r1", "Hi").exceptionOrNull())
    for (flow in listOf(repository.observeMessages("r1"), repository.observeConversations("u1"))) {
      try {
        flow.first()
        fail("The flow should fail")
      } catch (e: IOException) {
        assertSame(error, e)
      }
    }
  }

  @Test
  fun everyCallIsCountedIncludingFailures() = runTest {
    repository.sendMessage("r1", "Hi")
    repository.sendMessage("r1", "")
    assertEquals(2, repository.sendMessageCalls)
  }

  @Test
  fun gateHoldsTheMessageUntilCompleted() = runTest {
    val gate = CompletableDeferred<Unit>()
    repository.gate = gate

    repository.conversations.value = listOf(older)
    val sending = async { repository.sendMessage("r1", "Hi") }
    yield()
    assertFalse(sending.isCompleted)
    assertEquals(emptyList<Message>(), repository.observeMessages("r1").first())

    gate.complete(Unit)
    assertTrue(sending.await().isSuccess)
    assertEquals(1, repository.observeMessages("r1").first().size)
  }
}
