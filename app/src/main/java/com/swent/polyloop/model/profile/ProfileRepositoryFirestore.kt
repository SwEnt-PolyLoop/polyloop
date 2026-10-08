// Made with Claude.

package com.swent.polyloop.model.profile

import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import java.util.concurrent.TimeoutException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Firestore names for profiles, kept in one place so they are easy to change. Proposed in #45, not
 * agreed by the team yet.
 */
internal object ProfileFields {
  const val COLLECTION = "users"
  const val NAME = "name"
  const val EMAIL = "email"
  const val PHOTO_URL = "photoUrl"
}

/** [ProfileRepository] backed by Firestore, one document per user at `users/{uid}`. */
class ProfileRepositoryFirestore(private val db: FirebaseFirestore = Firebase.firestore) :
    ProfileRepository {

  override suspend fun createProfileIfMissing(
      uid: String,
      email: String,
      name: String,
  ): Result<Unit> {
    return try {
      val document = db.collection(ProfileFields.COLLECTION).document(uid)
      if (!document.get().await().exists()) {
        val profile = UserProfile(uid = uid, name = name, email = email)
        // set() only completes once the server confirms the write, which never happens if the
        // connection drops, so stop waiting after a while. Firestore still sends the queued write
        // later, so retrying is safe.
        val saved =
            withTimeoutOrNull(WRITE_TIMEOUT_MS) {
              // Merge writes only the fields the app owns and keeps the others, e.g. the rating
              // fields that Cloud Functions write.
              document
                  .set(
                      mapOf(
                          ProfileFields.NAME to profile.name,
                          ProfileFields.EMAIL to profile.email,
                          ProfileFields.PHOTO_URL to profile.photoUrl,
                      ),
                      SetOptions.merge(),
                  )
                  .await()
              true // await() on set() returns null even on success
            }
        if (saved == null) return Result.failure(TimeoutException("Profile not confirmed in time"))
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      // Rethrows only if the caller was cancelled (e.g. the screen closed); a task that Firebase
      // cancelled itself is an ordinary failure.
      currentCoroutineContext().ensureActive()
      Result.failure(e)
    } catch (e: FirebaseException) {
      Result.failure(e)
    }
  }

  private companion object {
    const val WRITE_TIMEOUT_MS = 10_000L
  }
}
