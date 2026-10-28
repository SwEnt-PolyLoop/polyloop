// Made with Claude.

package com.swent.polyloop.model.profile

import androidx.core.net.toUri
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storageMetadata
import com.swent.polyloop.model.RepositoryProvider
import java.util.concurrent.TimeoutException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [ProfileRepository] backed by Firestore, one document per user at `users/{uid}`, with the profile
 * photo in Cloud Storage at `users/{uid}/profile.jpg`.
 */
class ProfileRepositoryFirestore(
    private val db: FirebaseFirestore = Firebase.firestore,
    storageProvider: () -> FirebaseStorage = { RepositoryProvider.storage },
) : ProfileRepository {

  // Lazy, so creating the repository does not touch Storage; only uploadProfilePhoto does.
  private val storage by lazy(storageProvider)

  override suspend fun createProfileIfMissing(
      uid: String,
      email: String,
      name: String,
  ): Result<Unit> {
    if (!isValidId(uid)) return invalidId(uid)
    return withFirebaseErrors {
      val document = profileDocument(uid)
      val profile = UserProfile(uid = uid, name = name, email = email)
      when {
        document.get().await().exists() -> Result.success(Unit)
        // Merge writes only the fields the app owns and keeps the others, e.g. the rating fields
        // that Cloud Functions write.
        confirmWrite {
          document.set(
              mapOf(
                  ProfileFields.NAME to profile.name,
                  ProfileFields.EMAIL to profile.email,
                  ProfileFields.PHOTO_URL to profile.photoUrl,
              ),
              SetOptions.merge(),
          )
        } -> Result.success(Unit)
        else -> notConfirmed()
      }
    }
  }

  override suspend fun getProfile(uid: String): Result<UserProfile?> {
    if (!isValidId(uid)) return Result.success(null)
    return withFirebaseErrors {
      val snapshot = profileDocument(uid).get().await()
      Result.success(snapshot.data?.let { profileFromDocument(uid, it) })
    }
  }

  override fun observeProfile(uid: String): Flow<UserProfile?> {
    if (!isValidId(uid)) return flowOf(null)
    val document = profileDocument(uid)
    return callbackFlow {
      val registration = document.addSnapshotListener { snapshot, error ->
        // Closing with the error throws the Firestore error itself to the collector.
        // Firestore's own snapshots() cancels the flow instead: the collector would get a
        // CancellationException with a generic message, the real error being only its cause.
        if (error != null) close(error)
        else if (snapshot != null) {
          trySend(snapshot.data?.let { profileFromDocument(uid, it) })
        }
      }
      awaitClose { registration.remove() }
    }
        .distinctUntilChanged()
  }

  override suspend fun updateProfile(uid: String, name: String, photoUrl: String): Result<Unit> {
    if (!isValidId(uid)) return invalidId(uid)
    return withFirebaseErrors {
      // update() fails when the profile does not exist (PERMISSION_DENIED under the Security Rules,
      // NOT_FOUND without them), so this never creates one.
      val written = confirmWrite {
        profileDocument(uid)
            .update(
                mapOf<String, Any>(
                    ProfileFields.NAME to name,
                    ProfileFields.PHOTO_URL to photoUrl,
                )
            )
      }
      if (written) Result.success(Unit) else notConfirmed()
    }
  }

  override suspend fun uploadProfilePhoto(uid: String, photoUri: String): Result<String> {
    if (!isValidId(uid)) return invalidId(uid)
    return withFirebaseErrors {
      // One file per user, so a new photo replaces the old one instead of piling up.
      val ref = storage.reference.child("${ProfileFields.COLLECTION}/$uid/$PHOTO_FILE")
      ref.putFile(photoUri.toUri(), storageMetadata { contentType = PHOTO_CONTENT_TYPE }).await()
      Result.success(ref.downloadUrl.await().toString())
    }
  }

  private fun profileDocument(uid: String): DocumentReference =
      db.collection(ProfileFields.COLLECTION).document(uid)

  /** Firestore throws on these ids instead of failing the task, and no profile can have them. */
  private fun isValidId(uid: String): Boolean = uid.isNotBlank() && '/' !in uid

  private fun <T> invalidId(uid: String): Result<T> =
      Result.failure(IllegalArgumentException("Invalid profile id \"$uid\""))

  /**
   * Runs [write] and returns whether the server confirmed it in time. A write only completes once
   * the server confirms it, which never happens if the connection drops, so stop waiting after a
   * while. Firestore still sends the queued write later, so retrying is safe.
   */
  private suspend fun confirmWrite(write: () -> Task<Void>): Boolean =
      withTimeoutOrNull(WRITE_TIMEOUT_MS) {
        write().await()
        true // await() on a write returns null even on success
      } ?: false

  private fun notConfirmed(): Result<Unit> =
      Result.failure(TimeoutException("Profile not confirmed in time"))

  /**
   * Runs [block] and returns Firebase errors as failures. A cancellation is rethrown only if the
   * caller was cancelled (e.g. the screen closed); a task that Firebase cancelled itself is an
   * ordinary failure. Other exceptions are bugs and are not hidden.
   */
  private suspend fun <T> withFirebaseErrors(block: suspend () -> Result<T>): Result<T> =
      try {
        block()
      } catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()
        Result.failure(e)
      } catch (e: FirebaseException) {
        Result.failure(e)
      }

  private companion object {
    const val WRITE_TIMEOUT_MS = 10_000L
    const val PHOTO_FILE = "profile.jpg"
    const val PHOTO_CONTENT_TYPE = "image/jpeg"
  }
}
