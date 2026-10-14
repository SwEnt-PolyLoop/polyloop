// Made with Claude.

package com.swent.polyloop.ui.authentication

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.model.auth.AuthError
import com.swent.polyloop.model.auth.AuthResult
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.theme.PolyLoopTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI tests for CheckInboxScreen and its stateless CheckInboxContent. */
@RunWith(AndroidJUnit4::class)
class CheckInboxScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val email = "jane.doe@epfl.ch"

  // Unverified until a test sets a successful signInResult.
  private val repository =
      FakeAuthRepository(signInResult = AuthResult.Failure(AuthError.EMAIL_NOT_VERIFIED))
  private lateinit var viewModel: AuthViewModel
  private var signIns = 0
  private var backToForms = 0
  private lateinit var backDispatcher: OnBackPressedDispatcher

  // Owns the ViewModel so tearDown can clear it and stop the resend countdown.
  private val viewModelStore = ViewModelStore()

  @After
  fun tearDown() {
    viewModelStore.clear()
  }

  /**
   * Shows the screen backed by a real [AuthViewModel], reached the same way as in the app: logging
   * in with an unverified EPFL email.
   */
  private fun showScreen(typedEmail: String = email) {
    viewModel =
        ViewModelProvider(viewModelStore, AuthViewModel.Factory(repository))[
                AuthViewModel::class.java]
            .apply {
              onEmailChange(typedEmail)
              onPasswordChange("secret")
              submit()
            }
    composeTestRule.setContent {
      backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
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

    repository.signInResult = AuthResult.Success(AuthUser("uid", email, "Jane"))
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
      assertEquals(1, repository.calls.count { it.startsWith("resendVerificationEmail") })
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
  fun showsTheNormalizedEmailTheVerificationWentTo() {
    showScreen(typedEmail = " Jane.Doe@EPFL.ch ")

    composeTestRule.onNodeWithTag(C.Tag.auth_check_inbox_body).assertTextContains(email, true)
  }

  @Test
  fun systemBackDuringARequestStaysOnTheScreen() {
    showScreen()
    val gate = CompletableDeferred<Unit>()
    repository.gate = gate
    try {
      composeTestRule.onNodeWithTag(C.Tag.auth_verified_button).performClick()

      // Through the dispatcher, as Espresso.pressBack() waits for window focus and timed out on CI.
      composeTestRule.runOnUiThread { backDispatcher.onBackPressed() }

      composeTestRule.runOnIdle {
        assertEquals(0, backToForms)
        assertTrue(viewModel.uiState.value.isAwaitingVerification)
      }
    } finally {
      gate.complete(Unit)
    }
  }

  @Test
  fun shortScreenScrollsToTheButtons() {
    composeTestRule.setContent {
      PolyLoopTheme {
        // Shorter than the content, as in landscape or with a large font.
        Box(Modifier.height(240.dp)) {
          CheckInboxContent(
              state = AuthUiState(email = email, error = AuthError.VERIFICATION_EMAIL_NOT_SENT),
              onVerified = {},
              onResend = {},
              onBack = {},
          )
        }
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.auth_resend_button).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun countdownDisablesResendAndShowsSecondsLeft() {
    showContent(AuthUiState(email = email, resendCooldownSeconds = 42, resendCooldownEmail = email))

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
