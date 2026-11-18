// Edited with Claude.

package com.swent.polyloop.model.auth

/** User accounts. Only EPFL emails can sign up, and only verified users can sign in. */
interface AuthRepository {

  /**
   * Creates the account, saves the [name] and sends the verification email. The user stays signed
   * out.
   */
  suspend fun signUp(name: String, email: String, password: String): AuthResult<Unit>

  /** Signs in a verified user; otherwise fails and nobody is signed in. */
  suspend fun signIn(email: String, password: String): AuthResult<AuthUser>

  /** Sends the verification email again. The user stays signed out. */
  suspend fun resendVerificationEmail(email: String, password: String): AuthResult<Unit>

  /** The signed-in user, or null. */
  fun getCurrentUser(): AuthUser?

  /**
   * Whether the signed-in user is an admin, read from the custom claim `admin` set to true. False
   * when nobody is signed in or the read fails, so offline too, as the claim is read from the
   * server. False by default, for the repositories that have no admins.
   */
  suspend fun isAdmin(): Boolean = false

  fun signOut()
}
