// Made with Claude.

package com.swent.polyloop.e2e

import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.R
import com.swent.polyloop.model.RepositoryProvider
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.navigation.Screen
import com.swent.polyloop.utils.FirebaseEmulator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Signing up, verifying the email, signing out and logging in again, against Firebase Auth. */
@RunWith(AndroidJUnit4::class)
class AuthEndToEndTest : EndToEndTestBase() {

  @Test
  fun signUpSignOutAndLogInAgain() {
    setApp()
    assertRoute(Screen.Auth)
    composeTestRule.onNodeWithTag(C.Tag.top_bar).assertAbsent()

    // Sign up: a non-EPFL email is refused on the form.
    composeTestRule.onNodeWithTag(C.Tag.auth_signup_tab).performClick()
    fillField(C.Tag.auth_name_field, USER_NAME)
    fillField(C.Tag.auth_email_field, "student@gmail.com")
    fillField(C.Tag.auth_password_field, PASSWORD)
    composeTestRule.onNodeWithTag(C.Tag.auth_signup_button).performScrollTo().performClick()
    // Scrolled to: on a phone, the open keyboard pushes the form up past the email field.
    composeTestRule.onNodeWithTag(C.Tag.auth_email_error).performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.auth_check_inbox_screen).assertAbsent()

    composeTestRule.onNodeWithTag(C.Tag.auth_email_field).performTextReplacement(email)
    composeTestRule.onNodeWithTag(C.Tag.auth_email_error).assertAbsent()
    composeTestRule.onNodeWithTag(C.Tag.auth_signup_button).performScrollTo().performClick()

    // Check your inbox: Resend waits, and "I've verified" before verifying stays here.
    waitForTag(C.Tag.auth_check_inbox_screen)
    composeTestRule.onNodeWithTag(C.Tag.auth_resend_button).assertIsNotEnabled()
    pressWhenEnabled(C.Tag.auth_verified_button)
    // Waits for the log-in attempt to end: it asks to verify the email, and the screen stays.
    waitForText(C.Tag.auth_error, context.getString(R.string.auth_error_email_not_verified))
    composeTestRule.onNodeWithTag(C.Tag.auth_check_inbox_screen).assertIsDisplayed()

    runBlocking { FirebaseEmulator.verifyEmail(email) }
    pressWhenEnabled(C.Tag.auth_verified_button)

    waitForTag(C.Tag.browse_screen)
    assertRoute(Screen.Browse)
    assertNull(navController.previousBackStackEntry)

    // The first verified log-in created the profile, with the name typed at sign-up.
    composeTestRule.onNodeWithTag(C.Tag.top_bar_profile_button).performClick()
    waitForText(C.Tag.profile_name, USER_NAME)
    // The listings load separately from the profile, so they can still be loading here.
    waitForTag(C.Tag.profile_listings_empty)
    composeTestRule
        .onNodeWithTag(C.Tag.profile_listings_empty)
        .performScrollTo()
        .assertIsDisplayed()

    // Sign out: back on the form, with nothing to go back to.
    composeTestRule.onNodeWithTag(C.Tag.profile_sign_out_button).performScrollTo().performClick()
    waitForTag(C.Tag.auth_login_button)
    assertRoute(Screen.Auth)
    assertNull(navController.previousBackStackEntry)
    composeTestRule.onNodeWithTag(C.Tag.top_bar).assertAbsent()
    assertNull(RepositoryProvider.authRepository.getCurrentUser())

    // Log in: a wrong password is refused, the right one opens Browse.
    composeTestRule.onNodeWithTag(C.Tag.auth_login_tab).performClick()
    fillField(C.Tag.auth_email_field, email)
    composeTestRule
        .onNodeWithTag(C.Tag.auth_password_field)
        .performScrollTo()
        .performTextInput("wrong-password")
    composeTestRule.onNodeWithTag(C.Tag.auth_login_button).performScrollTo().performClick()
    waitForText(C.Tag.auth_error, context.getString(R.string.auth_error_wrong_credentials))

    composeTestRule.onNodeWithTag(C.Tag.auth_password_field).performTextReplacement(PASSWORD)
    pressWhenEnabled(C.Tag.auth_login_button)

    waitForTag(C.Tag.browse_screen)
    assertRoute(Screen.Browse)
    assertEquals(email, RepositoryProvider.authRepository.getCurrentUser()?.email)
  }
}
