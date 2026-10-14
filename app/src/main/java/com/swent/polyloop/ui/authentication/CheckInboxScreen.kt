// Made with Claude.

package com.swent.polyloop.ui.authentication

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.polyloop.R
import com.swent.polyloop.model.auth.EpflEmail
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.theme.PolyLoopTheme

/**
 * The "Check your inbox" step of Sign-up / login, shown while [AuthUiState.isAwaitingVerification].
 *
 * It must share the [AuthViewModel] of [AuthScreen], which holds the email and password needed to
 * resend the email and to log in again.
 *
 * @param onSignIn called once "I've verified" signed the user in.
 * @param onBackToForm called after the user chose to go back to the form (back arrow or system
 *   Back), e.g. to fix a mistyped email. Back does nothing while a request runs.
 */
@Composable
fun CheckInboxScreen(
    viewModel: AuthViewModel,
    onSignIn: () -> Unit,
    onBackToForm: () -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(state.isSignedIn) { if (state.isSignedIn) onSignIn() }

  // The ViewModel decides: it ignores Back while a request runs, even one started this frame.
  val backToForm = { if (viewModel.backToForm()) onBackToForm() }
  // Always enabled: a disabled handler would let system Back leave the screen during a request.
  BackHandler(onBack = backToForm)

  CheckInboxContent(
      state = state,
      onVerified = viewModel::confirmVerified,
      onResend = viewModel::resendVerificationEmail,
      onBack = backToForm,
  )
}

/** Stateless content of [CheckInboxScreen]. */
@Composable
internal fun CheckInboxContent(
    state: AuthUiState,
    onVerified: () -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  // The address the email went to, not the one as typed (spaces, capitals).
  val email = EpflEmail.normalize(state.email)

  // Scrolls when the screen is too short (landscape, large font). The minimum height keeps the
  // middle part centered otherwise: in a scrolling column, weight shares out the minimum height.
  BoxWithConstraints(
      modifier =
          Modifier.fillMaxSize()
              .background(colors.background)
              .testTag(C.Tag.auth_check_inbox_screen)
  ) {
    Column(
        modifier =
            Modifier.verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 16.dp, vertical = 16.dp)
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

      Column(
          modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
            modifier = Modifier.size(84.dp).background(colors.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
          // Decorative: the title says what to do.
          Icon(
              Icons.Outlined.Email,
              contentDescription = null,
              tint = colors.onPrimaryContainer,
              modifier = Modifier.size(32.dp),
          )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.auth_check_inbox_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = boldEmail(stringResource(R.string.auth_check_inbox_body, email), email),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(C.Tag.auth_check_inbox_body),
        )
        AuthMessage(
            error = state.error,
            isEmailResent = state.isVerificationEmailResent,
            centered = true,
            modifier = Modifier.padding(top = 8.dp),
        )
      }

      AuthButton(
          text = stringResource(R.string.auth_verified),
          onClick = onVerified,
          enabled = !state.isLoading,
          testTag = C.Tag.auth_verified_button,
      )
      Spacer(Modifier.height(12.dp))
      AuthButton(
          text =
              if (state.resendSecondsLeft > 0) {
                stringResource(R.string.auth_resend_email_countdown, state.resendSecondsLeft)
              } else {
                stringResource(R.string.auth_resend_email)
              },
          onClick = onResend,
          enabled = state.canResend,
          testTag = C.Tag.auth_resend_button,
          secondary = true,
      )
    }
  }
}

/** [text] with [email] in bold and in the main text color. */
@Composable
private fun boldEmail(text: String, email: String): AnnotatedString {
  val emailStyle =
      SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
  return buildAnnotatedString {
    append(text)
    val start = text.indexOf(email)
    if (start >= 0) addStyle(emailStyle, start, start + email.length)
  }
}

@Preview(showBackground = true)
@Composable
private fun CheckInboxPreview() {
  PolyLoopTheme {
    CheckInboxContent(
        state =
            AuthUiState(
                email = "jane.doe@epfl.ch",
                isAwaitingVerification = true,
                resendCooldownSeconds = 42,
                resendCooldownEmail = "jane.doe@epfl.ch",
            ),
        onVerified = {},
        onResend = {},
        onBack = {},
    )
  }
}
