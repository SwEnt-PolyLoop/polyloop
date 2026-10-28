// Made with Claude.

package com.swent.polyloop.model.profile

import kotlinx.coroutines.flow.Flow

/**
 * User profiles, stored at `users/{uid}`.
 *
 * Reading never drops a profile because of missing fields: a missing name, email or photo reads as
 * "", and a missing rating as 0 (profiles created before the rating existed have none).
 */
interface ProfileRepository {

  /**
   * Creates the profile of a verified user the first time they sign in. Called after every
   * successful sign-in; when the profile already exists nothing is written. Reading and writing are
   * not atomic: two first sign-ins at the same moment can both write (with the same data). Fails
   * with the cause if the profile cannot be read or written, with a `TimeoutException` if the
   * server does not confirm the write in time (e.g. the connection dropped), or with an
   * `IllegalArgumentException` if [uid] is blank or contains '/'.
   *
   * @param name the name typed at sign-up (the Firebase Auth display name).
   */
  suspend fun createProfileIfMissing(uid: String, email: String, name: String): Result<Unit>

  /**
   * The profile of [uid], read once, or null if that user has no profile. Fails with the cause if
   * it cannot be read.
   */
  suspend fun getProfile(uid: String): Result<UserProfile?>

  /**
   * The profile of [uid], emitted again every time it changes, or null while that user has no
   * profile. A read error is thrown to the collector, which handles it with `catch`.
   */
  fun observeProfile(uid: String): Flow<UserProfile?>

  /**
   * Changes the name and photo of an existing profile; the other fields are kept. Fails if the
   * profile does not exist or cannot be written, with a `TimeoutException` if the server does not
   * confirm the write in time, or with an `IllegalArgumentException` if [uid] is blank or contains
   * '/'.
   */
  suspend fun updateProfile(uid: String, name: String, photoUrl: String): Result<Unit>

  /**
   * Uploads the photo at [photoUri] (a local file taken with the in-app camera) to Cloud Storage as
   * the profile photo of [uid], replacing the previous one, and returns its download URL. The
   * profile itself is not changed: save the URL with [updateProfile]. Fails with the cause if the
   * photo cannot be uploaded, or with an `IllegalArgumentException` if [uid] is blank or contains
   * '/'.
   */
  suspend fun uploadProfilePhoto(uid: String, photoUri: String): Result<String>
}
