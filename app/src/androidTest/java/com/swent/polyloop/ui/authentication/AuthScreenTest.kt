// Made with ChatGPT and Claude.

package com.swent.polyloop.ui.authentication

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.model.auth.AuthError
import com.swent.polyloop.model.auth.AuthResult
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.profile.FakeProfileRepository
import com.swent.polyloop.model.profile.UserProfile
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.theme.PolyLoopTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI tests for AuthScreen. No Firebase, network, sleeps or coordinate-based gestures. */
@RunWith(AndroidJUnit4::class)
class AuthScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private lateinit var repository: FakeAuthRepository
  private lateinit var profileRepository: FakeProfileRepository
  private lateinit var viewModel: AuthViewModel
  private var signedInNavigations = 0
  private var verificationNavigations = 0

  // Owns the ViewModel so tearDown can clear it and stop the resend countdown.
  private val viewModelStore = ViewModelStore()

  @Before
  fun setUp() {
    repository = FakeAuthRepository()
    profileRepository = FakeProfileRepository()
    viewModel =
        ViewModelProvider(viewModelStore, AuthViewModel.Factory(repository, profileRepository))[
            AuthViewModel::class.java]
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

  @After
  fun tearDown() {
    viewModelStore.clear()
  }

  @Test
  fun loginIsShownInitially() {
    composeTestRule.onNodeWithText(context.getString(R.string.auth_tagline)).assertExists()
    composeTestRule.onNodeWithTag(C.Tag.auth_login_tab).assertExists()
    composeTestRule.onNodeWithTag(C.Tag.auth_signup_tab).assertExists()
    composeTestRule.onNodeWithTag(C.Tag.auth_email_field).assertExists()
    composeTestRule
        .onNodeWithTag(C.Tag.auth_password_field)
        .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
    composeTestRule.onNodeWithTag(C.Tag.auth_name_field).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.auth_login_button).assertIsEnabled()
    composeTestRule.onNodeWithTag(C.Tag.auth_signup_button).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.auth_error).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.auth_email_error).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.auth_password_error).assertDoesNotExist()
  }

  @Test
  fun signUpTabShowsNameAndSignUpButton() {
    tap(C.Tag.auth_signup_tab)
    composeTestRule.onNodeWithTag(C.Tag.auth_name_field).assertExists()
    composeTestRule.onNodeWithTag(C.Tag.auth_signup_button).assertIsEnabled()
    composeTestRule.onNodeWithTag(C.Tag.auth_login_button).assertDoesNotExist()
  }

  @Test
  fun switchingBackToLoginHidesName() {
    tap(C.Tag.auth_signup_tab)
    tap(C.Tag.auth_login_tab)
    composeTestRule.onNodeWithTag(C.Tag.auth_name_field).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.auth_login_button).assertExists()
  }

  @Test
  fun tabsPreserveEmailAndPassword() {
    type(C.Tag.auth_email_field, VALID_EMAIL)
    type(C.Tag.auth_password_field, VALID_PASSWORD)
    tap(C.Tag.auth_signup_tab)
    tap(C.Tag.auth_login_tab)
    tap(C.Tag.auth_login_button)
    composeTestRule.runOnIdle {
      assertEquals(listOf("signIn($VALID_EMAIL, $VALID_PASSWORD)"), repository.calls)
    }
  }

  @Test
  fun tappingEmptyEmailFieldShowsNoError() {
    tap(C.Tag.auth_email_field)
    composeTestRule.onNodeWithTag(C.Tag.auth_email_error).assertDoesNotExist()
  }

  @Test
  fun invalidEmailErrorStaysAfterMovingToPassword() {
    type(C.Tag.auth_email_field, "bob@gmail.com")
    tap(C.Tag.auth_password_field)
    assertErrorText(C.Tag.auth_email_error, R.string.auth_email_invalid_domain)
  }

  @Test
  fun invalidEmailShowsDomainMessageAndCorrectionRemovesIt() {
    tap(C.Tag.auth_email_field)
    type(C.Tag.auth_email_field, "test@gmail.com")
    assertErrorText(C.Tag.auth_email_error, R.string.auth_email_invalid_domain)
    clear(C.Tag.auth_email_field)
    type(C.Tag.auth_email_field, VALID_EMAIL)
    composeTestRule.onNodeWithTag(C.Tag.auth_email_error).assertDoesNotExist()
  }

  @Test
  fun emptyLoginShowsOnlyEmailAndPasswordValidation() {
    tap(C.Tag.auth_login_button)
    assertErrorText(C.Tag.auth_email_error, R.string.auth_email_required)
    assertErrorText(C.Tag.auth_password_error, R.string.auth_password_required)
    composeTestRule.onNodeWithTag(C.Tag.auth_name_error).assertDoesNotExist()
  }

  @Test
  fun emptySignUpShowsThreeValidationMessages() {
    tap(C.Tag.auth_signup_tab)
    tap(C.Tag.auth_signup_button)
    assertErrorText(C.Tag.auth_name_error, R.string.auth_name_required)
    assertErrorText(C.Tag.auth_email_error, R.string.auth_email_required)
    assertErrorText(C.Tag.auth_password_error, R.string.auth_password_required)
  }

  @Test
  fun successfulLoginNavigatesOnce() {
    fillLogin()
    tap(C.Tag.auth_login_button)
    composeTestRule.runOnIdle {
      assertEquals(1, signedInNavigations)
      assertEquals(0, verificationNavigations)
    }
    // A harmless state update triggers recomposition, but not a second navigation.
    type(C.Tag.auth_password_field, "x")
    composeTestRule.runOnIdle { assertEquals(1, signedInNavigations) }
  }

  @Test
  fun successfulSignUpNavigatesToVerificationOnce() {
    fillSignUp()
    tap(C.Tag.auth_signup_button)
    composeTestRule.runOnIdle {
      assertEquals(0, signedInNavigations)
      assertEquals(1, verificationNavigations)
    }
    type(C.Tag.auth_name_field, "x")
    composeTestRule.runOnIdle { assertEquals(1, verificationNavigations) }
  }

  @Test
  fun wrongCredentialsDisplayErrorWithoutNavigation() {
    repository.signInResult = AuthResult.Failure(AuthError.WRONG_CREDENTIALS)
    fillLogin()
    tap(C.Tag.auth_login_button)
    assertErrorText(C.Tag.auth_error, R.string.auth_error_wrong_credentials)
    composeTestRule.runOnIdle {
      assertEquals(0, signedInNavigations)
      assertEquals(0, verificationNavigations)
    }
  }

  @Test
  fun loginButtonDisabledUntilPendingRequestCompletes() {
    val gate = CompletableDeferred<Unit>()
    repository.gate = gate
    try {
      fillLogin()
      tap(C.Tag.auth_login_button)
      composeTestRule.onNodeWithTag(C.Tag.auth_login_button).assertIsNotEnabled()
      composeTestRule.runOnIdle { assertEquals(0, signedInNavigations) }
      gate.complete(Unit)
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag(C.Tag.auth_login_button).assertIsEnabled()
      composeTestRule.runOnIdle { assertEquals(1, signedInNavigations) }
    } finally {
      gate.complete(Unit)
    }
  }

  @Test
  fun loginWithoutNameShowsTheNameStepInsteadOfTheForm() {
    loginWithoutName()

    composeTestRule.onNodeWithText(context.getString(R.string.auth_name_step_title)).assertExists()
    composeTestRule.onNodeWithTag(C.Tag.auth_name_field).assertExists()
    composeTestRule.onNodeWithTag(C.Tag.auth_name_continue_button).assertIsEnabled()
    composeTestRule.onNodeWithTag(C.Tag.auth_login_tab).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.auth_email_field).assertDoesNotExist()
    composeTestRule.runOnIdle { assertEquals(0, signedInNavigations) }
  }

  @Test
  fun continueWithBlankNameShowsTheNameError() {
    loginWithoutName()

    tap(C.Tag.auth_name_continue_button)

    assertErrorText(C.Tag.auth_name_error, R.string.auth_name_required)
    composeTestRule.runOnIdle { assertEquals(0, signedInNavigations) }
  }

  @Test
  fun continueWithNameCreatesTheProfileAndNavigatesOnce() {
    loginWithoutName()

    type(C.Tag.auth_name_field, VALID_NAME)
    tap(C.Tag.auth_name_continue_button)

    composeTestRule.runOnIdle {
      assertEquals(
          listOf(UserProfile("uid1", VALID_NAME, VALID_EMAIL)),
          profileRepository.profiles,
      )
      assertEquals(1, signedInNavigations)
    }
  }

  @Test
  fun profileFailureShowsTheErrorOnTheLoginForm() {
    profileRepository.failure = IllegalStateException("offline")
    fillLogin()

    tap(C.Tag.auth_login_button)

    assertErrorText(C.Tag.auth_error, R.string.auth_error_profile_not_created)
    composeTestRule.onNodeWithTag(C.Tag.auth_login_button).assertExists()
    composeTestRule.runOnIdle { assertEquals(0, signedInNavigations) }
  }

  private fun loginWithoutName() {
    repository.signInResult =
        AuthResult.Success(AuthUser(uid = "uid1", email = VALID_EMAIL, name = ""))
    fillLogin()
    tap(C.Tag.auth_login_button)
  }

  private fun assertErrorText(tag: String, messageRes: Int) {
    composeTestRule.onNodeWithTag(tag).assertTextEquals(context.getString(messageRes))
  }

  private fun fillLogin() {
    type(C.Tag.auth_email_field, VALID_EMAIL)
    type(C.Tag.auth_password_field, VALID_PASSWORD)
  }

  private fun fillSignUp() {
    tap(C.Tag.auth_signup_tab)
    type(C.Tag.auth_name_field, VALID_NAME)
    type(C.Tag.auth_email_field, VALID_EMAIL)
    type(C.Tag.auth_password_field, VALID_PASSWORD)
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
    const val VALID_NAME = "John"
    const val VALID_EMAIL = "john@epfl.ch"
    const val VALID_PASSWORD = "secret123"
  }
}
