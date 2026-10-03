package com.swent.polyloop.model.auth

/** User accounts. Only EPFL emails can sign up, and only verified users can sign in. */
interface AuthRepository {

  /** Creates the account and sends the verification email. The user stays signed out. */
  suspend fun signUp(email: String, password: String): AuthResult<Unit>

  /** Signs in a verified user; otherwise fails and nobody is signed in. */
  suspend fun signIn(email: String, password: String): AuthResult<AuthUser>

  /** Sends the verification email again. The user stays signed out. */
  suspend fun resendVerificationEmail(email: String, password: String): AuthResult<Unit>

  /** The signed-in user, or null. */
  fun getCurrentUser(): AuthUser?

  fun signOut()
}
