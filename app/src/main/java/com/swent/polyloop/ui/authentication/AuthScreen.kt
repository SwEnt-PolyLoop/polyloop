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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.polyloop.model.auth.AuthError
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.auth.AuthResult
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.ui.theme.PolyLoopTheme

private const val NAME_FIELD_TAG = "signInName"
private const val EMAIL_FIELD_TAG = "signInEmail"
private const val PASSWORD_FIELD_TAG = "signInPassword"
private const val LOGIN_BUTTON_TAG = "signInLoginButton"
private const val SIGNUP_BUTTON_TAG = "signInSignupButton"
private const val LOGIN_TAB_TAG = "signInLoginTab"
private const val SIGNUP_TAB_TAG = "signInSignupTab"

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
  val state by viewModel.uiState.collectAsState()

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

  val colors = MaterialTheme.colorScheme
  val isSignUp = state.mode == AuthMode.SIGN_UP

  var isEmailFocused by remember { mutableStateOf(false) }

  /*
   * Field validation is driven by the flags exposed by AuthUiState.
   *
   * Name and password errors are shown after the user attempts to submit.
   * Email errors are shown when the field is focused or after submission.
   */
  val showEmailError =
      (isEmailFocused || state.hasAttemptedSubmit) && !state.isEmailValid

  val emailError =
      when {
        !showEmailError -> null
        state.email.isBlank() -> "Please enter your email address."
        else -> "Use your @epfl.ch address."
      }

  val nameError =
      if (isSignUp && state.hasAttemptedSubmit && state.isNameBlank) {
        "Please enter your name."
      } else {
        null
      }

  val passwordError =
      if (state.hasAttemptedSubmit && state.isPasswordBlank) {
        "Please enter your password."
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
          label = "Name",
          value = state.name,
          placeholder = "Enter your name",
          onValueChange = viewModel::onNameChange,
          errorText = nameError,
          testTag = NAME_FIELD_TAG,
      )

      Spacer(Modifier.height(16.dp))
    }

    AuthTextField(
        label = "EPFL email",
        value = state.email,
        placeholder = "Enter your EPFL email",
        onValueChange = viewModel::onEmailChange,
        errorText = emailError,
        keyboardType = KeyboardType.Email,
        onFocusChange = { isEmailFocused = it },
        testTag = EMAIL_FIELD_TAG,
    )

    Spacer(Modifier.height(16.dp))

    AuthTextField(
        label = "Password",
        value = state.password,
        placeholder = "Enter your password",
        onValueChange = viewModel::onPasswordChange,
        errorText = passwordError,
        keyboardType = KeyboardType.Password,
        isPassword = true,
        testTag = PASSWORD_FIELD_TAG,
    )

    Spacer(Modifier.height(20.dp))

    AuthButton(
        text = if (isSignUp) "Sign up" else "Log in",
        onClick = viewModel::submit,
        enabled = !state.isLoading,
        testTag = if (isSignUp) SIGNUP_BUTTON_TAG else LOGIN_BUTTON_TAG,
    )

    ErrorMessage(state.error)
  }
}

/** Renders the PolyLoop wordmark and the short description shown above the authentication form. */
@Composable
private fun Header() {
  val colors = MaterialTheme.colorScheme

  Text(
      text =
          buildAnnotatedString {
            withStyle(SpanStyle(color = colors.onBackground)) {
              append("Poly")
            }
            withStyle(SpanStyle(color = colors.primary)) {
              append("Loop")
            }
          },
      fontSize = 44.sp,
      fontWeight = FontWeight.ExtraBold,
      letterSpacing = (-1.5).sp,
  )

  Text(
      text = "Borrow and lend among EPFL students.",
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
        text = "Sign up",
        selected = mode == AuthMode.SIGN_UP,
        testTag = SIGNUP_TAB_TAG,
    ) {
      onModeChange(AuthMode.SIGN_UP)
    }

    Tab(
        text = "Log in",
        selected = mode == AuthMode.LOG_IN,
        testTag = LOGIN_TAB_TAG,
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
 * Password values are obscured when [isPassword] is true. Focus changes are reported through
 * [onFocusChange], allowing the caller to control when validation feedback appears.
 *
 * @param label visible field label.
 * @param value current field value.
 * @param placeholder hint shown while the field is empty.
 * @param onValueChange called when the user edits the value.
 * @param errorText validation message, or null when the field has no visible error.
 * @param testTag semantic test tag for UI tests.
 * @param keyboardType keyboard layout requested for the field.
 * @param isPassword whether to obscure the entered value.
 * @param onFocusChange called when the field gains or loses focus.
 */
@Composable
private fun AuthTextField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    errorText: String?,
    testTag: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    onFocusChange: (Boolean) -> Unit = {},
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
      textStyle = TextStyle(
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
      modifier =
          Modifier.fillMaxWidth()
              .height(62.dp)
              .testTag(testTag)
              .onFocusChanged {
                onFocusChange(it.isFocused)
              },
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
      )
    }
  }
}

/**
 * Displays the primary form action.
 *
 * @param text button label.
 * @param onClick action invoked when enabled and pressed.
 * @param enabled whether the user can invoke the action.
 * @param testTag semantic test tag for UI tests.
 */
@Composable
private fun AuthButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    testTag: String,
) {
  val colors = MaterialTheme.colorScheme

  Button(
      onClick = onClick,
      enabled = enabled,
      shape = RoundedCornerShape(14.dp),
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
      modifier =
          Modifier.fillMaxWidth()
              .height(52.dp)
              .testTag(testTag),
  ) {
    Text(
        text = text,
        fontSize = 17.sp,
        fontWeight = FontWeight.Medium,
    )
  }
}

/** Shows the latest authentication error in a fixed-height area below the primary action. */
@Composable
private fun ErrorMessage(error: AuthError?) {
  Column(
      modifier = Modifier
          .height(36.dp)
          .fillMaxWidth()
  ) {
    if (error != null) {
      Text(
          text = error.message(),
          color = MaterialTheme.colorScheme.error,
          fontSize = 13.sp,
          modifier = Modifier.padding(top = 4.dp),
      )
    }
  }
}

/** Converts an authentication failure into the message displayed to the user. */
private fun AuthError.message(): String =
    when (this) {
      AuthError.WRONG_CREDENTIALS ->
          "The email or password is incorrect."

      AuthError.WEAK_PASSWORD ->
          "Your password is too weak."

      AuthError.EMAIL_NOT_VERIFIED ->
          "Please verify your email address."

      AuthError.TOO_MANY_REQUESTS ->
          "Too many attempts. Please try again later."

      AuthError.NETWORK ->
          "A network error occurred. Please try again."

      AuthError.UNKNOWN ->
          "An unexpected error occurred. Please try again."

      AuthError.NAME_REQUIRED ->
          "Please enter your name."

      AuthError.INVALID_DOMAIN ->
          "Use your @epfl.ch address."

      AuthError.EMAIL_ALREADY_IN_USE ->
          "This email already has an account. Try logging in."

      AuthError.VERIFICATION_EMAIL_NOT_SENT ->
          "Account created, but the verification email could not be sent."

      AuthError.NAME_NOT_SAVED ->
          "Account created, but your name could not be saved."
    }

/** Preview of the login form backed by an in-memory authentication repository. */
@Preview(showBackground = true)
@Composable
private fun AuthScreenLogInPreview() {
  val viewModel = remember {
    AuthViewModel(PreviewAuthRepository()).also {
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
    AuthViewModel(PreviewAuthRepository()).also {
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

/** Interactive preview of the sign-up form using successful in-memory authentication responses. */
@Preview(showBackground = true)
@Composable
private fun AuthScreenInteractivePreview() {
  val viewModel = remember {
    AuthViewModel(PreviewAuthRepository()).also {
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

/** In-memory repository used by previews to avoid Firebase dependencies. */
private class PreviewAuthRepository : AuthRepository {

  override suspend fun signUp(
      name: String,
      email: String,
      password: String,
  ): AuthResult<Unit> =
      AuthResult.Success(Unit)

  override suspend fun signIn(
      email: String,
      password: String,
  ): AuthResult<AuthUser> =
      AuthResult.Success(
          AuthUser(
              uid = "preview",
              email = email,
              name = "Preview",
          )
      )

  override suspend fun resendVerificationEmail(
      email: String,
      password: String,
  ): AuthResult<Unit> =
      AuthResult.Success(Unit)

  override fun getCurrentUser(): AuthUser? = null

  override fun signOut() = Unit
}