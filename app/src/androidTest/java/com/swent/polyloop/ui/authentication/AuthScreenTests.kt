// Made with ChatGPT

package com.swent.polyloop.ui.authentication

import AuthScreen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.auth.AuthResult
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.ui.theme.PolyLoopTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Core UI tests for the authentication screen. */
@RunWith(AndroidJUnit4::class)
class AuthScreenTests {
  @get:Rule val composeTestRule = createComposeRule()
  private val repository = FakeAuthRepository()
  private val signedIn = mutableStateOf(0)
  private val verificationRequested = mutableStateOf(0)

  @Test
  fun modesSwitchAndShowModeSpecificFields() {
    show(AuthMode.LOG_IN)
    composeTestRule.onNodeWithText("EPFL email").assertIsDisplayed()
    composeTestRule.onNodeWithTag("signInSignupTab").performClick()
    composeTestRule.onNodeWithText("Name").assertIsDisplayed()
    composeTestRule.onNodeWithTag("signInName").assertIsDisplayed()
    composeTestRule.onNodeWithTag("signInLoginTab").performClick()
    composeTestRule.onNodeWithTag("signInLoginButton").assertIsDisplayed()
  }

  @Test
  fun loginValidatesFieldsThenCallsRepositoryAndCallback() {
    show(AuthMode.LOG_IN)
    composeTestRule.onNodeWithTag("signInLoginButton").performClick()
    composeTestRule.onNodeWithTag("signInEmailError").assertIsDisplayed()
    composeTestRule.onNodeWithTag("signInPasswordError").assertIsDisplayed()
    assertEquals(null, repository.signIn)

    composeTestRule.onNodeWithTag("signInEmail").performTextInput("prenom.nom@epfl.ch")
    composeTestRule.onNodeWithTag("signInPassword").performTextInput("pass123")
    composeTestRule.onNodeWithTag("signInLoginButton").performClick()

    composeTestRule.waitUntil(5_000) { signedIn.value == 1 }
    composeTestRule.runOnIdle { assertEquals("prenom.nom@epfl.ch" to "pass123", repository.signIn) }
  }

  @Test
  fun signUpDisplaysFieldsAndAcceptsInput() {
    show(AuthMode.SIGN_UP)

    composeTestRule.onNodeWithTag("signInName").assertIsDisplayed()
    composeTestRule.onNodeWithTag("signInEmail").assertIsDisplayed()
    composeTestRule.onNodeWithTag("signInPassword").assertIsDisplayed()

    composeTestRule.onNodeWithTag("signInName").performTextInput("First Last")
    composeTestRule.onNodeWithTag("signInEmail").performTextInput("prenom.nom@epfl.ch")
    composeTestRule.onNodeWithTag("signInPassword").performTextInput("pass123")

    composeTestRule.onNodeWithTag("signInSignupButton").performScrollTo().assertIsDisplayed()
  }

  private fun show(mode: AuthMode) {
    val viewModel = AuthViewModel(repository).also { it.switchMode(mode) }
    composeTestRule.setContent {
      PolyLoopTheme {
        AuthScreen(
            onSignIn = { signedIn.value++ },
            onNavigateToVerification = { verificationRequested.value++ },
            viewModel = viewModel,
        )
      }
    }
  }

  private class FakeAuthRepository : AuthRepository {
    var signIn: Pair<String, String>? = null
    var signUp: Triple<String, String, String>? = null

    override suspend fun signIn(email: String, password: String): AuthResult<AuthUser> =
        AuthResult.Success(AuthUser("u1", email, "First Last")).also { signIn = email to password }

    override suspend fun signUp(
        name: String,
        email: String,
        password: String,
    ): AuthResult<Unit> = AuthResult.Success(Unit).also { signUp = Triple(name, email, password) }

    override suspend fun resendVerificationEmail(email: String, password: String) =
        AuthResult.Success(Unit)

    override fun getCurrentUser(): AuthUser? = null

    override fun signOut() = Unit
  }
}
