package com.swent.polyloop.model.auth

/** The outcome of an auth action: [Success] with a value, or [Failure] with the reason. */
sealed class AuthResult<out T> {
  data class Success<out T>(val data: T) : AuthResult<T>()

  data class Failure(val error: AuthError) : AuthResult<Nothing>()
}

enum class AuthError {
  INVALID_DOMAIN,
  /** Wrong email or password, or no such account. */
  WRONG_CREDENTIALS,
  EMAIL_ALREADY_IN_USE,
  WEAK_PASSWORD,
  EMAIL_NOT_VERIFIED,
  TOO_MANY_REQUESTS,
  NETWORK,
  UNKNOWN,
}
