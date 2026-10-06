package com.swent.polyloop.model.auth

import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await

/** [AuthRepository] backed by Firebase Auth. */
class AuthRepositoryFirebase(private val auth: FirebaseAuth = Firebase.auth) : AuthRepository {

  override suspend fun signUp(email: String, password: String): AuthResult<Unit> {
    if (!EpflEmail.isValid(email)) return AuthResult.Failure(AuthError.INVALID_DOMAIN)
    // Firebase throws (and crashes the app) on an empty password instead of failing the task.
    if (password.isBlank()) return AuthResult.Failure(AuthError.WEAK_PASSWORD)

    return try {
      // Firebase signs the new user in automatically; `finally` signs them out again.
      val user =
          auth.createUserWithEmailAndPassword(EpflEmail.normalize(email), password).await().user
              ?: return AuthResult.Failure(AuthError.UNKNOWN)
      // The account exists now, so a failed email must not look like a failed sign-up.
      val emailSent =
          try {
            user.sendEmailVerification().await()
            true
          } catch (e: FirebaseException) {
            false
          }

      if (emailSent) AuthResult.Success(Unit)
      else AuthResult.Failure(AuthError.VERIFICATION_EMAIL_NOT_SENT)
    } catch (e: FirebaseException) {
      AuthResult.Failure(toAuthError(e))
    } finally {
      auth.signOut()
    }
  }

  override suspend fun signIn(email: String, password: String): AuthResult<AuthUser> {
    if (!EpflEmail.isValid(email)) return AuthResult.Failure(AuthError.INVALID_DOMAIN)
    if (password.isBlank()) return AuthResult.Failure(AuthError.WRONG_CREDENTIALS)

    // Stays false on every other path (errors, unverified, cancelled), so `finally` signs out.
    var signedIn = false
    return try {
      val user =
          auth.signInWithEmailAndPassword(EpflEmail.normalize(email), password).await().user
              ?: return AuthResult.Failure(AuthError.UNKNOWN)
      if (user.isEmailVerified) {
        signedIn = true
        AuthResult.Success(user.toAuthUser())
      } else {
        AuthResult.Failure(AuthError.EMAIL_NOT_VERIFIED)
      }
    } catch (e: FirebaseException) {
      AuthResult.Failure(toAuthError(e))
    } finally {
      if (!signedIn) auth.signOut()
    }
  }

  override suspend fun resendVerificationEmail(email: String, password: String): AuthResult<Unit> {
    if (!EpflEmail.isValid(email)) return AuthResult.Failure(AuthError.INVALID_DOMAIN)
    if (password.isBlank()) return AuthResult.Failure(AuthError.WRONG_CREDENTIALS)

    return try {
      // Only a signed-in user can receive the email, so sign in briefly; `finally` signs out.
      val user =
          auth.signInWithEmailAndPassword(EpflEmail.normalize(email), password).await().user
              ?: return AuthResult.Failure(AuthError.UNKNOWN)
      // Already verified: nothing to send, and nothing went wrong.
      if (!user.isEmailVerified) user.sendEmailVerification().await()
      AuthResult.Success(Unit)
    } catch (e: FirebaseException) {
      AuthResult.Failure(toAuthError(e))
    } finally {
      auth.signOut()
    }
  }

  override fun getCurrentUser(): AuthUser? {
    val user = auth.currentUser ?: return null
    // An unverified user is only left over if the app was killed in the middle of a sign-in step.
    return if (user.isEmailVerified) user.toAuthUser() else null
  }

  override fun signOut() {
    auth.signOut()
  }

  private fun FirebaseUser.toAuthUser() = AuthUser(uid = uid, email = email.orEmpty())

  private fun toAuthError(e: FirebaseException): AuthError =
      when (e) {
        // Must come before InvalidCredentials, because it is a special case of it.
        is FirebaseAuthWeakPasswordException -> AuthError.WEAK_PASSWORD
        is FirebaseAuthInvalidCredentialsException -> AuthError.WRONG_CREDENTIALS
        is FirebaseAuthInvalidUserException -> AuthError.WRONG_CREDENTIALS
        is FirebaseAuthUserCollisionException -> AuthError.EMAIL_ALREADY_IN_USE
        is FirebaseNetworkException -> AuthError.NETWORK
        is FirebaseTooManyRequestsException -> AuthError.TOO_MANY_REQUESTS
        else -> AuthError.UNKNOWN
      }
}
