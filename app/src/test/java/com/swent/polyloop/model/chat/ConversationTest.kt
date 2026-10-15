// Made with Claude.

package com.swent.polyloop.model.chat

import com.swent.polyloop.model.rental.RentalStatus
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
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
}
