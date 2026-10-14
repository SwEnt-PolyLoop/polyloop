// Made with Claude.

package com.swent.polyloop.ui.authentication

import androidx.lifecycle.ViewModel
import com.swent.polyloop.model.auth.AuthError
import com.swent.polyloop.model.auth.AuthResult
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

  private lateinit var repository: FakeAuthRepository
  private lateinit var viewModel: AuthViewModel
  private lateinit var scheduler: TestCoroutineScheduler

  private val state
    get() = viewModel.uiState.value

  @Before
  fun setUp() {
    val dispatcher = UnconfinedTestDispatcher()
    scheduler = dispatcher.scheduler
    Dispatchers.setMain(dispatcher)
    repository = FakeAuthRepository()
    viewModel = AuthViewModel(repository)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun fillLogIn() {
    viewModel.onEmailChange("john@epfl.ch")
    viewModel.onPasswordChange("secret")
  }

  private fun fillSignUp() {
    viewModel.switchMode(AuthMode.SIGN_UP)
    viewModel.onNameChange("John")
    fillLogIn()
  }

  /** Moves the virtual clock forward, running what was due, e.g. the resend countdown. */
  private fun advanceSeconds(seconds: Long) {
    scheduler.advanceTimeBy(seconds * 1_000)
    scheduler.runCurrent()
  }

  // ---------- form ----------

  @Test
  fun startsOnEmptyLogInForm() {
    assertEquals(AuthUiState(), state)
    assertEquals(AuthMode.LOG_IN, state.mode)
    assertFalse(state.canSubmit)
  }

  @Test
  fun logInNeedsEpflEmailAndPassword() {
    viewModel.onEmailChange("john@gmail.com")
    viewModel.onPasswordChange("secret")
    assertFalse(state.isEmailValid)
    assertFalse(state.canSubmit)

    viewModel.onEmailChange("john@epfl.ch")
    assertTrue(state.canSubmit)

    viewModel.onPasswordChange(" ")
    assertFalse(state.canSubmit)
  }

  @Test
  fun exposesBlankNameAndPassword() {
    assertTrue(state.isNameBlank)
    assertTrue(state.isPasswordBlank)

    viewModel.onNameChange("  ")
    viewModel.onPasswordChange("  ")
    assertTrue(state.isNameBlank)
    assertTrue(state.isPasswordBlank)

    viewModel.onNameChange("John")
    viewModel.onPasswordChange("secret")
    assertFalse(state.isNameBlank)
    assertFalse(state.isPasswordBlank)
  }

  @Test
  fun signUpAlsoNeedsName() {
    viewModel.switchMode(AuthMode.SIGN_UP)
    fillLogIn()
    assertFalse(state.canSubmit)

    viewModel.onNameChange("John")
    assertTrue(state.canSubmit)
  }

  @Test
  fun submitWithInvalidFieldsOnlyMarksTheAttempt() {
    viewModel.onEmailChange("john@gmail.com")

    viewModel.submit()

    assertTrue(state.hasAttemptedSubmit)
    assertTrue(repository.calls.isEmpty())
  }

  @Test
  fun switchingModeKeepsFieldsAndClearsErrors() {
    fillLogIn()
    repository.signInResult = AuthResult.Failure(AuthError.WRONG_CREDENTIALS)
    viewModel.submit()

    viewModel.switchMode(AuthMode.SIGN_UP)

    assertEquals(AuthMode.SIGN_UP, state.mode)
    assertEquals("john@epfl.ch", state.email)
    assertNull(state.error)
    assertFalse(state.hasAttemptedSubmit)
  }

  @Test
  fun editingAnyFieldClearsTheError() {
    fillLogIn()
    repository.signInResult = AuthResult.Failure(AuthError.WRONG_CREDENTIALS)

    viewModel.submit()
    viewModel.onPasswordChange("secret2")
    assertNull(state.error)

    viewModel.submit()
    viewModel.onEmailChange("jane@epfl.ch")
    assertNull(state.error)

    viewModel.submit()
    viewModel.onNameChange("Jane")
    assertNull(state.error)
  }

  // ---------- log in ----------

  @Test
  fun logInSuccessSignsIn() {
    fillLogIn()

    viewModel.submit()

    assertEquals(listOf("signIn(john@epfl.ch, secret)"), repository.calls)
    assertTrue(state.isSignedIn)
    assertFalse(state.isLoading)
    assertNull(state.error)
  }

  @Test
  fun logInShowsLoadingAndIgnoresSecondSubmitWhileWaiting() {
    val gate = CompletableDeferred<Unit>()
    repository.gate = gate
    fillLogIn()

    viewModel.submit()
    assertTrue(state.isLoading)
    assertFalse(state.canSubmit)
    viewModel.submit()

    gate.complete(Unit)
    assertEquals(1, repository.calls.size)
    assertFalse(state.isLoading)
    assertTrue(state.isSignedIn)
  }

  @Test
  fun logInFailureShowsTheError() {
    repository.signInResult = AuthResult.Failure(AuthError.WRONG_CREDENTIALS)
    fillLogIn()

    viewModel.submit()

    assertEquals(AuthError.WRONG_CREDENTIALS, state.error)
    assertFalse(state.isSignedIn)
    assertFalse(state.isAwaitingVerification)
  }

  @Test
  fun logInWithUnverifiedEpflEmailGoesToCheckInbox() {
    repository.signInResult = AuthResult.Failure(AuthError.EMAIL_NOT_VERIFIED)
    fillLogIn()

    viewModel.submit()

    assertTrue(state.isAwaitingVerification)
    assertEquals(AuthError.EMAIL_NOT_VERIFIED, state.error)
  }

  @Test
  fun logInWithNonEpflEmailNeverReachesCheckInbox() {
    viewModel.onEmailChange("john@gmail.com")
    viewModel.onPasswordChange("secret")

    viewModel.submit()

    assertTrue(state.hasAttemptedSubmit)
    assertFalse(state.isAwaitingVerification)
    assertTrue(repository.calls.isEmpty())
  }

  @Test
  fun unverifiedResultForNonEpflEmailDoesNotOpenCheckInbox() {
    repository.signInResult = AuthResult.Failure(AuthError.EMAIL_NOT_VERIFIED)
    viewModel.onEmailChange("john@gmail.com")
    viewModel.onPasswordChange("secret")

    viewModel.confirmVerified()

    assertFalse(state.isAwaitingVerification)
    assertEquals(AuthError.EMAIL_NOT_VERIFIED, state.error)
  }

  // ---------- sign up ----------

  @Test
  fun signUpSuccessGoesToCheckInbox() {
    fillSignUp()

    viewModel.submit()

    assertEquals(listOf("signUp(John, john@epfl.ch, secret)"), repository.calls)
    assertTrue(state.isAwaitingVerification)
    assertFalse(state.isSignedIn)
    assertNull(state.error)
  }

  @Test
  fun signUpWhoseEmailWasNotSentStillGoesToCheckInbox() {
    repository.signUpResult = AuthResult.Failure(AuthError.VERIFICATION_EMAIL_NOT_SENT)
    fillSignUp()

    viewModel.submit()

    assertTrue(state.isAwaitingVerification)
    assertEquals(AuthError.VERIFICATION_EMAIL_NOT_SENT, state.error)
  }

  @Test
  fun signUpWhoseNameWasNotSavedStillGoesToCheckInbox() {
    repository.signUpResult = AuthResult.Failure(AuthError.NAME_NOT_SAVED)
    fillSignUp()

    viewModel.submit()

    assertTrue(state.isAwaitingVerification)
    assertEquals(AuthError.NAME_NOT_SAVED, state.error)
  }

  @Test
  fun signUpWithBadEmailShowsTheErrorAndNeverReachesCheckInbox() {
    viewModel.switchMode(AuthMode.SIGN_UP)
    viewModel.onNameChange("John")
    viewModel.onEmailChange("john@gmail.com")
    viewModel.onPasswordChange("secret")

    viewModel.submit()

    assertTrue(state.hasAttemptedSubmit)
    assertFalse(state.isEmailValid)
    assertFalse(state.isAwaitingVerification)
    assertTrue(repository.calls.isEmpty())
  }

  @Test
  fun signUpRejectedDomainStaysOnTheForm() {
    repository.signUpResult = AuthResult.Failure(AuthError.INVALID_DOMAIN)
    fillSignUp()

    viewModel.submit()

    assertFalse(state.isAwaitingVerification)
    assertEquals(AuthError.INVALID_DOMAIN, state.error)
  }

  @Test
  fun signUpFailureStaysOnTheForm() {
    repository.signUpResult = AuthResult.Failure(AuthError.EMAIL_ALREADY_IN_USE)
    fillSignUp()

    viewModel.submit()

    assertFalse(state.isAwaitingVerification)
    assertEquals(AuthError.EMAIL_ALREADY_IN_USE, state.error)
  }

  // ---------- check your inbox ----------

  @Test
  fun resendSucceeds() {
    fillSignUp()
    viewModel.submit()
    advanceSeconds(60)

    viewModel.resendVerificationEmail()

    assertEquals("resendVerificationEmail(john@epfl.ch, secret)", repository.calls.last())
    assertTrue(state.isVerificationEmailResent)
    assertNull(state.error)
  }

  @Test
  fun resendFailureShowsTheError() {
    repository.resendResult = AuthResult.Failure(AuthError.TOO_MANY_REQUESTS)
    fillSignUp()
    viewModel.submit()
    advanceSeconds(60)

    viewModel.resendVerificationEmail()

    assertFalse(state.isVerificationEmailResent)
    assertEquals(AuthError.TOO_MANY_REQUESTS, state.error)
  }

  @Test
  fun resendIsIgnoredWhileLoading() {
    val gate = CompletableDeferred<Unit>()
    fillSignUp()
    viewModel.submit()
    advanceSeconds(60)
    repository.gate = gate

    viewModel.resendVerificationEmail()
    viewModel.resendVerificationEmail()
    gate.complete(Unit)

    assertEquals(1, repository.calls.count { it.startsWith("resend") })
  }

  @Test
  fun confirmVerifiedSignsInOnceVerified() {
    fillSignUp()
    viewModel.submit()

    viewModel.confirmVerified()

    assertEquals("signIn(john@epfl.ch, secret)", repository.calls.last())
    assertTrue(state.isSignedIn)
    assertFalse(state.isAwaitingVerification)
  }

  @Test
  fun confirmVerifiedBeforeVerifyingStaysOnCheckInbox() {
    fillSignUp()
    viewModel.submit()
    repository.signInResult = AuthResult.Failure(AuthError.EMAIL_NOT_VERIFIED)

    viewModel.confirmVerified()

    assertTrue(state.isAwaitingVerification)
    assertEquals(AuthError.EMAIL_NOT_VERIFIED, state.error)
    assertFalse(state.isSignedIn)
  }

  @Test
  fun confirmVerifiedIsIgnoredWhileLoading() {
    val gate = CompletableDeferred<Unit>()
    fillSignUp()
    viewModel.submit()
    repository.gate = gate

    viewModel.confirmVerified()
    viewModel.confirmVerified()
    gate.complete(Unit)

    assertEquals(1, repository.calls.count { it.startsWith("signIn") })
  }

  @Test
  fun backToFormFromLogInReturnsToLogIn() {
    repository.signInResult = AuthResult.Failure(AuthError.EMAIL_NOT_VERIFIED)
    fillLogIn()
    viewModel.submit()

    viewModel.backToForm()

    assertEquals(AuthMode.LOG_IN, state.mode)
    assertFalse(state.isAwaitingVerification)
    assertNull(state.error)
  }

  @Test
  fun backToFormReturnsToSignUpKeepingTheFields() {
    fillSignUp()
    viewModel.submit()
    advanceSeconds(60)
    viewModel.resendVerificationEmail()

    viewModel.backToForm()

    assertEquals(AuthMode.SIGN_UP, state.mode)
    assertFalse(state.isAwaitingVerification)
    assertFalse(state.isVerificationEmailResent)
    assertFalse(state.hasAttemptedSubmit)
    assertEquals("John", state.name)
    assertEquals("john@epfl.ch", state.email)
  }

  @Test
  fun backToFormIsIgnoredWhileARequestRuns() {
    repository.signInResult = AuthResult.Failure(AuthError.EMAIL_NOT_VERIFIED)
    fillLogIn()
    viewModel.submit()
    val gate = CompletableDeferred<Unit>()
    repository.gate = gate

    viewModel.confirmVerified()
    viewModel.backToForm()

    assertTrue(state.isAwaitingVerification)
    gate.complete(Unit)
    assertTrue(state.isAwaitingVerification)
    assertEquals(AuthError.EMAIL_NOT_VERIFIED, state.error)
  }

  // ---------- resend countdown ----------

  @Test
  fun signUpDisablesResendFor60Seconds() {
    fillSignUp()
    viewModel.submit()

    assertEquals(60, state.resendCooldownSeconds)
    assertFalse(state.canResend)

    advanceSeconds(1)
    assertEquals(59, state.resendCooldownSeconds)

    advanceSeconds(58)
    assertEquals(1, state.resendCooldownSeconds)
    assertFalse(state.canResend)

    advanceSeconds(1)
    assertEquals(0, state.resendCooldownSeconds)
    assertTrue(state.canResend)
  }

  @Test
  fun resendIsIgnoredDuringTheCountdown() {
    fillSignUp()
    viewModel.submit()
    advanceSeconds(30)

    viewModel.resendVerificationEmail()

    assertEquals(0, repository.calls.count { it.startsWith("resend") })
    assertEquals(30, state.resendCooldownSeconds)
  }

  @Test
  fun resendSuccessRestartsTheCountdown() {
    fillSignUp()
    viewModel.submit()
    advanceSeconds(60)

    viewModel.resendVerificationEmail()

    assertEquals(60, state.resendCooldownSeconds)
    advanceSeconds(1)
    assertEquals(59, state.resendCooldownSeconds)
  }

  @Test
  fun resendFailureDoesNotStartTheCountdown() {
    repository.resendResult = AuthResult.Failure(AuthError.TOO_MANY_REQUESTS)
    fillSignUp()
    viewModel.submit()
    advanceSeconds(60)

    viewModel.resendVerificationEmail()

    assertEquals(0, state.resendCooldownSeconds)
    assertTrue(state.canResend)
  }

  @Test
  fun signUpWhoseEmailWasNotSentCanResendRightAway() {
    repository.signUpResult = AuthResult.Failure(AuthError.VERIFICATION_EMAIL_NOT_SENT)
    fillSignUp()
    viewModel.submit()

    assertTrue(state.isAwaitingVerification)
    assertEquals(0, state.resendCooldownSeconds)
    assertTrue(state.canResend)
  }

  @Test
  fun logInWithUnverifiedEmailCanResendRightAway() {
    repository.signInResult = AuthResult.Failure(AuthError.EMAIL_NOT_VERIFIED)
    fillLogIn()
    viewModel.submit()

    assertTrue(state.isAwaitingVerification)
    assertEquals(0, state.resendCooldownSeconds)

    viewModel.resendVerificationEmail()

    assertEquals("resendVerificationEmail(john@epfl.ch, secret)", repository.calls.last())
  }

  @Test
  fun backToFormKeepsTheCountdownSoLoggingInAgainCannotResendRightAway() {
    fillSignUp()
    viewModel.submit()
    advanceSeconds(10)

    viewModel.backToForm()
    advanceSeconds(1)
    assertEquals(49, state.resendCooldownSeconds)

    repository.signInResult = AuthResult.Failure(AuthError.EMAIL_NOT_VERIFIED)
    viewModel.switchMode(AuthMode.LOG_IN)
    viewModel.submit()

    assertTrue(state.isAwaitingVerification)
    assertEquals(49, state.resendCooldownSeconds)
    assertFalse(state.canResend)
  }

  @Test
  fun signUpWhoseNameWasNotSavedStartsTheCountdown() {
    repository.signUpResult = AuthResult.Failure(AuthError.NAME_NOT_SAVED)
    fillSignUp()
    viewModel.submit()

    assertTrue(state.isAwaitingVerification)
    assertEquals(60, state.resendCooldownSeconds)
    assertFalse(state.canResend)
  }

  @Test
  fun canResendNeedsNoLoadingAndNoCountdown() {
    assertTrue(AuthUiState().canResend)
    assertFalse(AuthUiState(isLoading = true).canResend)
    assertFalse(AuthUiState(resendCooldownSeconds = 5).canResend)
  }

  // ---------- unexpected failures ----------

  @Test
  fun unexpectedExceptionShowsUnknownErrorInsteadOfCrashing() {
    repository.exception = IllegalStateException("not an AuthResult")
    fillLogIn()

    viewModel.submit()

    assertEquals(AuthError.UNKNOWN, state.error)
    assertFalse(state.isLoading)
    assertFalse(state.isSignedIn)
  }

  @Test
  fun cancellationIsNotShownAsAnError() {
    repository.exception = CancellationException("screen closed")
    fillLogIn()

    viewModel.submit()

    assertNull(state.error)
    assertFalse(state.isLoading)
  }

  // ---------- factory ----------

  @Test
  fun factoryCreatesTheViewModel() {
    val created = AuthViewModel.Factory(repository).create(AuthViewModel::class.java)

    assertTrue(created is AuthViewModel)
  }

  @Test
  fun factoryRejectsOtherViewModels() {
    class Other : ViewModel()

    assertThrows(IllegalArgumentException::class.java) {
      AuthViewModel.Factory(repository).create(Other::class.java)
    }
  }
}
