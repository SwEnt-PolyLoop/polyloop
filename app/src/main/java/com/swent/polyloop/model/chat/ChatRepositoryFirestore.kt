// Made with Claude.

package com.swent.polyloop.model.chat

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.swent.polyloop.model.firestore.confirmWrite
import com.swent.polyloop.model.firestore.isValidDocumentId
import com.swent.polyloop.model.firestore.snapshotsAsFlow
import com.swent.polyloop.model.firestore.withFirebaseErrors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * [ChatRepository] backed by Firestore. Each rental's messages are in its `messages` sub-collection
 * (`rentals/{rentalId}/messages`), and the chat list reads the rental documents themselves.
 */
class ChatRepositoryFirestore(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val auth: FirebaseAuth = Firebase.auth,
) : ChatRepository {

  override fun observeConversations(uid: String): Flow<List<Conversation>> {
    if (!isValidDocumentId(uid)) return flowOf(emptyList())
    // Sorted here, not by Firestore: filtering on one field and sorting on another would need a
    // composite index in the console. A user has few rentals, so sorting them here is cheap.
    return db.collection(ChatFields.RENTALS)
        .whereArrayContains(ChatFields.PARTICIPANTS, uid)
        .observe(::conversationFromDocument)
        .map { conversations -> conversations.sortedByDescending { it.lastMessageAt } }
  }

  override suspend fun getConversation(rentalId: String): Result<Conversation?> {
    if (!isValidDocumentId(rentalId)) return Result.success(null)
    return withFirebaseErrors {
      val snapshot = rental(rentalId).get().await()
      Result.success(snapshot.data?.let { conversationFromDocument(rentalId, it) })
    }
  }

  override fun observeMessages(rentalId: String): Flow<List<Message>> {
    if (!isValidDocumentId(rentalId)) return flowOf(emptyList())
    return rental(rentalId)
        .collection(ChatFields.MESSAGES)
        .orderBy(ChatFields.SENT_AT)
        .observe(::messageFromDocument)
  }

  override suspend fun sendMessage(rentalId: String, text: String): Result<Unit> {
    if (text.isBlank()) return Result.failure(IllegalArgumentException("Blank message"))
    if (!isValidDocumentId(rentalId)) {
      return Result.failure(IllegalArgumentException("Invalid rental id"))
    }
    val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Signed out"))

    val rental = rental(rentalId)
    val message = rental.collection(ChatFields.MESSAGES).document() // new random id
    return withFirebaseErrors {
      // A batch writes both or neither, so the chat list never misses a sent message. The update
      // fails if the rental does not exist, so no message is left without its rental.
      val batch =
          db.batch()
              .set(
                  message,
                  mapOf(
                      ChatFields.SENDER_ID to uid,
                      ChatFields.TEXT to text,
                      ChatFields.SENT_AT to FieldValue.serverTimestamp(),
                      ChatFields.TYPE to MessageType.USER.name,
                  ),
              )
              .update(
                  rental,
                  mapOf(
                      ChatFields.LAST_MESSAGE to text,
                      ChatFields.LAST_MESSAGE_AT to FieldValue.serverTimestamp(),
                  ),
              )
      // Not confirmed in time (offline): the batch stays queued and Firestore sends it later, so
      // it is not a failure. Reporting one would make the user send it again, twice.
      confirmWrite { batch.commit() }
      Result.success(Unit)
    }
  }

  private fun rental(rentalId: String): DocumentReference =
      db.collection(ChatFields.RENTALS).document(rentalId)

  /**
   * Listens to this query and emits its documents each time they change, skipping the ones [map]
   * rejects. A message just sent has no server time yet, so its time is estimated from the phone's
   * clock until the server's arrives; otherwise it would be hidden for a moment.
   */
  private fun <T> Query.observe(map: (String, Map<String, Any?>) -> T?): Flow<List<T>> =
      snapshotsAsFlow { snapshot ->
        snapshot.documents.mapNotNull { doc ->
          doc.getData(ServerTimestampBehavior.ESTIMATE)?.let { map(doc.id, it) }
        }
      }
      .distinctUntilChanged()
}
