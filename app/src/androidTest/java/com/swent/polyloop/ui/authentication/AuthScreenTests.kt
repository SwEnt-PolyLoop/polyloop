// Made with ChatGPT.
package com.swent.polyloop.ui.authentication

import AuthScreen
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.model.auth.AuthError
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.auth.AuthResult
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.ui.theme.PolyLoopTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI tests for AuthScreen. No Firebase, network, sleeps or coordinate-based gestures. */
@RunWith(AndroidJUnit4::class)
class AuthScreenTests {
  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var repository: FakeAuthRepository
  private lateinit var viewModel: AuthViewModel
  private var signedInNavigations = 0
  private var verificationNavigations = 0

  @Before
  fun setUp() {
    repository = FakeAuthRepository()
    viewModel = AuthViewModel(repository)
    signedInNavigations = 0
    verificationNavigations = 0
    composeTestRule.setContent {
      PolyLoopTheme {
        AuthScreen(
            onSignIn = { signedInNavigations++ },
            onNavigateToVerification = { verificationNavigations++ },
            viewModel = viewModel,
        )
      }
    }
    composeTestRule.waitForIdle()
  }

  @Test
  fun loginIsShownInitially() {
    composeTestRule.onNodeWithText("Borrow and lend among EPFL students.").assertExists()
    composeTestRule.onNodeWithTag(LOGIN_TAB).assertExists()
    composeTestRule.onNodeWithTag(SIGNUP_TAB).assertExists()
    composeTestRule.onNodeWithTag(EMAIL).assertExists()
    composeTestRule.onNodeWithTag(PASSWORD).assertExists()
    composeTestRule.onNodeWithTag(NAME).assertDoesNotExist()
    composeTestRule.onNodeWithTag(LOGIN_BUTTON).assertIsEnabled()
    composeTestRule.onNodeWithTag(SIGNUP_BUTTON).assertDoesNotExist()
    composeTestRule.onNodeWithTag(AUTH_ERROR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(EMAIL_ERROR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(PASSWORD_ERROR).assertDoesNotExist()
  }

  @Test
  fun signUpTabShowsNameAndSignUpButton() {
    tap(SIGNUP_TAB)
    composeTestRule.onNodeWithTag(NAME).assertExists()
    composeTestRule.onNodeWithTag(SIGNUP_BUTTON).assertIsEnabled()
    composeTestRule.onNodeWithTag(LOGIN_BUTTON).assertDoesNotExist()
  }

  @Test
  fun switchingBackToLoginHidesName() {
    tap(SIGNUP_TAB)
    tap(LOGIN_TAB)
    composeTestRule.onNodeWithTag(NAME).assertDoesNotExist()
    composeTestRule.onNodeWithTag(LOGIN_BUTTON).assertExists()
  }

  @Test
  fun tabsPreserveEmailAndPassword() {
    type(EMAIL, VALID_EMAIL)
    type(PASSWORD, VALID_PASSWORD)
    tap(SIGNUP_TAB)
    tap(LOGIN_TAB)
    tap(LOGIN_BUTTON)
    composeTestRule.runOnIdle {
      assertEquals(VALID_EMAIL, repository.loginEmail)
      assertEquals(VALID_PASSWORD, repository.loginPassword)
      assertEquals(1, repository.loginCalls)
    }
  }

  @Test
  fun emailErrorInitiallyHiddenAndShownOnFocus() {
    composeTestRule.onNodeWithTag(EMAIL_ERROR).assertDoesNotExist()
    tap(EMAIL)
    composeTestRule.onNodeWithTag(EMAIL_ERROR).assertTextEquals("Please enter your email address.")
  }

  @Test
  fun invalidEmailShowsDomainMessageAndCorrectionRemovesIt() {
    tap(EMAIL)
    type(EMAIL, "test@gmail.com")
    composeTestRule.onNodeWithTag(EMAIL_ERROR).assertTextEquals("Use your @epfl.ch address.")
    clear(EMAIL)
    type(EMAIL, VALID_EMAIL)
    composeTestRule.onNodeWithTag(EMAIL_ERROR).assertDoesNotExist()
  }

  @Test
  fun emptyLoginShowsOnlyEmailAndPasswordValidation() {
    tap(LOGIN_BUTTON)
    composeTestRule.onNodeWithTag(EMAIL_ERROR).assertTextEquals("Please enter your email address.")
    composeTestRule.onNodeWithTag(PASSWORD_ERROR).assertTextEquals("Please enter your password.")
    composeTestRule.onNodeWithTag(NAME_ERROR).assertDoesNotExist()
    composeTestRule.runOnIdle { assertEquals(0, repository.loginCalls) }
  }

  @Test
  fun invalidDomainDoesNotSubmit() {
    type(EMAIL, "test@gmail.com")
    type(PASSWORD, VALID_PASSWORD)
    tap(LOGIN_BUTTON)
    composeTestRule.onNodeWithTag(EMAIL_ERROR).assertTextEquals("Use your @epfl.ch address.")
    composeTestRule.runOnIdle { assertEquals(0, repository.loginCalls) }
  }

  @Test
  fun fixingFieldsAfterFailedSubmitClearsValidationMessages() {
    tap(LOGIN_BUTTON)
    type(EMAIL, VALID_EMAIL)
    composeTestRule.onNodeWithTag(EMAIL_ERROR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(PASSWORD_ERROR).assertExists()
    type(PASSWORD, VALID_PASSWORD)
    composeTestRule.onNodeWithTag(PASSWORD_ERROR).assertDoesNotExist()
  }

  @Test
  fun emptySignUpShowsThreeValidationMessagesWithoutCallingRepository() {
    tap(SIGNUP_TAB)
    tap(SIGNUP_BUTTON)
    composeTestRule.onNodeWithTag(NAME_ERROR).assertTextEquals("Please enter your name.")
    composeTestRule.onNodeWithTag(EMAIL_ERROR).assertTextEquals("Please enter your email address.")
    composeTestRule.onNodeWithTag(PASSWORD_ERROR).assertTextEquals("Please enter your password.")
    composeTestRule.runOnIdle { assertEquals(0, repository.signUpCalls) }
  }

  @Test
  fun switchingModesResetsSubmitErrors() {
    tap(LOGIN_BUTTON)
    tap(SIGNUP_TAB)
    composeTestRule.onNodeWithTag(NAME_ERROR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(EMAIL_ERROR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(PASSWORD_ERROR).assertDoesNotExist()
  }

  @Test
  fun successfulLoginForwardsValuesAndNavigatesOnce() {
    fillLogin()
    tap(LOGIN_BUTTON)
    composeTestRule.runOnIdle {
      assertEquals(1, repository.loginCalls)
      assertEquals(VALID_EMAIL, repository.loginEmail)
      assertEquals(VALID_PASSWORD, repository.loginPassword)
      assertEquals(1, signedInNavigations)
      assertEquals(0, verificationNavigations)
    }
    // A harmless state update triggers recomposition, but not a second navigation.
    type(PASSWORD, "x")
    composeTestRule.runOnIdle { assertEquals(1, signedInNavigations) }
  }

  @Test
  fun successfulSignUpForwardsValuesAndNavigatesOnce() {
    fillSignUp()
    tap(SIGNUP_BUTTON)
    composeTestRule.runOnIdle {
      assertEquals(1, repository.signUpCalls)
      assertEquals(VALID_NAME, repository.signUpName)
      assertEquals(VALID_EMAIL, repository.signUpEmail)
      assertEquals(VALID_PASSWORD, repository.signUpPassword)
      assertEquals(0, signedInNavigations)
      assertEquals(1, verificationNavigations)
    }
    type(NAME, "x")
    composeTestRule.runOnIdle { assertEquals(1, verificationNavigations) }
  }

  @Test
  fun wrongCredentialsDisplayErrorWithoutNavigation() {
    repository.loginResult = AuthResult.Failure(AuthError.WRONG_CREDENTIALS)
    fillLogin()
    tap(LOGIN_BUTTON)
    composeTestRule
        .onNodeWithTag(AUTH_ERROR)
        .assertTextEquals("The email or password is incorrect.")
    composeTestRule.runOnIdle {
      assertEquals(0, signedInNavigations)
      assertEquals(0, verificationNavigations)
    }
  }

  @Test
  fun changingPasswordClearsRepositoryError() {
    repository.loginResult = AuthResult.Failure(AuthError.WRONG_CREDENTIALS)
    fillLogin()
    tap(LOGIN_BUTTON)
    composeTestRule.onNodeWithTag(AUTH_ERROR).assertExists()
    type(PASSWORD, "x")
    composeTestRule.onNodeWithTag(AUTH_ERROR).assertDoesNotExist()
  }

  @Test
  fun everyNonNavigationAuthErrorHasItsOwnMessage() {
    fillLogin()
    val cases =
        listOf(
            AuthError.WRONG_CREDENTIALS to "The email or password is incorrect.",
            AuthError.WEAK_PASSWORD to "Your password is too weak.",
            AuthError.TOO_MANY_REQUESTS to "Too many attempts. Please try again later.",
            AuthError.NETWORK to "A network error occurred. Please try again.",
            AuthError.UNKNOWN to "An unexpected error occurred. Please try again.",
            AuthError.NAME_REQUIRED to "Please enter your name.",
            AuthError.INVALID_DOMAIN to "Use your @epfl.ch address.",
            AuthError.EMAIL_ALREADY_IN_USE to "This email already has an account. Try logging in.",
            AuthError.VERIFICATION_EMAIL_NOT_SENT to
                "Account created, but the verification email could not be sent.",
            AuthError.NAME_NOT_SAVED to "Account created, but your name could not be saved.",
        )
    for ((error, message) in cases) {
      repository.loginResult = AuthResult.Failure(error)
      tap(LOGIN_BUTTON)
      composeTestRule.onNodeWithTag(AUTH_ERROR).assertTextEquals(message)
    }
    composeTestRule.runOnIdle {
      assertEquals(cases.size, repository.loginCalls)
      assertEquals(0, signedInNavigations)
      assertEquals(0, verificationNavigations)
    }
  }

  @Test
  fun unverifiedLoginDisplaysMessageAndRequestsVerificationNavigation() {
    repository.loginResult = AuthResult.Failure(AuthError.EMAIL_NOT_VERIFIED)
    fillLogin()
    tap(LOGIN_BUTTON)
    composeTestRule.onNodeWithTag(AUTH_ERROR).assertTextEquals("Please verify your email address.")
    composeTestRule.runOnIdle {
      assertEquals(0, signedInNavigations)
      assertEquals(1, verificationNavigations)
    }
  }

  @Test
  fun signUpAlreadyInUseDisplaysErrorWithoutNavigation() {
    repository.signUpResult = AuthResult.Failure(AuthError.EMAIL_ALREADY_IN_USE)
    fillSignUp()
    tap(SIGNUP_BUTTON)
    composeTestRule
        .onNodeWithTag(AUTH_ERROR)
        .assertTextEquals("This email already has an account. Try logging in.")
    composeTestRule.runOnIdle { assertEquals(0, verificationNavigations) }
  }

  @Test
  fun signUpVerificationEmailNotSentShowsErrorAndNavigates() {
    repository.signUpResult = AuthResult.Failure(AuthError.VERIFICATION_EMAIL_NOT_SENT)
    fillSignUp()
    tap(SIGNUP_BUTTON)
    composeTestRule
        .onNodeWithTag(AUTH_ERROR)
        .assertTextEquals("Account created, but the verification email could not be sent.")
    composeTestRule.runOnIdle { assertEquals(1, verificationNavigations) }
  }

  @Test
  fun signUpNameNotSavedShowsErrorAndNavigates() {
    repository.signUpResult = AuthResult.Failure(AuthError.NAME_NOT_SAVED)
    fillSignUp()
    tap(SIGNUP_BUTTON)
    composeTestRule
        .onNodeWithTag(AUTH_ERROR)
        .assertTextEquals("Account created, but your name could not be saved.")
    composeTestRule.runOnIdle { assertEquals(1, verificationNavigations) }
  }

  @Test
  fun loginButtonDisabledUntilPendingRequestCompletes() {
    val gate = CompletableDeferred<Unit>()
    repository.gate = gate
    try {
      fillLogin()
      tap(LOGIN_BUTTON)
      composeTestRule.onNodeWithTag(LOGIN_BUTTON).assertIsNotEnabled()
      composeTestRule.runOnIdle {
        assertEquals(1, repository.loginCalls)
        assertEquals(0, signedInNavigations)
      }
      gate.complete(Unit)
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag(LOGIN_BUTTON).assertIsEnabled()
      composeTestRule.runOnIdle { assertEquals(1, signedInNavigations) }
    } finally {
      gate.complete(Unit)
    }
  }

  @Test
  fun signUpButtonDisabledUntilPendingRequestCompletes() {
    val gate = CompletableDeferred<Unit>()
    repository.gate = gate
    try {
      fillSignUp()
      tap(SIGNUP_BUTTON)
      composeTestRule.onNodeWithTag(SIGNUP_BUTTON).assertIsNotEnabled()
      composeTestRule.runOnIdle { assertEquals(1, repository.signUpCalls) }
      gate.complete(Unit)
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag(SIGNUP_BUTTON).assertIsEnabled()
      composeTestRule.runOnIdle { assertEquals(1, verificationNavigations) }
    } finally {
      gate.complete(Unit)
    }
  }

  @Test
  fun unexpectedRepositoryExceptionDisplaysError() {
    repository.exception = IllegalStateException("Simulated failure")
    fillLogin()
    tap(LOGIN_BUTTON)
    composeTestRule
        .onNodeWithTag(AUTH_ERROR)
        .assertTextEquals("An unexpected error occurred. Please try again.")
    composeTestRule.runOnIdle {
      assertEquals(0, signedInNavigations)
      assertEquals(0, verificationNavigations)
    }
  }

  private fun fillLogin() {
    type(EMAIL, VALID_EMAIL)
    type(PASSWORD, VALID_PASSWORD)
  }

  private fun fillSignUp() {
    tap(SIGNUP_TAB)
    type(NAME, VALID_NAME)
    type(EMAIL, VALID_EMAIL)
    type(PASSWORD, VALID_PASSWORD)
  }

  private fun tap(tag: String) {
    composeTestRule.onNodeWithTag(tag).performScrollTo().performClick()
    composeTestRule.waitForIdle()
  }

  private fun type(tag: String, value: String) {
    composeTestRule.onNodeWithTag(tag).performScrollTo().performTextInput(value)
    composeTestRule.waitForIdle()
  }

  private fun clear(tag: String) {
    composeTestRule.onNodeWithTag(tag).performScrollTo().performTextClearance()
    composeTestRule.waitForIdle()
  }

  private companion object {
    const val NAME = "signInName"
    const val EMAIL = "signInEmail"
    const val PASSWORD = "signInPassword"
    const val LOGIN_BUTTON = "signInLoginButton"
    const val SIGNUP_BUTTON = "signInSignupButton"
    const val LOGIN_TAB = "signInLoginTab"
    const val SIGNUP_TAB = "signInSignupTab"
    const val NAME_ERROR = "signInNameError"
    const val EMAIL_ERROR = "signInEmailError"
    const val PASSWORD_ERROR = "signInPasswordError"
    const val AUTH_ERROR = "signInAuthError"
    const val VALID_NAME = "John"
    const val VALID_EMAIL = "john@epfl.ch"
    const val VALID_PASSWORD = "secret123"
  }
}

/** Test-only controllable dependency: no Firebase or real external operations. */
private class FakeAuthRepository : AuthRepository {
  var loginResult: AuthResult<AuthUser> =
      AuthResult.Success(AuthUser(uid = "test-uid", email = "john@epfl.ch", name = "John"))
  var signUpResult: AuthResult<Unit> = AuthResult.Success(Unit)
  var gate: CompletableDeferred<Unit>? = null
  var exception: RuntimeException? = null

  var loginCalls = 0
    private set

  var signUpCalls = 0
    private set

  var loginEmail: String? = null
    private set

  var loginPassword: String? = null
    private set

  var signUpName: String? = null
    private set

  var signUpEmail: String? = null
    private set

  var signUpPassword: String? = null
    private set

  override suspend fun signIn(email: String, password: String): AuthResult<AuthUser> {
    loginCalls++
    loginEmail = email
    loginPassword = password
    gate?.await()
    exception?.let { throw it }
    return loginResult
  }

  override suspend fun signUp(name: String, email: String, password: String): AuthResult<Unit> {
    signUpCalls++
    signUpName = name
    signUpEmail = email
    signUpPassword = password
    gate?.await()
    exception?.let { throw it }
    return signUpResult
  }

  override suspend fun resendVerificationEmail(
      email: String,
      password: String,
  ): AuthResult<Unit> = AuthResult.Success(Unit)

  override fun getCurrentUser(): AuthUser? = null

  override fun signOut() = Unit
}
