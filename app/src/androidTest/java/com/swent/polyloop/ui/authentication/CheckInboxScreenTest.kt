// Made with Claude.

package com.swent.polyloop.ui.authentication

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.model.auth.AuthError
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.auth.AuthResult
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.theme.PolyLoopTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI tests for CheckInboxScreen and its stateless CheckInboxContent. */
@RunWith(AndroidJUnit4::class)
class CheckInboxScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val email = "jane.doe@epfl.ch"

  private val repository = VerificationAuthRepository()
  private lateinit var viewModel: AuthViewModel
  private var signIns = 0
  private var backToForms = 0

  /**
   * Shows the screen backed by a real [AuthViewModel], reached the same way as in the app: logging
   * in with an unverified EPFL email.
   */
  private fun showScreen() {
    viewModel =
        AuthViewModel(repository).apply {
          onEmailChange(email)
          onPasswordChange("secret")
          submit()
        }
    composeTestRule.setContent {
      PolyLoopTheme {
        CheckInboxScreen(
            viewModel = viewModel,
            onSignIn = { signIns++ },
            onBackToForm = { backToForms++ },
        )
      }
    }
    composeTestRule.waitForIdle()
  }

  /** Shows only the stateless content, for states that are hard to reach in real time. */
  private fun showContent(state: AuthUiState) {
    composeTestRule.setContent {
      PolyLoopTheme {
        CheckInboxContent(state = state, onVerified = {}, onResend = {}, onBack = {})
      }
    }
  }

  @Test
  fun showsTheEmailAndSignsInOnceVerified() {
    showScreen()
    composeTestRule.onNodeWithTag(C.Tag.auth_check_inbox_screen).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.auth_check_inbox_body).assertTextContains(email, true)

    repository.verified = true
    composeTestRule.onNodeWithTag(C.Tag.auth_verified_button).performClick()

    composeTestRule.runOnIdle { assertEquals(1, signIns) }
  }

  @Test
  fun notVerifiedYetShowsAnErrorThenResendConfirmsAndStartsTheCountdown() {
    showScreen()
    composeTestRule.onNodeWithTag(C.Tag.auth_verified_button).performClick()
    composeTestRule
        .onNodeWithTag(C.Tag.auth_error)
        .assertTextEquals(context.getString(R.string.auth_error_email_not_verified))

    // An unverified log-in sent no email, so Resend is available right away.
    composeTestRule
        .onNodeWithTag(C.Tag.auth_resend_button)
        .assertIsEnabled()
        .assertTextEquals(context.getString(R.string.auth_resend_email))
        .performClick()

    composeTestRule
        .onNodeWithTag(C.Tag.auth_email_resent)
        .assertTextEquals(context.getString(R.string.auth_email_resent))
    composeTestRule.onNodeWithTag(C.Tag.auth_error).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.auth_resend_button).assertIsNotEnabled()
    composeTestRule.runOnIdle {
      assertEquals(1, repository.resendCalls)
      assertEquals(0, signIns)
    }
  }

  @Test
  fun backReturnsToTheForm() {
    showScreen()

    composeTestRule.onNodeWithTag(C.Tag.auth_back_to_form_button).performClick()

    composeTestRule.runOnIdle {
      assertEquals(1, backToForms)
      assertFalse(viewModel.uiState.value.isAwaitingVerification)
    }
  }

  @Test
  fun countdownDisablesResendAndShowsSecondsLeft() {
    showContent(AuthUiState(email = email, resendCooldownSeconds = 42))

    composeTestRule
        .onNodeWithTag(C.Tag.auth_resend_button)
        .assertIsNotEnabled()
        .assertTextEquals(context.getString(R.string.auth_resend_email_countdown, 42))
    composeTestRule.onNodeWithTag(C.Tag.auth_verified_button).assertIsEnabled()
  }

  @Test
  fun loadingDisablesTheButtonsAndBack() {
    showContent(AuthUiState(email = email, isLoading = true))

    composeTestRule.onNodeWithTag(C.Tag.auth_verified_button).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.auth_resend_button).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.auth_back_to_form_button).assertIsNotEnabled()
  }

  @Test
  fun longErrorGrowsInsteadOfBeingCutOff() {
    composeTestRule.setContent {
      PolyLoopTheme {
        // Narrow enough for the message to wrap over several lines.
        AuthMessage(
            error = AuthError.VERIFICATION_EMAIL_NOT_SENT,
            centered = true,
            modifier = Modifier.width(100.dp),
        )
      }
    }

    // A fixed 36dp area used to clip it to about two lines.
    composeTestRule.onNodeWithTag(C.Tag.auth_error).assertHeightIsAtLeast(48.dp)
  }
}

/** Fails sign-in as unverified until [verified] is set; counts resent emails. */
private class VerificationAuthRepository : AuthRepository {
  var verified = false
  var resendCalls = 0
    private set

  override suspend fun signIn(email: String, password: String): AuthResult<AuthUser> =
      if (verified) AuthResult.Success(AuthUser("uid", email, "Jane"))
      else AuthResult.Failure(AuthError.EMAIL_NOT_VERIFIED)

  override suspend fun resendVerificationEmail(email: String, password: String): AuthResult<Unit> {
    resendCalls++
    return AuthResult.Success(Unit)
  }

  override suspend fun signUp(name: String, email: String, password: String) =
      AuthResult.Success(Unit)

  override fun getCurrentUser(): AuthUser? = null

  override fun signOut() = Unit
}
