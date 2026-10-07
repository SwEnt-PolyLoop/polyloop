package com.swent.polyloop.ui.authentication

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.model.auth.AuthError
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.auth.AuthResult
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.ui.theme.PolyLoopTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Compose UI tests for the authentication screen's visible behavior and callbacks. */
@RunWith(AndroidJUnit4::class)
class AuthScreenTests {

  @get:Rule val composeTestRule = createComposeRule()

  private val repository = FakeAuthRepository()
  private var signInCallbackCalls = mutableStateOf(0)
  private var verificationNavigationCalls = mutableStateOf(0)
  private var forgotPasswordCallbackCalls = mutableStateOf(0)

  // --- Mode-dependent layout ---

  @Test
  fun logInModeShowsLogInFieldsAndForgotPassword() {
    setContentWith(AuthMode.LOG_IN)

    composeTestRule.onNodeWithText("EPFL email").assertIsDisplayed()
    composeTestRule.onNodeWithText("Password").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Name").assertCountEquals(0)
    composeTestRule.onNodeWithText("Forgot password?").assertIsDisplayed()
    composeTestRule.onNodeWithTag("signInLoginButton").assertIsDisplayed()
  }

  @Test
  fun signUpModeShowsNameFieldAndHidesForgotPassword() {
    setContentWith(AuthMode.SIGN_UP)

    composeTestRule.onNodeWithText("Name").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Forgot password?").assertCountEquals(0)
    composeTestRule.onNodeWithTag("signInSignupButton").assertIsDisplayed()
  }

  // --- Field callbacks and mode switching ---

  @Test
  fun clickingForgotPasswordCallsItsCallback() {
    setContentWith(AuthMode.LOG_IN)

    composeTestRule.onNodeWithText("Forgot password?").performClick()

    composeTestRule.runOnIdle { assertEquals(1, forgotPasswordCallbackCalls.value) }
  }

  @Test
  fun clickingSignUpTabSwitchesModeAndUpdatesTheForm() {
    setContentWith(AuthMode.LOG_IN)

    composeTestRule.onNodeWithTag("signInSignupTab").performClick()

    composeTestRule.onNodeWithText("Name").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Forgot password?").assertCountEquals(0)
  }

  // --- Validation ---

  @Test
  fun emptyEmailShowsContextualValidationOnFocusAndSubmit() {
    setContentWith(AuthMode.LOG_IN)

    composeTestRule.onNodeWithTag("signInEmail").performClick()
    composeTestRule.onNodeWithText("Use your @epfl.ch address.").assertIsDisplayed()
    composeTestRule.onNodeWithTag("signInPassword").performClick()
    composeTestRule.onAllNodesWithText("Use your @epfl.ch address.").assertCountEquals(0)

    composeTestRule.onNodeWithTag("signInLoginButton").performClick()
    composeTestRule.onNodeWithText("Please enter your email address.").assertIsDisplayed()
  }

  @Test
  fun nonEpflEmailShowsDomainValidationAfterSubmit() {
    setContentWith(AuthMode.LOG_IN)

    composeTestRule.onNodeWithTag("signInEmail").performTextInput("test@gmail.com")
    composeTestRule.onNodeWithTag("signInLoginButton").performClick()

    composeTestRule.onNodeWithText("Use your @epfl.ch address.").assertIsDisplayed()
    composeTestRule.runOnIdle { assertNull(repository.signInArguments) }
  }

  @Test
  fun validEpflEmailShowsNoEmailValidationError() {
    setContentWith(AuthMode.LOG_IN)

    composeTestRule.onNodeWithTag("signInEmail").performTextInput("test.user@epfl.ch")
    composeTestRule.onNodeWithTag("signInPassword").performClick()

    composeTestRule.onAllNodesWithText("Please enter your email address.").assertCountEquals(0)
    composeTestRule.onAllNodesWithText("Use your @epfl.ch address.").assertCountEquals(0)
  }

  @Test
  fun logInWithEmptyFieldsShowsValidationAndDoesNotCallRepository() {
    setContentWith(AuthMode.LOG_IN)

    composeTestRule.onNodeWithTag("signInLoginButton").performClick()

    composeTestRule.onNodeWithText("Please enter your email address.").assertIsDisplayed()
    composeTestRule.onNodeWithText("Please enter your password.").assertIsDisplayed()
    composeTestRule.runOnIdle { assertNull(repository.signInArguments) }
  }

  @Test
  fun logInWithEmptyPasswordShowsValidationAndDoesNotCallRepository() {
    setContentWith(AuthMode.LOG_IN)
    composeTestRule.onNodeWithTag("signInEmail").performTextInput("prenom.nom@epfl.ch")

    composeTestRule.onNodeWithTag("signInLoginButton").performClick()

    composeTestRule.onNodeWithText("Please enter your password.").assertIsDisplayed()
    composeTestRule.runOnIdle { assertNull(repository.signInArguments) }
  }

  @Test
  fun signUpWithEmptyNameShowsValidationAndDoesNotCallRepository() {
    setContentWith(AuthMode.SIGN_UP)
    composeTestRule.onNodeWithTag("signInEmail").performTextInput("prenom.nom@epfl.ch")
    composeTestRule.onNodeWithTag("signInPassword").performTextInput("pass123")

    composeTestRule.onNodeWithTag("signInSignupButton").performClick()

    composeTestRule.onNodeWithText("Please enter your name.").assertIsDisplayed()
    composeTestRule.runOnIdle { assertNull(repository.signUpArguments) }
  }

  @Test
  fun signUpWithEmptyPasswordShowsValidationAndDoesNotCallRepository() {
    setContentWith(AuthMode.SIGN_UP)
    composeTestRule.onNodeWithTag("signInName").performTextInput("First Last")
    composeTestRule.onNodeWithTag("signInEmail").performTextInput("prenom.nom@epfl.ch")

    composeTestRule.onNodeWithTag("signInSignupButton").performClick()

    composeTestRule.onNodeWithText("Please enter your password.").assertIsDisplayed()
    composeTestRule.runOnIdle { assertNull(repository.signUpArguments) }
  }

  @Test
  fun signUpWithNonEpflEmailShowsValidationAndDoesNotCallRepository() {
    setContentWith(AuthMode.SIGN_UP)
    composeTestRule.onNodeWithTag("signInName").performTextInput("First Last")
    composeTestRule.onNodeWithTag("signInEmail").performTextInput("test@gmail.com")
    composeTestRule.onNodeWithTag("signInPassword").performTextInput("pass123")

    composeTestRule.onNodeWithTag("signInSignupButton").performClick()

    composeTestRule.onNodeWithText("Use your @epfl.ch address.").assertIsDisplayed()
    composeTestRule.runOnIdle { assertNull(repository.signUpArguments) }
  }

  // --- Successful flows ---

  @Test
  fun logInSuccessCallsRepositoryAndSignInCallback() {
    setContentWith(AuthMode.LOG_IN)
    fillValidLogInForm()

    composeTestRule.onNodeWithTag("signInLoginButton").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) { signInCallbackCalls.value == 1 }

    composeTestRule.runOnIdle {
      assertEquals(SignInArguments("prenom.nom@epfl.ch", "pass123"), repository.signInArguments)
      assertEquals(1, signInCallbackCalls.value)
    }
  }

  @Test
  fun logInPassesEnteredEmailToRepository() {
    setContentWith(AuthMode.LOG_IN)
    composeTestRule.onNodeWithTag("signInEmail").performTextInput("  prenom.nom@epfl.ch  ")
    composeTestRule.onNodeWithTag("signInPassword").performTextInput("pass123")

    composeTestRule.onNodeWithTag("signInLoginButton").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) { signInCallbackCalls.value == 1 }

    composeTestRule.runOnIdle {
      assertEquals(
          SignInArguments("  prenom.nom@epfl.ch  ", "pass123"),
          repository.signInArguments,
      )
    }
  }

  @Test
  fun signUpSuccessCallsRepositoryAndNavigatesToVerification() {
    setContentWith(AuthMode.SIGN_UP)
    fillValidSignUpForm()

    composeTestRule.onNodeWithTag("signInSignupButton").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) { verificationNavigationCalls.value == 1 }

    composeTestRule.runOnIdle {
      assertEquals(
          SignUpArguments("First Last", "prenom.nom@epfl.ch", "pass123"),
          repository.signUpArguments,
      )
      assertEquals(1, verificationNavigationCalls.value)
    }
  }

  // --- Log-in repository failures ---

  @Test
  fun wrongCredentialsResultShowsItsMessage() {
    repository.signInResult = AuthResult.Failure(AuthError.WRONG_CREDENTIALS)
    setContentWith(AuthMode.LOG_IN)
    fillValidLogInForm()

    composeTestRule.onNodeWithTag("signInLoginButton").performClick()
    waitForErrorText("The email or password is incorrect.")

    composeTestRule.runOnIdle {
      assertEquals(0, verificationNavigationCalls.value)
      assertEquals(0, signInCallbackCalls.value)
    }
  }

  @Test
  fun invalidDomainResultShowsItsMessage() {
    repository.signInResult = AuthResult.Failure(AuthError.INVALID_DOMAIN)
    setContentWith(AuthMode.LOG_IN)
    fillValidLogInForm()

    composeTestRule.onNodeWithTag("signInLoginButton").performClick()
    waitForErrorText("Use your @epfl.ch address.")

    composeTestRule.runOnIdle {
      assertEquals(0, verificationNavigationCalls.value)
      assertEquals(0, signInCallbackCalls.value)
    }
  }

  @Test
  fun weakPasswordResultShowsItsMessage() {
    repository.signInResult = AuthResult.Failure(AuthError.WEAK_PASSWORD)
    setContentWith(AuthMode.LOG_IN)
    fillValidLogInForm()

    composeTestRule.onNodeWithTag("signInLoginButton").performClick()
    waitForErrorText("Your password is too weak.")

    composeTestRule.runOnIdle {
      assertEquals(0, verificationNavigationCalls.value)
      assertEquals(0, signInCallbackCalls.value)
    }
  }

  @Test
  fun tooManyRequestsResultShowsItsMessage() {
    repository.signInResult = AuthResult.Failure(AuthError.TOO_MANY_REQUESTS)
    setContentWith(AuthMode.LOG_IN)
    fillValidLogInForm()

    composeTestRule.onNodeWithTag("signInLoginButton").performClick()
    waitForErrorText("Too many attempts. Please try again later.")

    composeTestRule.runOnIdle {
      assertEquals(0, verificationNavigationCalls.value)
      assertEquals(0, signInCallbackCalls.value)
    }
  }

  @Test
  fun networkErrorResultShowsItsMessage() {
    repository.signInResult = AuthResult.Failure(AuthError.NETWORK)
    setContentWith(AuthMode.LOG_IN)
    fillValidLogInForm()

    composeTestRule.onNodeWithTag("signInLoginButton").performClick()
    waitForErrorText("A network error occurred. Please try again.")

    composeTestRule.runOnIdle {
      assertEquals(0, verificationNavigationCalls.value)
      assertEquals(0, signInCallbackCalls.value)
    }
  }

  @Test
  fun unknownErrorResultShowsItsMessage() {
    repository.signInResult = AuthResult.Failure(AuthError.UNKNOWN)
    setContentWith(AuthMode.LOG_IN)
    fillValidLogInForm()

    composeTestRule.onNodeWithTag("signInLoginButton").performClick()
    waitForErrorText("An unexpected error occurred. Please try again.")

    composeTestRule.runOnIdle {
      assertEquals(0, verificationNavigationCalls.value)
      assertEquals(0, signInCallbackCalls.value)
    }
  }

  // --- Log-in with unverified email: navigates to verification ---

  @Test
  fun logInWithUnverifiedEpflEmailNavigatesToVerification() {
    repository.signInResult = AuthResult.Failure(AuthError.EMAIL_NOT_VERIFIED)
    setContentWith(AuthMode.LOG_IN)
    fillValidLogInForm()

    composeTestRule.onNodeWithTag("signInLoginButton").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) { verificationNavigationCalls.value == 1 }

    composeTestRule.runOnIdle { assertEquals(0, signInCallbackCalls.value) }
    composeTestRule.onNodeWithText("Please verify your email address.").assertIsDisplayed()
  }

  // --- Sign-up repository failures ---

  @Test
  fun emailAlreadyInUseResultShowsItsMessageAndDoesNotNavigate() {
    repository.signUpResult = AuthResult.Failure(AuthError.EMAIL_ALREADY_IN_USE)
    setContentWith(AuthMode.SIGN_UP)
    fillValidSignUpForm()

    composeTestRule.onNodeWithTag("signInSignupButton").performClick()
    waitForErrorText("This email already has an account. Try logging in.")

    composeTestRule.runOnIdle {
      assertEquals(0, verificationNavigationCalls.value)
      assertEquals(
          SignUpArguments("First Last", "prenom.nom@epfl.ch", "pass123"),
          repository.signUpArguments,
      )
    }
  }

  @Test
  fun nameRequiredResultShowsItsMessageAndDoesNotNavigate() {
    repository.signUpResult = AuthResult.Failure(AuthError.NAME_REQUIRED)
    setContentWith(AuthMode.SIGN_UP)
    fillValidSignUpForm()

    composeTestRule.onNodeWithTag("signInSignupButton").performClick()
    waitForErrorText("Please enter your name.")

    composeTestRule.runOnIdle { assertEquals(0, verificationNavigationCalls.value) }
  }

  // Partial failures: the account was created, so the user must still verify.

  @Test
  fun verificationEmailNotSentShowsErrorAndStillNavigatesToVerification() {
    repository.signUpResult = AuthResult.Failure(AuthError.VERIFICATION_EMAIL_NOT_SENT)
    setContentWith(AuthMode.SIGN_UP)
    fillValidSignUpForm()

    composeTestRule.onNodeWithTag("signInSignupButton").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) { verificationNavigationCalls.value == 1 }

    composeTestRule
        .onNodeWithText("Account created, but the verification email could not be sent.")
        .assertIsDisplayed()
  }

  @Test
  fun nameNotSavedShowsErrorAndStillNavigatesToVerification() {
    repository.signUpResult = AuthResult.Failure(AuthError.NAME_NOT_SAVED)
    setContentWith(AuthMode.SIGN_UP)
    fillValidSignUpForm()

    composeTestRule.onNodeWithTag("signInSignupButton").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) { verificationNavigationCalls.value == 1 }

    composeTestRule
        .onNodeWithText("Account created, but your name could not be saved.")
        .assertIsDisplayed()
  }

  // --- Idle state ---

  @Test
  fun noAuthErrorShowsNoAuthenticationErrorMessage() {
    setContentWith(AuthMode.LOG_IN)

    composeTestRule.onAllNodesWithText("The email or password is incorrect.").assertCountEquals(0)
    composeTestRule
        .onAllNodesWithText("This email already has an account. Try logging in.")
        .assertCountEquals(0)
  }

  // --- Loading state ---

  @Test
  fun loadingDisablesTheLogInButtonUntilTheCallCompletes() {
    val gate = CompletableDeferred<Unit>()
    repository.signInGate = gate
    setContentWith(AuthMode.LOG_IN)
    fillValidLogInForm()

    composeTestRule.onNodeWithTag("signInLoginButton").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) { repository.signInArguments != null }
    composeTestRule.onNodeWithTag("signInLoginButton").assertIsNotEnabled()

    gate.complete(Unit)
    composeTestRule.waitUntil(timeoutMillis = 5_000) { signInCallbackCalls.value == 1 }
    composeTestRule.onNodeWithTag("signInLoginButton").assertIsEnabled()
  }

  // --- Helpers ---

  private fun fillValidLogInForm() {
    composeTestRule.onNodeWithTag("signInEmail").performTextInput("prenom.nom@epfl.ch")
    composeTestRule.onNodeWithTag("signInPassword").performTextInput("pass123")
  }

  private fun fillValidSignUpForm() {
    composeTestRule.onNodeWithTag("signInName").performTextInput("First Last")
    composeTestRule.onNodeWithTag("signInEmail").performTextInput("prenom.nom@epfl.ch")
    composeTestRule.onNodeWithTag("signInPassword").performTextInput("pass123")
  }

  /** Waits until the error message is rendered, then asserts it is displayed. */
  private fun waitForErrorText(message: String) {
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText(message).assertIsDisplayed()
  }

  private fun setContentWith(mode: AuthMode) {
    val viewModel = AuthViewModel(repository)
    viewModel.switchMode(mode)
    composeTestRule.setContent {
      PolyLoopTheme {
        AuthScreen(
            onSignIn = { signInCallbackCalls.value++ },
            onNavigateToVerification = { verificationNavigationCalls.value++ },
            onForgotPassword = { forgotPasswordCallbackCalls.value++ },
            viewModel = viewModel,
        )
      }
    }
  }

  private class FakeAuthRepository : AuthRepository {
    var signInArguments: SignInArguments? = null
    var signUpArguments: SignUpArguments? = null
    var signInResult: AuthResult<AuthUser> =
        AuthResult.Success(
            AuthUser(uid = "user-1", email = "prenom.nom@epfl.ch", name = "First Last")
        )
    var signUpResult: AuthResult<Unit> = AuthResult.Success(Unit)
    var signInGate: CompletableDeferred<Unit>? = null

    override suspend fun signUp(name: String, email: String, password: String): AuthResult<Unit> {
      signUpArguments = SignUpArguments(name, email, password)
      return signUpResult
    }

    override suspend fun signIn(email: String, password: String): AuthResult<AuthUser> {
      signInArguments = SignInArguments(email, password)
      signInGate?.await()
      return signInResult
    }

    override suspend fun resendVerificationEmail(
        email: String,
        password: String,
    ): AuthResult<Unit> = AuthResult.Success(Unit)

    override fun getCurrentUser(): AuthUser? = null

    override fun signOut() = Unit
  }

  private data class SignInArguments(val email: String, val password: String)

  private data class SignUpArguments(val name: String, val email: String, val password: String)
}
