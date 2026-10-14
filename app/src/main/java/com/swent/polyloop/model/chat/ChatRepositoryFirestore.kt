// Made with Claude.

package com.swent.polyloop.model.chat

import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
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
    if (!isValidId(uid)) return flowOf(emptyList())
    return db.collection(ChatFields.RENTALS)
        .whereArrayContains(ChatFields.PARTICIPANTS, uid)
        .orderBy(ChatFields.LAST_MESSAGE_AT, Query.Direction.DESCENDING)
        .observe(::conversationFromDocument)
  }

  override suspend fun getConversation(rentalId: String): Result<Conversation?> {
    if (!isValidId(rentalId)) return Result.success(null)
    return try {
      val snapshot = rental(rentalId).get().await()
      Result.success(snapshot.data?.let { conversationFromDocument(rentalId, it) })
    } catch (e: FirebaseException) {
      Result.failure(e)
    }
  }

  override fun observeMessages(rentalId: String): Flow<List<Message>> {
    if (!isValidId(rentalId)) return flowOf(emptyList())
    return rental(rentalId)
        .collection(ChatFields.MESSAGES)
        .orderBy(ChatFields.SENT_AT)
        .observe(::messageFromDocument)
  }

  override suspend fun sendMessage(rentalId: String, text: String): Result<Unit> {
    if (text.isBlank()) return Result.failure(IllegalArgumentException("Blank message"))
    if (!isValidId(rentalId)) return Result.failure(IllegalArgumentException("Invalid rental id"))
    val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Signed out"))

    val rental = rental(rentalId)
    val message = rental.collection(ChatFields.MESSAGES).document() // new random id
    return try {
      // A batch writes both or neither, so the chat list never misses a sent message. The update
      // fails if the rental does not exist, so no message is left without its rental.
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
          .commit()
          .await()
      Result.success(Unit)
    } catch (e: FirebaseException) {
      Result.failure(e)
    }
  }

  private fun rental(rentalId: String): DocumentReference =
      db.collection(ChatFields.RENTALS).document(rentalId)

  /** Firestore throws on these ids instead of failing the task, and no rental or user has them. */
  private fun isValidId(id: String): Boolean = id.isNotBlank() && '/' !in id

  /**
   * Listens to this query and emits its documents each time they change, skipping the ones [map]
   * rejects. A message just sent has no server time yet, so its time is estimated from the phone's
   * clock until the server's arrives; otherwise it would be hidden for a moment.
   */
  private fun <T> Query.observe(map: (String, Map<String, Any?>) -> T?): Flow<List<T>> =
      callbackFlow {
        val registration = addSnapshotListener { snapshot, error ->
          // Closing with the error throws it to the collector, who can show it.
          if (error != null) close(error)
          else if (snapshot != null) {
            trySend(
                snapshot.documents.mapNotNull { doc ->
                  doc.getData(ServerTimestampBehavior.ESTIMATE)?.let { map(doc.id, it) }
                }
            )
          }
        }
        awaitClose { registration.remove() }
      }
      .distinctUntilChanged()
}
