package com.swent.polyloop.model.auth

/** The outcome of an auth action: [Success] with a value, or [Failure] with the reason. */
sealed class AuthResult<out T> {
  data class Success<out T>(val data: T) : AuthResult<T>()

  data class Failure(val error: AuthError) : AuthResult<Nothing>()
}

enum class AuthError {
  NAME_REQUIRED,
  INVALID_DOMAIN,
  /** Wrong email or password, or no such account. */
  WRONG_CREDENTIALS,
  EMAIL_ALREADY_IN_USE,
  WEAK_PASSWORD,
  /** Longer than [PasswordRule.MAX_LENGTH_CHARS], which the Firebase password policy refuses. */
  PASSWORD_TOO_LONG,
  EMAIL_NOT_VERIFIED,
  /** The account was created, but the verification email could not be sent; offer "Resend". */
  VERIFICATION_EMAIL_NOT_SENT,
  /** The account was created and the email sent, but the name could not be saved. */
  NAME_NOT_SAVED,
  /** The user signed in, but their profile could not be created; they are signed out again. */
  PROFILE_NOT_CREATED,
  /**
   * On "Check your inbox": the account is gone, because the server deletes accounts not verified in
   * time (see [com.swent.polyloop.ui.authentication.AuthViewModel.SIGN_UP_EXPIRY_MINUTES]).
   */
  SIGN_UP_EXPIRED,
  TOO_MANY_REQUESTS,
  NETWORK,
  UNKNOWN,
}
