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
   * with the cause if the profile cannot be read or written, or with a `TimeoutException` if the
   * server does not confirm the write in time (e.g. the connection dropped).
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
   * profile does not exist or cannot be written, or with a `TimeoutException` if the server does
   * not confirm the write in time.
   */
  suspend fun updateProfile(uid: String, name: String, photoUrl: String): Result<Unit>
}
