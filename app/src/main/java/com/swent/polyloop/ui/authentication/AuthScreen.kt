// Made with Copilot

package com.swent.polyloop.ui.authentication

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.polyloop.R
import com.swent.polyloop.model.auth.AuthError
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.profile.FakeProfileRepository
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.theme.PolyLoopTheme

/**
 * Displays the login and sign-up forms and forwards user actions to [AuthViewModel].
 *
 * Successful authentication invokes [onSignIn]. When the account needs email verification,
 * [onNavigateToVerification] is invoked so the host can open the verification step. That step
 * should share this ViewModel instance to retain the credentials needed for verification.
 *
 * @param onSignIn called after the ViewModel reports a verified signed-in user.
 * @param onNavigateToVerification called when the ViewModel reports that email verification is
 *   required.
 * @param viewModel screen state holder. By default, it is obtained from the current
 *   [androidx.lifecycle.ViewModelStoreOwner].
 */
@Composable
fun AuthScreen(
    onSignIn: () -> Unit,
    onNavigateToVerification: () -> Unit,
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory()),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(state.isSignedIn) {
    if (state.isSignedIn) {
      onSignIn()
    }
  }

  LaunchedEffect(state.isAwaitingVerification) {
    if (state.isAwaitingVerification) {
      onNavigateToVerification()
    }
  }

  if (state.isAskingName) {
    NameStep(
        state = state,
        onNameChange = viewModel::onNameChange,
        onContinue = viewModel::submitName,
    )
    return
  }

  val colors = MaterialTheme.colorScheme
  val isSignUp = state.mode == AuthMode.SIGN_UP

  /*
   * Field validation is driven by the flags exposed by AuthUiState.
   *
   * Name and password errors are shown after the user attempts to submit.
   * Email errors are shown as soon as the typed address doesn't match, or after submission.
   */
  val showEmailError = (state.email.isNotEmpty() || state.hasAttemptedSubmit) && !state.isEmailValid

  val emailError =
      when {
        !showEmailError -> null
        state.email.isBlank() -> R.string.auth_email_required
        else -> R.string.auth_email_invalid_domain
      }

  val nameError =
      if (isSignUp && state.hasAttemptedSubmit && state.isNameBlank) {
        R.string.auth_name_required
      } else {
        null
      }

  val passwordError =
      if (state.hasAttemptedSubmit && state.isPasswordBlank) {
        R.string.auth_password_required
      } else {
        null
      }

  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(colors.background)
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 14.dp)
              .padding(top = 64.dp)
  ) {
    Header()

    Spacer(Modifier.height(16.dp))

    SegmentedTabs(
        mode = state.mode,
        onModeChange = viewModel::switchMode,
    )

    Spacer(Modifier.height(16.dp))

    // The name field is only displayed in sign-up mode.
    if (isSignUp) {
      AuthTextField(
          label = stringResource(R.string.auth_name_label),
          value = state.name,
          placeholder = stringResource(R.string.auth_name_placeholder),
          onValueChange = viewModel::onNameChange,
          errorText = nameError?.let { stringResource(it) },
          testTag = C.Tag.auth_name_field,
          errorTestTag = C.Tag.auth_name_error,
      )

      Spacer(Modifier.height(16.dp))
    }

    AuthTextField(
        label = stringResource(R.string.auth_email_label),
        value = state.email,
        placeholder = stringResource(R.string.auth_email_placeholder),
        onValueChange = viewModel::onEmailChange,
        errorText = emailError?.let { stringResource(it) },
        keyboardType = KeyboardType.Email,
        testTag = C.Tag.auth_email_field,
        errorTestTag = C.Tag.auth_email_error,
    )

    Spacer(Modifier.height(16.dp))

    AuthTextField(
        label = stringResource(R.string.auth_password_label),
        value = state.password,
        placeholder = stringResource(R.string.auth_password_placeholder),
        onValueChange = viewModel::onPasswordChange,
        errorText = passwordError?.let { stringResource(it) },
        keyboardType = KeyboardType.Password,
        isPassword = true,
        testTag = C.Tag.auth_password_field,
        errorTestTag = C.Tag.auth_password_error,
    )

    Spacer(Modifier.height(20.dp))

    AuthButton(
        text = stringResource(if (isSignUp) R.string.auth_signup else R.string.auth_login),
        onClick = viewModel::submit,
        enabled = !state.isLoading,
        testTag = if (isSignUp) C.Tag.auth_signup_button else C.Tag.auth_login_button,
    )

    AuthMessage(state.error)
  }
}

/**
 * Asks for the name of a verified user whose name could not be saved at sign-up, before their
 * profile is created.
 *
 * @param state current screen state; only the name, loading flag and error are used.
 * @param onNameChange called when the user edits the name.
 * @param onContinue called when the user presses "Continue".
 */
@Composable
private fun NameStep(
    state: AuthUiState,
    onNameChange: (String) -> Unit,
    onContinue: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme

  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(colors.background)
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 14.dp)
              .padding(top = 64.dp)
  ) {
    Header()

    Spacer(Modifier.height(16.dp))

    Text(
        text = stringResource(R.string.auth_name_step_title),
        color = colors.onBackground,
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
    )

    Spacer(Modifier.height(16.dp))

    AuthTextField(
        label = stringResource(R.string.auth_name_label),
        value = state.name,
        placeholder = stringResource(R.string.auth_name_placeholder),
        onValueChange = onNameChange,
        errorText =
            if (state.hasAttemptedSubmit && state.isNameBlank) {
              stringResource(R.string.auth_name_required)
            } else {
              null
            },
        testTag = C.Tag.auth_name_field,
        errorTestTag = C.Tag.auth_name_error,
    )

    Spacer(Modifier.height(20.dp))

    AuthButton(
        text = stringResource(R.string.auth_continue),
        onClick = onContinue,
        enabled = !state.isLoading,
        testTag = C.Tag.auth_name_continue_button,
    )

    AuthMessage(state.error)
  }
}

/** Renders the PolyLoop wordmark and the short description shown above the authentication form. */
@Composable
private fun Header() {
  val colors = MaterialTheme.colorScheme
  val wordmarkStart = stringResource(R.string.auth_wordmark_start)
  val wordmarkEnd = stringResource(R.string.auth_wordmark_end)

  Text(
      text =
          buildAnnotatedString {
            withStyle(SpanStyle(color = colors.onBackground)) { append(wordmarkStart) }
            withStyle(SpanStyle(color = colors.primary)) { append(wordmarkEnd) }
          },
      fontSize = 44.sp,
      fontWeight = FontWeight.ExtraBold,
      letterSpacing = (-1.5).sp,
  )

  Text(
      text = stringResource(R.string.auth_tagline),
      color = colors.onSurfaceVariant,
      fontSize = 16.sp,
      modifier = Modifier.padding(top = 8.dp),
  )
}

/**
 * Shows the login and sign-up choices as mutually exclusive tabs.
 *
 * @param mode currently selected authentication mode.
 * @param onModeChange called with the mode selected by the user.
 */
@Composable
private fun SegmentedTabs(
    mode: AuthMode,
    onModeChange: (AuthMode) -> Unit,
) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .background(
                  MaterialTheme.colorScheme.surfaceVariant,
                  RoundedCornerShape(16.dp),
              )
              .padding(4.dp)
  ) {
    Tab(
        text = stringResource(R.string.auth_signup),
        selected = mode == AuthMode.SIGN_UP,
        testTag = C.Tag.auth_signup_tab,
    ) {
      onModeChange(AuthMode.SIGN_UP)
    }

    Tab(
        text = stringResource(R.string.auth_login),
        selected = mode == AuthMode.LOG_IN,
        testTag = C.Tag.auth_login_tab,
    ) {
      onModeChange(AuthMode.LOG_IN)
    }
  }
}

/**
 * Renders one selectable tab within [SegmentedTabs].
 *
 * @param text visible tab label.
 * @param selected whether this tab represents the current mode.
 * @param testTag semantic test tag for UI tests.
 * @param onClick called when the tab is selected.
 */
@Composable
private fun RowScope.Tab(
    text: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme

  Box(
      contentAlignment = Alignment.Center,
      modifier =
          Modifier.weight(1f)
              .height(44.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(
                  if (selected) {
                    colors.surface
                  } else {
                    colors.surfaceVariant
                  }
              )
              .clickable(
                  interactionSource = remember { MutableInteractionSource() },
                  indication = null,
                  onClick = onClick,
              )
              .testTag(testTag),
  ) {
    Text(
        text = text,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        color =
            if (selected) {
              colors.onSurface
            } else {
              colors.onSurfaceVariant
            },
    )
  }
}

/**
 * Renders a labeled text field, its optional validation message, and a stable error-message slot.
 *
 * Password values are obscured when [isPassword] is true.
 *
 * @param label visible field label.
 * @param value current field value.
 * @param placeholder hint shown while the field is empty.
 * @param onValueChange called when the user edits the value.
 * @param errorText validation message, or null when the field has no visible error.
 * @param testTag semantic test tag for UI tests.
 * @param errorTestTag semantic test tag of the validation message for UI tests.
 * @param keyboardType keyboard layout requested for the field.
 * @param isPassword whether to obscure the entered value.
 */
@Composable
private fun AuthTextField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    errorText: String?,
    testTag: String,
    errorTestTag: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
) {
  val colors = MaterialTheme.colorScheme

  Text(
      text = label,
      color = colors.onSurface,
      fontSize = 15.sp,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.padding(bottom = 8.dp),
  )

  OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      placeholder = {
        Text(
            text = placeholder,
            color = colors.onSurfaceVariant,
            fontSize = 18.sp,
        )
      },
      singleLine = true,
      shape = RoundedCornerShape(14.dp),
      isError = errorText != null,
      visualTransformation =
          if (isPassword) {
            PasswordVisualTransformation()
          } else {
            VisualTransformation.None
          },
      keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
      textStyle =
          TextStyle(
              fontSize = 18.sp,
              color = colors.onSurface,
          ),
      colors =
          OutlinedTextFieldDefaults.colors(
              focusedContainerColor = colors.surface,
              unfocusedContainerColor = colors.surface,
              focusedBorderColor = colors.onSurface,
              unfocusedBorderColor = colors.outline,
              cursorColor = colors.primary,
          ),
      modifier = Modifier.fillMaxWidth().height(62.dp).testTag(testTag),
  )

  // Keep a stable height so the form does not jump when an error appears.
  Column(
      modifier = Modifier.height(20.dp),
  ) {
    if (errorText != null) {
      Text(
          text = errorText,
          color = colors.error,
          fontSize = 13.sp,
          modifier = Modifier.testTag(errorTestTag),
      )
    }
  }
}

/**
 * Displays a form action: filled for the main one, outlined for a [secondary] one.
 *
 * @param text button label.
 * @param onClick action invoked when enabled and pressed.
 * @param enabled whether the user can invoke the action.
 * @param testTag semantic test tag for UI tests.
 * @param secondary shows the outlined style, e.g. for "Resend email" below the main action.
 */
@Composable
internal fun AuthButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    testTag: String,
    secondary: Boolean = false,
) {
  val colors = MaterialTheme.colorScheme
  val shape = RoundedCornerShape(14.dp)
  val modifier = Modifier.fillMaxWidth().height(52.dp).testTag(testTag)
  val label: @Composable RowScope.() -> Unit = {
    Text(
        text = text,
        fontSize = 17.sp,
        fontWeight = FontWeight.Medium,
    )
  }

  if (secondary) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        border = BorderStroke(1.dp, colors.outline),
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = colors.surface,
                contentColor = colors.onSurface,
            ),
        modifier = modifier,
        content = label,
    )
  } else {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
            ),
        elevation =
            ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
                disabledElevation = 0.dp,
            ),
        modifier = modifier,
        content = label,
    )
  }
}

/**
 * Shows the latest authentication error, or else the "email sent again" confirmation, below the
 * primary action. The area keeps a minimum height so the layout does not jump when a message
 * appears, and grows for a message too long for it.
 *
 * @param isEmailResent shows the confirmation of "Resend email" when there is no error.
 * @param centered centers the message, for the centered "Check your inbox" step.
 */
@Composable
internal fun AuthMessage(
    error: AuthError?,
    modifier: Modifier = Modifier,
    isEmailResent: Boolean = false,
    centered: Boolean = false,
) {
  val colors = MaterialTheme.colorScheme
  val textAlign = if (centered) TextAlign.Center else TextAlign.Start
  Column(
      modifier = modifier.heightIn(min = 36.dp).fillMaxWidth(),
      horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
  ) {
    when {
      error != null ->
          Text(
              text = stringResource(error.messageRes()),
              color = colors.error,
              fontSize = 13.sp,
              textAlign = textAlign,
              modifier = Modifier.padding(top = 4.dp).testTag(C.Tag.auth_error),
          )
      isEmailResent ->
          Text(
              text = stringResource(R.string.auth_email_resent),
              color = colors.onSurfaceVariant,
              fontSize = 13.sp,
              textAlign = textAlign,
              modifier = Modifier.padding(top = 4.dp).testTag(C.Tag.auth_email_resent),
          )
    }
  }
}

/** Converts an authentication failure into the string resource of the message shown to the user. */
@StringRes
internal fun AuthError.messageRes(): Int =
    when (this) {
      AuthError.WRONG_CREDENTIALS -> R.string.auth_error_wrong_credentials
      AuthError.WEAK_PASSWORD -> R.string.auth_error_weak_password
      AuthError.EMAIL_NOT_VERIFIED -> R.string.auth_error_email_not_verified
      AuthError.TOO_MANY_REQUESTS -> R.string.auth_error_too_many_requests
      AuthError.NETWORK -> R.string.auth_error_network
      AuthError.UNKNOWN -> R.string.auth_error_unknown
      AuthError.NAME_REQUIRED -> R.string.auth_name_required
      AuthError.INVALID_DOMAIN -> R.string.auth_email_invalid_domain
      AuthError.EMAIL_ALREADY_IN_USE -> R.string.auth_error_email_already_in_use
      AuthError.VERIFICATION_EMAIL_NOT_SENT -> R.string.auth_error_verification_email_not_sent
      AuthError.NAME_NOT_SAVED -> R.string.auth_error_name_not_saved
      AuthError.PROFILE_NOT_CREATED -> R.string.auth_error_profile_not_created
    }

/** Preview of the login form backed by an in-memory authentication repository. */
@Preview(showBackground = true)
@Composable
private fun AuthScreenLogInPreview() {
  val viewModel = remember {
    AuthViewModel(FakeAuthRepository(), FakeProfileRepository()).also {
      it.switchMode(AuthMode.LOG_IN)
    }
  }

  PolyLoopTheme {
    AuthScreen(
        onSignIn = {},
        onNavigateToVerification = {},
        viewModel = viewModel,
    )
  }
}

/** Preview of the sign-up form backed by an in-memory authentication repository. */
@Preview(showBackground = true)
@Composable
private fun AuthScreenSignUpPreview() {
  val viewModel = remember {
    AuthViewModel(FakeAuthRepository(), FakeProfileRepository()).also {
      it.switchMode(AuthMode.SIGN_UP)
    }
  }

  PolyLoopTheme {
    AuthScreen(
        onSignIn = {},
        onNavigateToVerification = {},
        viewModel = viewModel,
    )
  }
}
