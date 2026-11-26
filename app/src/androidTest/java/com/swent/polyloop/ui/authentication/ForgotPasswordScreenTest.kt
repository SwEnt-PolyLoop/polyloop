// Made with Claude.

package com.swent.polyloop.ui.authentication

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.model.auth.AuthError
import com.swent.polyloop.model.auth.AuthResult
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.profile.FakeProfileRepository
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

/** UI tests for ForgotPasswordScreen and its stateless ForgotPasswordContent. */
@RunWith(AndroidJUnit4::class)
class ForgotPasswordScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val email = "jane.doe@epfl.ch"

  private val repository = FakeAuthRepository()
  private lateinit var viewModel: AuthViewModel
  private lateinit var backDispatcher: OnBackPressedDispatcher

  // Owns the ViewModel so tearDown can clear it and stop the countdown.
  private val viewModelStore = ViewModelStore()

  @After
  fun tearDown() {
    viewModelStore.clear()
  }

  /**
   * Shows the screen backed by a real [AuthViewModel], reached the same way as in the app: typing
   * [typedEmail] on the log-in form, then "Forgot password?".
   */
  private fun showScreen(typedEmail: String = email) {
    viewModel =
        ViewModelProvider(
                viewModelStore,
                AuthViewModel.Factory(repository, FakeProfileRepository()),
            )[AuthViewModel::class.java]
            .apply {
              onEmailChange(typedEmail)
              openPasswordReset()
            }
    composeTestRule.setContent {
      backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
      PolyLoopTheme { ForgotPasswordScreen(viewModel = viewModel) }
    }
    composeTestRule.waitForIdle()
  }

  /** Shows only the stateless content, for states that are hard to reach in real time. */
  private fun showContent(state: AuthUiState) {
    composeTestRule.setContent {
      PolyLoopTheme {
        ForgotPasswordContent(state = state, onEmailChange = {}, onSend = {}, onBack = {})
      }
    }
  }

  private fun tapSend() {
    composeTestRule.onNodeWithTag(C.Tag.auth_reset_send_button).performScrollTo().performClick()
    composeTestRule.waitForIdle()
  }

  @Test
  fun showsTheTypedEmailAndSendsTheResetEmail() {
    showScreen()
    composeTestRule.onNodeWithTag(C.Tag.auth_reset_screen).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.auth_email_field).assertTextContains(email)
    composeTestRule.onNodeWithTag(C.Tag.auth_reset_sent).assertDoesNotExist()

    tapSend()

    composeTestRule
        .onNodeWithTag(C.Tag.auth_reset_sent)
        .assertTextEquals(context.getString(R.string.auth_reset_sent))
    composeTestRule.onNodeWithTag(C.Tag.auth_error).assertDoesNotExist()
    composeTestRule.runOnIdle {
      assertEquals(listOf("sendPasswordResetEmail($email)"), repository.calls)
    }
  }

  @Test
  fun sendingStartsTheCountdown() {
    showScreen()
    composeTestRule
        .onNodeWithTag(C.Tag.auth_reset_send_button)
        .assertIsEnabled()
        .assertTextEquals(context.getString(R.string.auth_reset_send))

    tapSend()

    composeTestRule.onNodeWithTag(C.Tag.auth_reset_send_button).assertIsNotEnabled()
  }

  @Test
  fun countdownDisablesSendAndShowsSecondsLeft() {
    showContent(AuthUiState(email = email, resetCooldownSeconds = 42, resetCooldownEmail = email))

    composeTestRule
        .onNodeWithTag(C.Tag.auth_reset_send_button)
        .assertIsNotEnabled()
        .assertTextEquals(context.getString(R.string.auth_reset_send_countdown, 42))
  }

  @Test
  fun nonEpflEmailShowsTheInlineErrorAndSendsNothing() {
    showScreen(typedEmail = "jane@gmail.com")
    composeTestRule
        .onNodeWithTag(C.Tag.auth_email_error)
        .assertTextEquals(context.getString(R.string.auth_email_invalid_domain))

    tapSend()

    composeTestRule.onNodeWithTag(C.Tag.auth_reset_sent).assertDoesNotExist()
    composeTestRule.runOnIdle { assertTrue(repository.calls.isEmpty()) }
  }

  @Test
  fun emptyEmailShowsTheRequiredErrorOnceSendIsPressed() {
    showScreen(typedEmail = "")
    composeTestRule.onNodeWithTag(C.Tag.auth_email_error).assertDoesNotExist()

    tapSend()

    composeTestRule
        .onNodeWithTag(C.Tag.auth_email_error)
        .assertTextEquals(context.getString(R.string.auth_email_required))
    composeTestRule.runOnIdle { assertTrue(repository.calls.isEmpty()) }
  }

  @Test
  fun networkErrorShowsItsMessage() {
    repository.resetResult = AuthResult.Failure(AuthError.NETWORK)
    showScreen()

    tapSend()

    composeTestRule
        .onNodeWithTag(C.Tag.auth_error)
        .assertTextEquals(context.getString(R.string.auth_error_network))
    composeTestRule.onNodeWithTag(C.Tag.auth_reset_sent).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.auth_reset_send_button).assertIsEnabled()
  }

  @Test
  fun editingTheEmailHidesTheConfirmation() {
    showScreen()
    tapSend()

    composeTestRule.onNodeWithTag(C.Tag.auth_email_field).performTextClearance()
    composeTestRule.onNodeWithTag(C.Tag.auth_email_field).performTextInput("john@epfl.ch")

    composeTestRule.onNodeWithTag(C.Tag.auth_reset_sent).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.auth_reset_send_button).assertIsEnabled()
  }

  @Test
  fun backArrowReturnsToTheFormKeepingTheEmail() {
    showScreen()

    composeTestRule.onNodeWithTag(C.Tag.auth_back_to_form_button).performClick()

    composeTestRule.runOnIdle {
      assertFalse(viewModel.uiState.value.isResettingPassword)
      assertEquals(email, viewModel.uiState.value.email)
    }
  }

  @Test
  fun systemBackReturnsToTheForm() {
    showScreen()

    // Through the dispatcher, as Espresso.pressBack() waits for window focus and timed out on CI.
    composeTestRule.runOnUiThread { backDispatcher.onBackPressed() }

    composeTestRule.runOnIdle { assertFalse(viewModel.uiState.value.isResettingPassword) }
  }

  @Test
  fun systemBackWhileSendingStaysOnTheScreen() {
    showScreen()
    val gate = CompletableDeferred<Unit>()
    repository.gate = gate
    try {
      tapSend()

      composeTestRule.runOnUiThread { backDispatcher.onBackPressed() }

      composeTestRule.runOnIdle { assertTrue(viewModel.uiState.value.isResettingPassword) }
    } finally {
      gate.complete(Unit)
    }
  }

  @Test
  fun loadingDisablesSendAndBack() {
    showContent(AuthUiState(email = email, isLoading = true))

    composeTestRule.onNodeWithTag(C.Tag.auth_reset_send_button).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.auth_back_to_form_button).assertIsNotEnabled()
  }
}
