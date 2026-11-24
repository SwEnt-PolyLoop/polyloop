// Made with Claude.

package com.swent.polyloop.model.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests the default implementations of [AuthRepository]. */
class AuthRepositoryTest {

  /** Overrides only what has no default, like the test repositories that never send an email. */
  private val repository =
      object : AuthRepository {
        override suspend fun signUp(name: String, email: String, password: String) =
            AuthResult.Success(Unit)

        override suspend fun signIn(email: String, password: String): AuthResult<AuthUser> =
            AuthResult.Failure(AuthError.UNKNOWN)

        override suspend fun resendVerificationEmail(email: String, password: String) =
            AuthResult.Success(Unit)

        override fun getCurrentUser(): AuthUser? = null

        override fun signOut() = Unit
      }

  @Test
  fun sendPasswordResetEmailFailsByDefault() = runTest {
    assertEquals(
        AuthResult.Failure(AuthError.UNKNOWN),
        repository.sendPasswordResetEmail("john@epfl.ch"),
    )
  }
}
