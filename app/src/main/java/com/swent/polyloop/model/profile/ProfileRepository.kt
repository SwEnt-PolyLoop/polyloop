// Made with Claude.

package com.swent.polyloop.model.profile

/** User profiles. */
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
}
