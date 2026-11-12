// Made with Claude.

package com.swent.polyloop.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.swent.polyloop.MainActivity
import com.swent.polyloop.e2e.EndToEndTestBase.Companion.CLEANUP_TIMEOUT_MS
import com.swent.polyloop.e2e.EndToEndTestBase.Companion.PASSWORD
import com.swent.polyloop.e2e.EndToEndTestBase.Companion.USER_NAME
import com.swent.polyloop.e2e.EndToEndTestBase.Companion.deleteAccount
import com.swent.polyloop.e2e.EndToEndTestBase.Companion.profileFields
import com.swent.polyloop.e2e.EndToEndTestBase.Companion.retryWhenPaused
import com.swent.polyloop.e2e.EndToEndTestBase.Companion.waitForNode
import com.swent.polyloop.model.RepositoryProvider
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.profile.ProfileFields
import com.swent.polyloop.resources.C
import com.swent.polyloop.utils.FirebaseEmulator
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/**
 * Launches the real [MainActivity], as a user opening the app does, and logs in an existing account
 * against the Auth emulator. The other end-to-end tests set the app's content themselves, so this
 * is the one that covers the activity. It cannot extend [EndToEndTestBase]: the activity must start
 * after the account exists, so its setup is a rule, not a @Before.
 */
@RunWith(AndroidJUnit4::class)
class AppStartEndToEndTest {

  private val email = "e2e-${UUID.randomUUID().toString().take(8)}@epfl.ch"

  /**
   * An existing verified account with its profile, signed out. Applied before the activity starts,
   * which reads the signed-in user on launch: @Before would run too late.
   */
  @get:Rule(order = 0)
  val signedOutAccount =
      object : ExternalResource() {
        override fun before() {
          FirebaseEmulator.connect()
          RepositoryProvider.reset()
          RepositoryProvider.connectivityRepository = FakeConnectivityRepository(online = true)
          val uid = runBlocking { FirebaseEmulator.signUpVerified(email, PASSWORD) }
          FirebaseEmulator.createDocumentAsAdmin(
              "${ProfileFields.COLLECTION}/$uid",
              profileFields(USER_NAME, email),
          )
          Firebase.auth.signOut()
        }

        override fun after() {
          // Bounded, as in EndToEndTestBase: a lost emulator would otherwise hang here.
          runBlocking { withTimeoutOrNull(CLEANUP_TIMEOUT_MS) { deleteAccount(email) } }
          Firebase.auth.signOut()
          RepositoryProvider.reset()
        }
      }

  @get:Rule(order = 1) val composeTestRule = createAndroidComposeRule<MainActivity>()

  /**
   * Each step goes through the base's pause helpers (#196): the activity is most likely to be
   * paused right after it starts, which is when this test runs.
   */
  @Test
  fun theAppStartsOnTheLogInFormAndLogsIn() {
    composeTestRule.waitForNode(hasTestTag(C.Tag.auth_login_button))
    step { composeTestRule.onNodeWithTag(C.Tag.top_bar).assertDoesNotExist() }

    step { composeTestRule.onNodeWithTag(C.Tag.auth_email_field).performScrollTo() }
    step { composeTestRule.onNodeWithTag(C.Tag.auth_email_field).performTextInput(email) }
    step { composeTestRule.onNodeWithTag(C.Tag.auth_password_field).performScrollTo() }
    step { composeTestRule.onNodeWithTag(C.Tag.auth_password_field).performTextInput(PASSWORD) }
    step { composeTestRule.onNodeWithTag(C.Tag.auth_login_button).performScrollTo() }
    step { composeTestRule.onNodeWithTag(C.Tag.auth_login_button).performClick() }

    composeTestRule.waitForNode(hasTestTag(C.Tag.browse_screen))
  }

  /** Runs [action] once a screen is shown, again if the activity was paused meanwhile. */
  private fun step(action: () -> Unit) {
    composeTestRule.waitForNode(isRoot())
    composeTestRule.retryWhenPaused(action)
  }
}
