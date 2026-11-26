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

  /**
   * Sends a password reset email to [email]. Also succeeds when the address has no account, as
   * Firebase sends nothing then and, with email enumeration protection on, does not say so: the
   * screen must never tell who has an account. Fails by default, for the test repositories that
   * never send one.
   */
  suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit> =
      AuthResult.Failure(AuthError.UNKNOWN)

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
