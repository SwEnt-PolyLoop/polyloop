package com.swent.polyloop.model.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.utils.FirebaseEmulator
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real [AuthRepositoryFirebase] against the Firebase Auth emulator (not mocks), so these
 * tests see how Firebase really behaves. Needs `firebase emulators:start`.
 */
@RunWith(AndroidJUnit4::class)
class AuthRepositoryFirebaseEmulatorTest {

  // Created here (not in setUp) so tearDown still works when setUp fails. Creating it does not
  // contact Firebase yet, so connect() can still switch it to the emulator.
  private val repository = AuthRepositoryFirebase()

  @Before
  fun setUp() {
    FirebaseEmulator.connect()
  }

  @After
  fun tearDown() {
    repository.signOut()
  }

  /** A new address for each test, so accounts left by earlier runs never get in the way. */
  private fun uniqueEmail() = "test-${UUID.randomUUID()}@epfl.ch"

  @Test
  fun signInBeforeVerifyingReturnsEmailNotVerified() {
    runBlocking {
      val email = uniqueEmail()
      repository.signUp("Test User", email, PASSWORD)

      val result = repository.signIn(email, PASSWORD)

      assertEquals(AuthResult.Failure(AuthError.EMAIL_NOT_VERIFIED), result)
      assertNull(repository.getCurrentUser())
    }
  }

  @Test
  fun signInAfterVerifyingReturnsTheUserWithTheirName() {
    runBlocking {
      val email = uniqueEmail()
      repository.signUp(" Test User ", email, PASSWORD)
      FirebaseEmulator.verifyEmail(email)

      val result = repository.signIn(email, PASSWORD)

      val user = (result as AuthResult.Success).data
      assertEquals(email, user.email)
      assertEquals("Test User", user.name)
      assertEquals(user, repository.getCurrentUser())
    }
  }

  @Test
  fun signUpTwiceWithTheSameEmailReturnsEmailAlreadyInUse() {
    runBlocking {
      val email = uniqueEmail()
      repository.signUp("Test User", email, PASSWORD)

      val result = repository.signUp("Someone Else", email, PASSWORD)

      assertEquals(AuthResult.Failure(AuthError.EMAIL_ALREADY_IN_USE), result)
    }
  }

  @Test
  fun signInWithWrongPasswordReturnsWrongCredentials() {
    runBlocking {
      val email = uniqueEmail()
      repository.signUp("Test User", email, PASSWORD)

      val result = repository.signIn(email, "not-the-password")

      assertEquals(AuthResult.Failure(AuthError.WRONG_CREDENTIALS), result)
    }
  }

  @Test
  fun signUpWithTooShortPasswordReturnsWeakPassword() {
    runBlocking {
      val result = repository.signUp("Test User", uniqueEmail(), "12345")

      assertEquals(AuthResult.Failure(AuthError.WEAK_PASSWORD), result)
    }
  }

  private companion object {
    const val PASSWORD = "password123"
  }
}
