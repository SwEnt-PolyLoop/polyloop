// Made with Claude.

package com.swent.polyloop

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.swent.polyloop.model.RepositoryProvider
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.navigation.Screen
import com.swent.polyloop.utils.FirebaseEmulator
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Goes through the whole app as merged on main, with the real repositories pointed at the Firebase
 * emulators (Auth, Firestore, Storage). Only the network status is faked, so the test never depends
 * on the emulator's connection. Needs `firebase emulators:start`.
 *
 * Steps still done by the test instead of the UI, to move to the UI once the features exist:
 * publishing a listing (no camera yet) and sending a rental request (Request does not send it yet).
 */
@RunWith(AndroidJUnit4::class)
class EndToEndTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var navController: NavHostController

  private lateinit var backDispatcher: OnBackPressedDispatcher

  // A new account for each run, so accounts left by earlier runs never get in the way.
  private val email = "e2e-${UUID.randomUUID().toString().take(8)}@epfl.ch"

  @Before
  fun setUp() {
    FirebaseEmulator.connect()
    // A session kept by an earlier test would skip the Sign-up / login screen.
    Firebase.auth.signOut()
    RepositoryProvider.reset()
    RepositoryProvider.connectivityRepository = FakeConnectivityRepository(online = true)
  }

  @After
  fun tearDown() {
    Firebase.auth.signOut()
    RepositoryProvider.reset()
  }

  @Test
  fun signUpSignOutAndLogInAgain() {
    setApp()
    assertRoute(Screen.Auth)
    composeTestRule.onNodeWithTag(C.Tag.top_bar).assertDoesNotExist()

    // Sign up: a non-EPFL email is refused on the form.
    composeTestRule.onNodeWithTag(C.Tag.auth_signup_tab).performClick()
    composeTestRule.onNodeWithTag(C.Tag.auth_name_field).performScrollTo().performTextInput(NAME)
    composeTestRule
        .onNodeWithTag(C.Tag.auth_email_field)
        .performScrollTo()
        .performTextInput("student@gmail.com")
    composeTestRule
        .onNodeWithTag(C.Tag.auth_password_field)
        .performScrollTo()
        .performTextInput(PASSWORD)
    composeTestRule.onNodeWithTag(C.Tag.auth_signup_button).performScrollTo().performClick()
    composeTestRule.onNodeWithTag(C.Tag.auth_email_error).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.auth_check_inbox_screen).assertDoesNotExist()

    composeTestRule.onNodeWithTag(C.Tag.auth_email_field).performTextReplacement(email)
    composeTestRule.onNodeWithTag(C.Tag.auth_email_error).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.auth_signup_button).performScrollTo().performClick()

    // Check your inbox: Resend waits, and "I've verified" before verifying stays here.
    waitForTag(C.Tag.auth_check_inbox_screen)
    composeTestRule.onNodeWithTag(C.Tag.auth_resend_button).assertIsNotEnabled()
    pressWhenEnabled(C.Tag.auth_verified_button)
    waitUntilEnabled(C.Tag.auth_verified_button)
    composeTestRule.onNodeWithTag(C.Tag.auth_check_inbox_screen).assertIsDisplayed()

    runBlocking { FirebaseEmulator.verifyEmail(email) }
    pressWhenEnabled(C.Tag.auth_verified_button)

    waitForTag(C.Tag.browse_screen)
    assertRoute(Screen.Browse)
    assertNull(navController.previousBackStackEntry)

    // The first verified log-in created the profile, with the name typed at sign-up.
    composeTestRule.onNodeWithTag(C.Tag.top_bar_profile_button).performClick()
    waitForText(C.Tag.profile_name, NAME)
    composeTestRule
        .onNodeWithTag(C.Tag.profile_listings_empty)
        .performScrollTo()
        .assertIsDisplayed()

    // Sign out: back on the form, with nothing to go back to.
    composeTestRule.onNodeWithTag(C.Tag.profile_sign_out_button).performScrollTo().performClick()
    waitForTag(C.Tag.auth_login_button)
    assertRoute(Screen.Auth)
    assertNull(navController.previousBackStackEntry)
    composeTestRule.onNodeWithTag(C.Tag.top_bar).assertDoesNotExist()
    assertNull(RepositoryProvider.authRepository.getCurrentUser())

    // Log in: a wrong password is refused, the right one opens Browse.
    composeTestRule.onNodeWithTag(C.Tag.auth_login_tab).performClick()
    composeTestRule.onNodeWithTag(C.Tag.auth_email_field).performScrollTo().performTextInput(email)
    composeTestRule
        .onNodeWithTag(C.Tag.auth_password_field)
        .performScrollTo()
        .performTextInput("wrong-password")
    composeTestRule.onNodeWithTag(C.Tag.auth_login_button).performScrollTo().performClick()
    waitForTag(C.Tag.auth_error)

    composeTestRule.onNodeWithTag(C.Tag.auth_password_field).performTextReplacement(PASSWORD)
    pressWhenEnabled(C.Tag.auth_login_button)

    waitForTag(C.Tag.browse_screen)
    assertRoute(Screen.Browse)
    assertEquals(email, RepositoryProvider.authRepository.getCurrentUser()?.email)
  }

  private fun setApp() {
    composeTestRule.setContent {
      backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
      navController = rememberNavController()
      PolyLoopApp(navController = navController)
    }
  }

  private fun assertRoute(screen: Screen) {
    composeTestRule.waitForIdle()
    assertEquals(screen.route, navController.currentDestination?.route)
  }

  /** Waits for [tag] to be shown; Firebase calls take longer than in-memory fakes. */
  private fun waitForTag(tag: String) = waitFor(hasTestTag(tag))

  private fun waitForText(tag: String, text: String) {
    waitFor(hasTestTag(tag) and hasText(text))
  }

  private fun waitUntilEnabled(tag: String) = waitFor(hasTestTag(tag) and isEnabled())

  /** Waits for the button [tag] to accept a press, e.g. after a request ends, then presses it. */
  private fun pressWhenEnabled(tag: String) {
    waitUntilEnabled(tag)
    composeTestRule.onNodeWithTag(tag).performScrollTo().performClick()
    composeTestRule.waitForIdle()
  }

  private fun waitFor(matcher: SemanticsMatcher) {
    composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
      composeTestRule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private companion object {
    const val NAME = "Eve Student"
    const val PASSWORD = "Password123!"
    const val TIMEOUT_MS = 15_000L
  }
}
