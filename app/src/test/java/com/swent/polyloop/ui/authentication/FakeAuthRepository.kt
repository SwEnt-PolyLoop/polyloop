// Made with Claude.

package com.swent.polyloop.ui.authentication

import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.auth.AuthResult
import com.swent.polyloop.model.auth.AuthUser
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory [AuthRepository] for tests. Each call returns the matching result, which tests set
 * beforehand. When [gate] is set, calls wait for it to complete, so tests can see the loading
 * state. Every call is recorded in [calls].
 */
class FakeAuthRepository(
    var signUpResult: AuthResult<Unit> = AuthResult.Success(Unit),
    var signInResult: AuthResult<AuthUser> =
        AuthResult.Success(AuthUser(uid = "uid1", email = "john@epfl.ch", name = "John")),
    var resendResult: AuthResult<Unit> = AuthResult.Success(Unit),
    var gate: CompletableDeferred<Unit>? = null,
) : AuthRepository {

  // The calls received, e.g. "signIn(john@epfl.ch, secret)", in order.
  val calls = mutableListOf<String>()

  override suspend fun signUp(name: String, email: String, password: String): AuthResult<Unit> {
    calls += "signUp($name, $email, $password)"
    gate?.await()
    return signUpResult
  }

  override suspend fun signIn(email: String, password: String): AuthResult<AuthUser> {
    calls += "signIn($email, $password)"
    gate?.await()
    return signInResult
  }

  override suspend fun resendVerificationEmail(email: String, password: String): AuthResult<Unit> {
    calls += "resendVerificationEmail($email, $password)"
    gate?.await()
    return resendResult
  }

  override fun getCurrentUser(): AuthUser? = null

  override fun signOut() {
    calls += "signOut()"
  }
}
