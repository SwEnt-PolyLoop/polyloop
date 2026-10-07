package com.swent.polyloop.ui.authentication

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.model.auth.AuthError
import com.swent.polyloop.ui.theme.PolyLoopTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val SIGN_IN_SCREEN_TAG = "signInScreen"
private const val EMAIL_FIELD_TAG = "signInEmail"
private const val PASSWORD_FIELD_TAG = "signInPassword"
private const val LOGIN_BUTTON_TAG = "signInLoginButton"

@RunWith(AndroidJUnit4::class)
class SignInScreenTests {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun displaysSignInContentAndRunsNavigationCallbacks() {
    var signupTapped = false
    var forgotPasswordTapped = false
    setSignInContent(
        onForgotPassword = { forgotPasswordTapped = true },
        onNavSignup = { signupTapped = true },
    )

    composeTestRule.onNodeWithText("PolyLoop").assertIsDisplayed()
    composeTestRule.onNodeWithText("Borrow and lend among EPFL students.").assertIsDisplayed()
    composeTestRule.onNodeWithText("EPFL email").assertIsDisplayed()
    composeTestRule.onNodeWithText("Password").assertIsDisplayed()

    composeTestRule.onNodeWithText("Sign up").performClick()
    composeTestRule.onNodeWithText("Forgot password?").performClick()

    assertEquals(true, signupTapped)
    assertEquals(true, forgotPasswordTapped)
  }

  @Test
  fun blankSubmissionShowsRequiredEmailAndPasswordMessages() {
    var submitted = false
    setSignInContent(onLogin = { _, _ -> submitted = true })

    loginButton().assertIsEnabled()
    loginButton().performClick()

    composeTestRule.onNodeWithText("Please enter your email address.").assertIsDisplayed()
    composeTestRule.onNodeWithText("Please enter your password.").assertIsDisplayed()
    assertEquals(false, submitted)
  }

  @Test
  fun malformedOrNonEpflAddressesCannotBeSubmitted() {
    var submitted = false
    setSignInContent(onLogin = { _, _ -> submitted = true })
    passwordField().performTextInput("password123")

    listOf(
        "student@gmail.com",
        "student@notepfl.ch",
        "student@epfl.ch.evil",
        "student@@epfl.ch",
    ).forEachIndexed { index, email ->
      if (index > 0) emailField().performTextClearance()
      emailField().performTextInput(email)

      loginButton().performClick()

      composeTestRule.onNodeWithText("Use your @epfl.ch address.").assertIsDisplayed()
      assertEquals(false, submitted)
    }
  }

  @Test
  fun validSubmissionTrimsEmailAndRequiresPassword() {
    var submittedEmail: String? = null
    var submittedPassword: String? = null
    setSignInContent(
        onLogin = { email, password ->
          submittedEmail = email
          submittedPassword = password
        }
    )
    emailField().performTextInput("  student@epfl.ch  ")

    loginButton().performClick()

    composeTestRule.onNodeWithText("Please enter your password.").assertIsDisplayed()
    assertNull(submittedEmail)

    passwordField().performTextInput("password123")
    composeTestRule.onAllNodes(hasText("Please enter your password.")).assertCountEquals(0)
    loginButton().performClick()

    assertEquals("student@epfl.ch", submittedEmail)
    assertEquals("password123", submittedPassword)
  }

  @Test
  fun epflDomainIsCaseInsensitive() {
    var submittedEmail: String? = null
    setSignInContent(onLogin = { email, _ -> submittedEmail = email })
    emailField().performTextInput("student@EPFL.CH")
    passwordField().performTextInput("password123")

    loginButton().performClick()

    assertEquals("student@epfl.ch", submittedEmail)
  }

  @Test
  fun invalidEmailShowsGuidanceWhileEmailFieldIsFocused() {
    setSignInContent()

    emailField().performClick()

    composeTestRule.onNodeWithText("Use your @epfl.ch address.").assertIsDisplayed()
  }

  @Test
  fun displaysOnlyMessagesForLoginRelatedAuthErrors() {
    val authError = mutableStateOf<AuthError?>(null)
    composeTestRule.setContent {
      PolyLoopTheme {
        Box(modifier = Modifier.testTag(SIGN_IN_SCREEN_TAG)) {
          SignInScreen(authError = authError.value)
        }
      }
    }
    composeTestRule.onNodeWithTag(SIGN_IN_SCREEN_TAG).assertIsDisplayed()

    val messages =
        listOf(
            AuthError.WRONG_CREDENTIALS to "The email or password is incorrect.",
            AuthError.WEAK_PASSWORD to "Your password is too weak.",
            AuthError.EMAIL_NOT_VERIFIED to "Please verify your email address.",
            AuthError.TOO_MANY_REQUESTS to "Too many attempts. Please try again later.",
            AuthError.NETWORK to "A network error occurred. Please try again.",
            AuthError.UNKNOWN to "An unexpected error occurred. Please try again.",
        )
    messages.forEach { (error, message) ->
      composeTestRule.runOnIdle { authError.value = error }
      composeTestRule.onNodeWithText(message).assertIsDisplayed()
    }

    val errorsWithoutWarning: List<AuthError?> =
        listOf(
            AuthError.NAME_REQUIRED,
            AuthError.INVALID_DOMAIN,
            AuthError.EMAIL_ALREADY_IN_USE,
            AuthError.VERIFICATION_EMAIL_NOT_SENT,
            AuthError.NAME_NOT_SAVED,
        ) + null
    errorsWithoutWarning.forEach { error ->
      composeTestRule.runOnIdle { authError.value = error }
      messages.forEach { (_, message) ->
        composeTestRule.onAllNodes(hasText(message)).assertCountEquals(0)
      }
    }
  }

  private fun setSignInContent(
      onLogin: (email: String, password: String) -> Unit = { _, _ -> },
      authError: AuthError? = null,
      onForgotPassword: () -> Unit = {},
      onNavSignup: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      PolyLoopTheme {
        Box(modifier = Modifier.testTag(SIGN_IN_SCREEN_TAG)) {
          SignInScreen(
              onLogin = onLogin,
              authError = authError,
              onForgotPassword = onForgotPassword,
              onNavSignup = onNavSignup,
          )
        }
      }
    }
    composeTestRule.onNodeWithTag(SIGN_IN_SCREEN_TAG).assertIsDisplayed()
  }

  private fun emailField() = composeTestRule.onNodeWithTag(EMAIL_FIELD_TAG)

  private fun passwordField() = composeTestRule.onNodeWithTag(PASSWORD_FIELD_TAG)

  private fun loginButton() = composeTestRule.onNodeWithTag(LOGIN_BUTTON_TAG)
}
