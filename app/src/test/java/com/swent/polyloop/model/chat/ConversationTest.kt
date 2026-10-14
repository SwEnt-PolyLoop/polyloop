// Made with Claude.

package com.swent.polyloop.model.chat

import com.swent.polyloop.model.rental.RentalStatus
import java.time.Instant
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
