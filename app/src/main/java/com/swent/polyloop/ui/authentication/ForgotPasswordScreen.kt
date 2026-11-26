// Made with Claude.

package com.swent.polyloop.ui.authentication

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.polyloop.R
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.theme.PolyLoopTheme

/**
 * The "Forgot password" step of Sign-up / login, shown while [AuthUiState.isResettingPassword].
 *
 * It must share the [AuthViewModel] of [AuthScreen], whose email it uses: the address typed on the
 * log-in form is filled in, and a corrected one stays when going back. Firebase sends the email,
 * and the user sets the new password on the page its link opens, then logs in again. The host shows
 * the form again once the user goes back to it (back arrow or system Back).
 */
@Composable
fun ForgotPasswordScreen(viewModel: AuthViewModel) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  // The ViewModel decides: it ignores Back while a request runs, even one started this frame.
  val backToForm: () -> Unit = { viewModel.backToForm() }
  // Always enabled: a disabled handler would let system Back leave the screen during a request.
  BackHandler(onBack = backToForm)

  ForgotPasswordContent(
      state = state,
      onEmailChange = viewModel::onEmailChange,
      onSend = viewModel::sendPasswordReset,
      onBack = backToForm,
  )
}

/** Stateless content of [ForgotPasswordScreen]. */
@Composable
internal fun ForgotPasswordContent(
    state: AuthUiState,
    onEmailChange: (String) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme

  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(colors.background)
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 14.dp, vertical = 16.dp)
              .testTag(C.Tag.auth_reset_screen)
  ) {
    IconButton(
        onClick = onBack,
        enabled = !state.isLoading,
        modifier = Modifier.testTag(C.Tag.auth_back_to_form_button),
    ) {
      Icon(
          Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = stringResource(R.string.auth_back_to_form),
      )
    }

    Spacer(Modifier.height(16.dp))

    Text(
        text = stringResource(R.string.auth_reset_title),
        color = colors.onBackground,
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
    )

    Text(
        text = stringResource(R.string.auth_reset_body),
        color = colors.onSurfaceVariant,
        fontSize = 16.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
    )

    AuthTextField(
        label = stringResource(R.string.auth_email_label),
        value = state.email,
        placeholder = stringResource(R.string.auth_email_placeholder),
        onValueChange = onEmailChange,
        errorText = state.emailErrorRes()?.let { stringResource(it) },
        keyboardType = KeyboardType.Email,
        testTag = C.Tag.auth_email_field,
        errorTestTag = C.Tag.auth_email_error,
    )

    Spacer(Modifier.height(20.dp))

    AuthButton(
        text =
            if (state.resetSecondsLeft > 0) {
              stringResource(R.string.auth_reset_send_countdown, state.resetSecondsLeft)
            } else {
              stringResource(R.string.auth_reset_send)
            },
        onClick = onSend,
        enabled = state.canSendReset,
        testTag = C.Tag.auth_reset_send_button,
    )

    if (state.isResetEmailSent) {
      // The same text whether or not the address has an account: the app cannot tell either.
      Text(
          text = stringResource(R.string.auth_reset_sent),
          color = colors.onSurfaceVariant,
          fontSize = 13.sp,
          modifier =
              Modifier.heightIn(min = 36.dp)
                  .fillMaxWidth()
                  .padding(top = 4.dp)
                  .testTag(C.Tag.auth_reset_sent),
      )
    } else {
      AuthMessage(state.error)
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun ForgotPasswordPreview() {
  PolyLoopTheme {
    ForgotPasswordContent(
        state =
            AuthUiState(
                email = "jane.doe@epfl.ch",
                isResettingPassword = true,
                isResetEmailSent = true,
                resetCooldownSeconds = 42,
                resetCooldownEmail = "jane.doe@epfl.ch",
            ),
        onEmailChange = {},
        onSend = {},
        onBack = {},
    )
  }
}
