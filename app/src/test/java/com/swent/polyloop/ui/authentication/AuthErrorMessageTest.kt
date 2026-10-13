// Made with Claude.

package com.swent.polyloop.ui.authentication

import com.swent.polyloop.R
import com.swent.polyloop.model.auth.AuthError
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthErrorMessageTest {

  @Test
  fun everyAuthErrorMapsToItsMessage() {
    val expected =
        mapOf(
            AuthError.WRONG_CREDENTIALS to R.string.auth_error_wrong_credentials,
            AuthError.WEAK_PASSWORD to R.string.auth_error_weak_password,
            AuthError.EMAIL_NOT_VERIFIED to R.string.auth_error_email_not_verified,
            AuthError.TOO_MANY_REQUESTS to R.string.auth_error_too_many_requests,
            AuthError.NETWORK to R.string.auth_error_network,
            AuthError.UNKNOWN to R.string.auth_error_unknown,
            AuthError.NAME_REQUIRED to R.string.auth_name_required,
            AuthError.INVALID_DOMAIN to R.string.auth_email_invalid_domain,
            AuthError.EMAIL_ALREADY_IN_USE to R.string.auth_error_email_already_in_use,
            AuthError.VERIFICATION_EMAIL_NOT_SENT to
                R.string.auth_error_verification_email_not_sent,
            AuthError.NAME_NOT_SAVED to R.string.auth_error_name_not_saved,
        )

    assertEquals(expected, AuthError.entries.associateWith { it.messageRes() })
  }
}
