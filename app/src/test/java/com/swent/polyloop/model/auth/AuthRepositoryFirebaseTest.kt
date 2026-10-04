package com.swent.polyloop.model.auth

import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.AuthResult as FirebaseAuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthRepositoryFirebaseTest {

  private lateinit var auth: FirebaseAuth
  private lateinit var user: FirebaseUser
  private lateinit var repository: AuthRepositoryFirebase

  @Before
  fun setUp() {
    // relaxed = true: calls we did not stub return a default value instead of crashing
    auth = mockk(relaxed = true)
    user = mockk(relaxed = true)
    every { user.uid } returns "uid123"
    every { user.email } returns "john@epfl.ch"
    every { user.sendEmailVerification() } returns Tasks.forResult(null)

    repository = AuthRepositoryFirebase(auth)
  }

  /** Builds the object Firebase returns after a sign-up or sign-in. */
  private fun firebaseResultWith(firebaseUser: FirebaseUser?): FirebaseAuthResult {
    val result = mockk<FirebaseAuthResult>()
    every { result.user } returns firebaseUser
    return result
  }

  // ---------- signUp ----------

  @Test
  fun signUpWithEpflEmailSendsVerificationAndSignsOut() = runTest {
    every { auth.createUserWithEmailAndPassword(any(), any()) } returns
        Tasks.forResult(firebaseResultWith(user))

    val result = repository.signUp("John@EPFL.ch", "password")

    assertEquals(AuthResult.Success(Unit), result)
    verify { auth.createUserWithEmailAndPassword("john@epfl.ch", "password") }
    verify { user.sendEmailVerification() }
    verify { auth.signOut() }
  }

  @Test
  fun signUpWithNonEpflEmailReturnsInvalidDomain() = runTest {
    val result = repository.signUp("john@gmail.com", "password")

    assertEquals(AuthResult.Failure(AuthError.INVALID_DOMAIN), result)
    verify(exactly = 0) { auth.createUserWithEmailAndPassword(any(), any()) }
  }

  @Test
  fun signUpWithExistingEmailReturnsEmailAlreadyInUse() = runTest {
    every { auth.createUserWithEmailAndPassword(any(), any()) } returns
        Tasks.forException(FirebaseAuthUserCollisionException("code", "message"))

    val result = repository.signUp("john@epfl.ch", "password")

    assertEquals(AuthResult.Failure(AuthError.EMAIL_ALREADY_IN_USE), result)
  }

  @Test
  fun signUpWithWeakPasswordReturnsWeakPassword() = runTest {
    every { auth.createUserWithEmailAndPassword(any(), any()) } returns
        Tasks.forException(FirebaseAuthWeakPasswordException("code", "message", "reason"))

    val result = repository.signUp("john@epfl.ch", "simple")

    assertEquals(AuthResult.Failure(AuthError.WEAK_PASSWORD), result)
    verify { auth.signOut() }
  }

  @Test
  fun signUpWithoutNetworkReturnsNetwork() = runTest {
    every { auth.createUserWithEmailAndPassword(any(), any()) } returns
        Tasks.forException(FirebaseNetworkException("offline"))

    val result = repository.signUp("john@epfl.ch", "simple")

    assertEquals(AuthResult.Failure(AuthError.NETWORK), result)
    verify { auth.signOut() }
  }

  @Test
  fun signUpWithNoUserReturnedReturnsUnknown() = runTest {
    every { auth.createUserWithEmailAndPassword(any(), any()) } returns
        Tasks.forResult(firebaseResultWith(null))

    val result = repository.signUp("john@epfl.ch", "pass")

    assertEquals(AuthResult.Failure(AuthError.UNKNOWN), result)
  }

  // ---------- signIn ----------

  @Test
  fun signInWithVerifiedEmailReturnsUser() = runTest {
    every { user.isEmailVerified } returns true
    every { auth.signInWithEmailAndPassword(any(), any()) } returns
        Tasks.forResult(firebaseResultWith(user))

    val result = repository.signIn("john@epfl.ch", "password")

    assertEquals(AuthResult.Success(AuthUser("uid123", "john@epfl.ch")), result)
    verify(exactly = 0) { auth.signOut() }
  }

  @Test
  fun signInWithUnverifiedEmailReturnsEmailNotVerifiedAndSignsOut() = runTest {
    every { user.isEmailVerified } returns false
    every { auth.signInWithEmailAndPassword(any(), any()) } returns
        Tasks.forResult(firebaseResultWith(user))

    val result = repository.signIn("john@epfl.ch", "password")

    assertEquals(AuthResult.Failure(AuthError.EMAIL_NOT_VERIFIED), result)
    verify { auth.signOut() }
  }

  @Test
  fun signInWithWrongPasswordReturnsWrongCredentials() = runTest {
    every { auth.signInWithEmailAndPassword(any(), any()) } returns
        Tasks.forException(FirebaseAuthInvalidCredentialsException("code", "message"))

    val result = repository.signIn("john@epfl.ch", "wrong")

    assertEquals(AuthResult.Failure(AuthError.WRONG_CREDENTIALS), result)
  }

  @Test
  fun signInWithWrongDomainReturnsInvalidDomain() = runTest {
    val result = repository.signIn("john@gmail.com", "pass")

    assertEquals(AuthResult.Failure(AuthError.INVALID_DOMAIN), result)
    verify(exactly = 0) { auth.signInWithEmailAndPassword(any(), any()) }
  }

  @Test
  fun signInWithTooManyAttemptsReturnsTooManyRequests() = runTest {
    every { auth.signInWithEmailAndPassword(any(), any()) } returns
        Tasks.forException(FirebaseTooManyRequestsException("code"))

    val result = repository.signIn("john@epfl.ch", "pass")

    assertEquals(AuthResult.Failure(AuthError.TOO_MANY_REQUESTS), result)
  }

  @Test
  fun signInWithDisabledUserReturnsWrongCredentials() = runTest {
    every { auth.signInWithEmailAndPassword(any(), any()) } returns
        Tasks.forException(FirebaseAuthInvalidUserException("code", "mess"))

    val result = repository.signIn("john@epfl.ch", "pass")

    assertEquals(AuthResult.Failure(AuthError.WRONG_CREDENTIALS), result)
  }

  @Test
  fun signInWithNoUserReturnedReturnsUnknown() = runTest {
    every { auth.signInWithEmailAndPassword(any(), any()) } returns
        Tasks.forResult(firebaseResultWith(null))

    val result = repository.signIn("john@epfl.ch", "pass")

    assertEquals(AuthResult.Failure(AuthError.UNKNOWN), result)
  }

  // ---------- resendVerificationEmail ----------

  @Test
  fun resendForUnverifiedUserSendsEmailAndSignsOut() = runTest {
    every { user.isEmailVerified } returns false
    every { auth.signInWithEmailAndPassword(any(), any()) } returns
        Tasks.forResult(firebaseResultWith(user))

    val result = repository.resendVerificationEmail("John@EPFL.ch", "password")

    assertEquals(AuthResult.Success(Unit), result)
    verify { auth.signInWithEmailAndPassword("john@epfl.ch", "password") }
    verify { user.sendEmailVerification() }
    verify { auth.signOut() }
  }

  @Test
  fun resendForVerifiedUserDoesNothing() = runTest {
    every { user.isEmailVerified } returns true
    every { auth.signInWithEmailAndPassword(any(), any()) } returns
        Tasks.forResult(firebaseResultWith(user))

    val result = repository.resendVerificationEmail("John@EPFL.ch", "password")

    assertEquals(AuthResult.Success(Unit), result)
    verify { auth.signInWithEmailAndPassword("john@epfl.ch", "password") }
    verify(exactly = 0) { user.sendEmailVerification() }
    verify { auth.signOut() }
  }

  @Test
  fun resendWithWrongPasswordReturnsWrongCredentials() = runTest {
    every { auth.signInWithEmailAndPassword(any(), any()) } returns
        Tasks.forException(FirebaseAuthInvalidCredentialsException("code", "message"))

    val result = repository.resendVerificationEmail("john@epfl.ch", "wrong")

    assertEquals(AuthResult.Failure(AuthError.WRONG_CREDENTIALS), result)
    verify(exactly = 0) { user.sendEmailVerification() }
    verify { auth.signOut() }
  }

  @Test
  fun resendWithNoUserReturnedReturnsUnknown() = runTest {
    every { auth.signInWithEmailAndPassword(any(), any()) } returns
        Tasks.forResult(firebaseResultWith(null))

    val result = repository.resendVerificationEmail("john@epfl.ch", "password")

    assertEquals(AuthResult.Failure(AuthError.UNKNOWN), result)
    verify { auth.signOut() }
  }

  // ---------- getCurrentUser ----------

  @Test
  fun getCurrentUserWhenNobodySignedInReturnsNull() {
    every { auth.currentUser } returns null

    assertNull(repository.getCurrentUser())
  }

  @Test
  fun getCurrentUserWhenVerifiedReturnsUser() {
    every { user.isEmailVerified } returns true
    every { auth.currentUser } returns user

    assertEquals(AuthUser("uid123", "john@epfl.ch"), repository.getCurrentUser())
  }

  @Test
  fun getCurrentUserWhenUnverifiedReturnsNull() {
    every { user.isEmailVerified } returns false
    every { auth.currentUser } returns user

    assertNull(repository.getCurrentUser())
  }

  // ---------- signOut ----------

  @Test
  fun signOutSignsOutOfFirebase() {
    repository.signOut()

    verify { auth.signOut() }
  }

  // ---------- errors ----------

  @Test
  fun signInWithUnknownFirebaseErrorReturnsUnknown() = runTest {
    every { auth.signInWithEmailAndPassword(any(), any()) } returns
        Tasks.forException(FirebaseException("msg"))

    val result = repository.signIn("john@epfl.ch", "pass")

    assertEquals(AuthResult.Failure(AuthError.UNKNOWN), result)
  }
}
