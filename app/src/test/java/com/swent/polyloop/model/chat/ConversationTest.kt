// Made with Claude.

package com.swent.polyloop.model.chat

import com.swent.polyloop.model.rental.RentalStatus
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationTest {

  private val conversation =
      Conversation(
          rentalId = "r1",
          listingTitle = "Camping tent",
          ownerId = "u1",
          ownerName = "Ana",
          borrowerId = "u2",
          borrowerName = "Ben",
          lastMessage = "",
          lastMessageAt = Instant.EPOCH,
          status = RentalStatus.REQUESTED,
          startDate = LocalDate.of(2026, 10, 20),
          endDate = LocalDate.of(2026, 10, 22),
      )

  @Test fun ownerSeesTheBorrowersName() = assertEquals("Ben", conversation.otherPersonName("u1"))

  @Test fun borrowerSeesTheOwnersName() = assertEquals("Ana", conversation.otherPersonName("u2"))

  @Test
  fun sameNamesStillPickTheOtherSide() {
    val namesakes = conversation.copy(ownerName = "Alex", borrowerName = "Alex B.")
    assertEquals("Alex B.", namesakes.otherPersonName("u1"))
    assertEquals("Alex", namesakes.otherPersonName("u2"))
  }

  // ---------- isUnreadFor ----------

  private val sentAt = Instant.parse("2026-10-13T15:00:00Z")
  private val withMessage = conversation.copy(lastMessage = "Hi", lastMessageAt = sentAt)

  @Test
  fun noMessageYetIsNeverUnread() {
    assertFalse(conversation.isUnreadFor("u1"))
    assertFalse(conversation.copy(lastReadAt = mapOf("u1" to Instant.EPOCH)).isUnreadFor("u1"))
  }

  @Test fun messageInAChatNeverOpenedIsUnread() = assertTrue(withMessage.isUnreadFor("u1"))

  @Test
  fun messageAfterTheLastOpeningIsUnread() =
      assertTrue(
          withMessage.copy(lastReadAt = mapOf("u1" to sentAt.minusSeconds(1))).isUnreadFor("u1")
      )

  @Test
  fun messageSeenWhenOpeningIsRead() =
      assertFalse(
          withMessage.copy(lastReadAt = mapOf("u1" to sentAt.plusSeconds(1))).isUnreadFor("u1")
      )

  @Test
  fun ownMessageSavedAtTheSameInstantIsRead() =
      assertFalse(withMessage.copy(lastReadAt = mapOf("u1" to sentAt)).isUnreadFor("u1"))

  @Test
  fun onlyMyOwnReadTimeCounts() {
    val benOpenedIt = withMessage.copy(lastReadAt = mapOf("u2" to sentAt.plusSeconds(1)))
    assertTrue(benOpenedIt.isUnreadFor("u1"))
    assertFalse(benOpenedIt.isUnreadFor("u2"))
  }
}
