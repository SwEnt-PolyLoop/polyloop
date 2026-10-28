// Made with Claude.

package com.swent.polyloop.model.profile

import androidx.core.net.toUri
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storageMetadata
import com.swent.polyloop.model.RepositoryProvider
import com.swent.polyloop.model.firestore.confirmWrite
import com.swent.polyloop.model.firestore.isValidDocumentId
import com.swent.polyloop.model.firestore.snapshotsAsFlow
import com.swent.polyloop.model.firestore.withFirebaseErrors
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.flow.Flow
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
    if (!isValidDocumentId(uid)) return invalidId(uid)
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
    if (!isValidDocumentId(uid)) return Result.success(null)
    return withFirebaseErrors {
      val snapshot = profileDocument(uid).get().await()
      Result.success(snapshot.data?.let { profileFromDocument(uid, it) })
    }
  }

  override fun observeProfile(uid: String): Flow<UserProfile?> {
    if (!isValidDocumentId(uid)) return flowOf(null)
    return profileDocument(uid)
        .snapshotsAsFlow { snapshot -> snapshot.data?.let { profileFromDocument(uid, it) } }
        .distinctUntilChanged()
  }

  override suspend fun updateProfile(uid: String, name: String, photoUrl: String): Result<Unit> {
    if (!isValidDocumentId(uid)) return invalidId(uid)
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
    if (!isValidDocumentId(uid)) return invalidId(uid)
    return withFirebaseErrors {
      // One file per user, so a new photo replaces the old one instead of piling up.
      val ref = storage.reference.child("${ProfileFields.COLLECTION}/$uid/$PHOTO_FILE")
      val upload =
          ref.putFile(photoUri.toUri(), storageMetadata { contentType = PHOTO_CONTENT_TYPE })
      // Offline, Storage keeps retrying for minutes, so stop waiting after a while.
      val url =
          withTimeoutOrNull(UPLOAD_TIMEOUT_MS) {
            upload.await()
            ref.downloadUrl.await().toString()
          }
      if (url != null) Result.success(url)
      else {
        // Stop the upload too, so it cannot replace the photo later without the profile knowing.
        upload.cancel()
        Result.failure(TimeoutException("Profile photo not uploaded in time"))
      }
    }
  }

  private fun profileDocument(uid: String): DocumentReference =
      db.collection(ProfileFields.COLLECTION).document(uid)

  private fun <T> invalidId(uid: String): Result<T> =
      Result.failure(IllegalArgumentException("Invalid profile id \"$uid\""))

  private fun notConfirmed(): Result<Unit> =
      Result.failure(TimeoutException("Profile not confirmed in time"))

  private companion object {
    // Longer than a Firestore write: a photo can take a while to send.
    const val UPLOAD_TIMEOUT_MS = 30_000L
    const val PHOTO_FILE = "profile.jpg"
    const val PHOTO_CONTENT_TYPE = "image/jpeg"
  }
}
