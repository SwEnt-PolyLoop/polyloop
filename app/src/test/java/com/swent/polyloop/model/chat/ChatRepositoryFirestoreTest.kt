// Made with Claude.

package com.swent.polyloop.model.chat

import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.WriteBatch
import com.swent.polyloop.model.rental.RentalStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Checks the Firestore calls with mocks; the emulator test checks the real behaviour. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepositoryFirestoreTest {

  private val db = mockk<FirebaseFirestore>()
  private val auth = mockk<FirebaseAuth>()
  private val rentals = mockk<CollectionReference>()
  private val rental = mockk<DocumentReference>()
  private val messages = mockk<CollectionReference>()
  private val newMessage = mockk<DocumentReference>()
  private val messagesQuery = mockk<Query>()
  private val myRentals = mockk<Query>()
  private val myRentalsQuery = mockk<Query>()
  private val batch = mockk<WriteBatch>()
  private val registration = mockk<ListenerRegistration>(relaxed = true)
  private val repository = ChatRepositoryFirestore(db, auth)

  private val messageData = slot<Any>()
  private val rentalUpdate = slot<Map<String, Any>>()
  private val listener = slot<EventListener<QuerySnapshot>>()

  private val notFound =
      FirebaseFirestoreException("no rental", FirebaseFirestoreException.Code.NOT_FOUND)

  private val sentAt = Instant.parse("2026-10-13T15:02:00Z")
  private val timestamp = Timestamp(sentAt.epochSecond, sentAt.nano)

  @Before
  fun setUp() {
    val user = mockk<FirebaseUser>()
    every { user.uid } returns "u1"
    every { auth.currentUser } returns user

    every { db.collection("rentals") } returns rentals
    every { rentals.document("r1") } returns rental
    every { rental.collection("messages") } returns messages
    every { messages.document() } returns newMessage
    every { messages.orderBy("sentAt") } returns messagesQuery
    every { rentals.whereArrayContains("participants", "u1") } returns myRentals
    every { myRentals.orderBy("lastMessageAt", Query.Direction.DESCENDING) } returns myRentalsQuery

    every { db.batch() } returns batch
    every { batch.set(newMessage, capture(messageData)) } returns batch
    every { batch.update(rental, capture(rentalUpdate)) } returns batch
    every { batch.commit() } returns Tasks.forResult(null)
  }

  // ---------- sendMessage ----------

  @Test
  fun sendWritesTheMessageAndTheLastMessageInOneBatch() = runTest {
    assertTrue(repository.sendMessage("r1", " See you at 5? ").isSuccess)

    assertEquals(
        mapOf(
            "senderId" to "u1",
            "text" to " See you at 5? ", // kept exactly as typed
            "sentAt" to FieldValue.serverTimestamp(),
            "type" to "USER",
        ),
        messageData.captured,
    )
    assertEquals(
        mapOf("lastMessage" to " See you at 5? ", "lastMessageAt" to FieldValue.serverTimestamp()),
        rentalUpdate.captured,
    )
    verify(exactly = 1) { batch.commit() }
  }

  @Test
  fun blankMessagesAreRefusedWithoutWriting() = runTest {
    for (text in listOf("", "   ", "\n\t")) {
      assertTrue(repository.sendMessage("r1", text).exceptionOrNull() is IllegalArgumentException)
    }
    verify(exactly = 0) { db.batch() }
  }

  @Test
  fun invalidRentalIdsAreRefusedWithoutWriting() = runTest {
    for (id in listOf("", " ", "r1/messages")) {
      assertTrue(repository.sendMessage(id, "Hi").exceptionOrNull() is IllegalArgumentException)
    }
    verify(exactly = 0) { db.batch() }
  }

  @Test
  fun sendingWhileSignedOutIsRefusedWithoutWriting() = runTest {
    every { auth.currentUser } returns null
    assertTrue(repository.sendMessage("r1", "Hi").exceptionOrNull() is IllegalStateException)
    verify(exactly = 0) { db.batch() }
  }

  @Test
  fun firestoreErrorIsReturnedAsAFailure() = runTest {
    every { batch.commit() } returns Tasks.forException(notFound)
    assertSame(notFound, repository.sendMessage("r1", "Hi").exceptionOrNull())
  }

  // ---------- observeMessages ----------

  private fun document(id: String, data: Map<String, Any?>): DocumentSnapshot {
    val doc = mockk<DocumentSnapshot>()
    every { doc.id } returns id
    every { doc.getData(ServerTimestampBehavior.ESTIMATE) } returns data
    return doc
  }

  private fun snapshotOf(vararg docs: DocumentSnapshot): QuerySnapshot {
    val snapshot = mockk<QuerySnapshot>()
    every { snapshot.documents } returns docs.toList()
    return snapshot
  }

  private fun messageDoc(id: String, text: String) =
      document(
          id,
          mapOf("senderId" to "u1", "text" to text, "sentAt" to timestamp, "type" to "USER"),
      )

  private fun message(id: String, text: String) = Message(id, "u1", text, sentAt, MessageType.USER)

  @Test
  fun messagesAreEmittedInOrderSkippingBrokenOnesAndEachChange() = runTest {
    every { messagesQuery.addSnapshotListener(capture(listener)) } returns registration
    val emitted = mutableListOf<List<Message>>()
    val collector =
        launch(UnconfinedTestDispatcher(testScheduler)) {
          repository.observeMessages("r1").toList(emitted)
        }

    val hi = messageDoc("m1", "Hi")
    val broken = document("m2", mapOf("text" to "no sender"))
    listener.captured.onEvent(snapshotOf(hi, broken), null)
    listener.captured.onEvent(snapshotOf(hi, broken), null) // same data: not emitted again
    listener.captured.onEvent(snapshotOf(hi, broken, messageDoc("m3", "Ok")), null)
    collector.cancelAndJoin()

    assertEquals(
        listOf(listOf(message("m1", "Hi")), listOf(message("m1", "Hi"), message("m3", "Ok"))),
        emitted,
    )
    verify { registration.remove() } // stops listening when the screen stops collecting
  }

  @Test
  fun messagesFlowFailsWithTheFirestoreError() = runTest {
    every { messagesQuery.addSnapshotListener(capture(listener)) } answers
        {
          listener.captured.onEvent(null, notFound)
          registration
        }
    try {
      repository.observeMessages("r1").first()
      fail("The flow should fail")
    } catch (e: FirebaseFirestoreException) {
      assertSame(notFound, e)
    }
    verify { registration.remove() }
  }

  @Test
  fun invalidRentalIdHasNoMessagesAndNoListener() = runTest {
    for (id in listOf("", " ", "r1/messages")) {
      assertEquals(emptyList<Message>(), repository.observeMessages(id).first())
    }
    verify(exactly = 0) { db.collection(any()) }
  }

  // ---------- observeConversations ----------

  private val rentalData =
      mapOf(
          "listingTitle" to "Camping tent",
          "ownerId" to "u1",
          "ownerName" to "Ana",
          "borrowerId" to "u2",
          "borrowerName" to "Ben",
          "lastMessage" to "Hi",
          "lastMessageAt" to timestamp,
          "status" to "ACCEPTED",
      )

  @Test
  fun conversationsComeFromTheUsersRentalsNewestFirst() = runTest {
    every { myRentalsQuery.addSnapshotListener(capture(listener)) } answers
        {
          listener.captured.onEvent(
              snapshotOf(document("r1", rentalData), document("r9", mapOf("ownerId" to "u1"))),
              null,
          )
          registration
        }

    assertEquals(
        listOf(
            Conversation(
                "r1",
                "Camping tent",
                "u1",
                "Ana",
                "u2",
                "Ben",
                "Hi",
                sentAt,
                RentalStatus.ACCEPTED,
            )
        ),
        repository.observeConversations("u1").first(),
    )
    // The query itself does the filtering and sorting (checked for real on the emulator).
    verify { rentals.whereArrayContains("participants", "u1") }
    verify { myRentals.orderBy("lastMessageAt", Query.Direction.DESCENDING) }
  }

  @Test
  fun invalidUserIdHasNoConversationsAndNoListener() = runTest {
    for (uid in listOf("", " ", "u1/x")) {
      assertEquals(emptyList<Conversation>(), repository.observeConversations(uid).first())
    }
    verify(exactly = 0) { db.collection(any()) }
  }
}
