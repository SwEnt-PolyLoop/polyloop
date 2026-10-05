// Made with Claude.

package com.swent.polyloop.model.profile

import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

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
  ): Result<Unit> =
      try {
        val document = db.collection(ProfileFields.COLLECTION).document(uid)
        if (!document.get().await().exists()) {
          val profile = UserProfile(uid = uid, name = name, email = email)
          // Only the fields the app owns: the rating fields are left to Cloud Functions.
          document
              .set(
                  mapOf(
                      ProfileFields.NAME to profile.name,
                      ProfileFields.EMAIL to profile.email,
                      ProfileFields.PHOTO_URL to profile.photoUrl,
                  )
              )
              .await()
        }
        Result.success(Unit)
      } catch (e: CancellationException) {
        // Cancellation is not a failure: the caller (e.g. a closed screen) stopped waiting.
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }
}
