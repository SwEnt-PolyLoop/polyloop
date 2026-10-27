// Made with Claude.

package com.swent.polyloop.model.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.swent.polyloop.utils.FirebaseEmulator
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real [ChatRepositoryFirestore] against the Firebase emulators (not mocks), so these
 * tests check what mocks cannot: the paths, the sorting and the batch. Needs `firebase
 * emulators:start`.
 */
@RunWith(AndroidJUnit4::class)
class ChatRepositoryFirestoreEmulatorTest {

  private lateinit var repository: ChatRepositoryFirestore
  private lateinit var uid: String

  @Before
  fun setUp() {
    FirebaseEmulator.connect()
    repository = ChatRepositoryFirestore()
    // A new account for each test, so data left by earlier runs never matches its queries.
    uid = runBlocking { FirebaseEmulator.signUpVerified("chat-${UUID.randomUUID()}@epfl.ch") }
  }

  @After
  fun tearDown() {
    Firebase.auth.signOut()
  }

  /**
   * Writes a rental document with the fields the chat reads, as `requestRental` will (#94). As an
   * admin: the rules let only the borrower create a rental, and only for an existing listing.
   */
  private fun createRental(owner: String, borrower: String, lastMessageAt: Long): String {
    val id = "rental-${UUID.randomUUID()}"
    FirebaseEmulator.createDocumentAsAdmin(
        "rentals/$id",
        mapOf(
            "listingTitle" to "Camping tent",
            "ownerId" to owner,
            "ownerName" to "Ana",
            "borrowerId" to borrower,
            "borrowerName" to "Ben",
            "participants" to listOf(owner, borrower),
            "lastMessage" to "",
            "lastMessageAt" to Timestamp(lastMessageAt, 0),
            "status" to "REQUESTED",
            "startDate" to "2026-10-20",
            "endDate" to "2026-10-22",
        ),
    )
    return id
  }

  @Test
  fun sentMessagesAreObservedInOrderAndBecomeTheRentalsLastMessage() = runBlocking {
    val rentalId = createRental(owner = uid, borrower = "someone", lastMessageAt = 1_000)

    assertTrue(repository.sendMessage(rentalId, "Hi").isSuccess)
    assertTrue(repository.sendMessage(rentalId, "See you at 5?").isSuccess)

    val messages =
        withTimeout(TIMEOUT_MS) { repository.observeMessages(rentalId).first { it.size == 2 } }
    assertEquals(listOf("Hi", "See you at 5?"), messages.map { it.text })
    assertTrue(messages.all { it.senderId == uid && it.type == MessageType.USER })

    val rental = Firebase.firestore.collection("rentals").document(rentalId).get().await()
    assertEquals("See you at 5?", rental.getString("lastMessage"))
    assertTrue(rental.getTimestamp("lastMessageAt")!! > Timestamp(1_000, 0))
  }

  @Test
  fun conversationsAreTheUsersOwnNewestFirstAndANewMessageMovesOneToTheTop() = runBlocking {
    val older = createRental(owner = uid, borrower = "someone", lastMessageAt = 1_000)
    val newer = createRental(owner = "someone", borrower = uid, lastMessageAt = 2_000)
    createRental(owner = "someone", borrower = "someone else", lastMessageAt = 3_000) // not mine

    val before =
        withTimeout(TIMEOUT_MS) { repository.observeConversations(uid).first { it.size == 2 } }
    assertEquals(listOf(newer, older), before.map { it.rentalId })

    repository.sendMessage(older, "Still available?")

    val after =
        withTimeout(TIMEOUT_MS) {
          repository.observeConversations(uid).first { it.firstOrNull()?.rentalId == older }
        }
    assertEquals(listOf(older, newer), after.map { it.rentalId })
    assertEquals("Still available?", after[0].lastMessage)
  }

  @Test
  fun sendingToAMissingRentalFailsAndWritesNothing() = runBlocking {
    val missing = "rental-${UUID.randomUUID()}"

    assertTrue(repository.sendMessage(missing, "Hi").isFailure)

    // The rules hide the messages of a missing rental, so check the batch left no rental either.
    assertEquals(null, repository.getConversation(missing).getOrThrow())
  }

  private companion object {
    const val TIMEOUT_MS = 10_000L
  }

  @Test
  fun getConversationReadsTheRentalAndNullForAMissingOne() = runBlocking {
    val rentalId = createRental(owner = uid, borrower = "someone", lastMessageAt = 1_000)

    val conversation = repository.getConversation(rentalId).getOrThrow()
    assertEquals("Camping tent", conversation?.listingTitle)
    assertEquals(LocalDate.of(2026, 10, 20), conversation?.startDate)
    assertEquals(LocalDate.of(2026, 10, 22), conversation?.endDate)

    assertEquals(null, repository.getConversation("rental-${UUID.randomUUID()}").getOrThrow())
  }
}
