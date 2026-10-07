//Made with Copilot
//Made with Copilot

package com.swent.polyloop.ui.authentication

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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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
 * Log-in / sign-up screen backed by [AuthViewModel].
 *
 * The email verification step is a separate route: [onNavigateToVerification] is called when the
 * ViewModel says the user must verify their email, and [onSignIn] once a verified user is in.
 *
 * The default ViewModel is scoped to the nav back stack entry of this route. The verification
 * screen must reuse that same instance (it needs the typed email and password).
 */
@Composable
fun AuthScreen(
    onSignIn: () -> Unit,
    onNavigateToVerification: () -> Unit,
    onForgotPassword: () -> Unit = {},
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory()),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isSignedIn) { if (state.isSignedIn) onSignIn() }
    LaunchedEffect(state.isAwaitingVerification) {
        if (state.isAwaitingVerification) onNavigateToVerification()
    }

    val colors = MaterialTheme.colorScheme
    val isSignUp = state.mode == AuthMode.SIGN_UP
    var isEmailFocused by remember { mutableStateOf(false) }

    val showEmailError = (isEmailFocused || state.hasAttemptedSubmit) && !state.isEmailValid
    val emailError =
        when {
            !showEmailError -> null
            state.hasAttemptedSubmit && state.email.isBlank() -> "Please enter your email address."
            else -> "Use your @epfl.ch address."
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

        SegmentedTabs(state.mode, viewModel::switchMode)
        Spacer(Modifier.height(16.dp))

        // The only field that depends on the mode.
        if (isSignUp) {
            AuthTextField(
                label = "Name",
                value = state.name,
                placeholder = "Enter your name",
                onValueChange = viewModel::onNameChange,
                errorText =
                    if (state.hasAttemptedSubmit && state.name.isBlank()) "Please enter your name."
                    else null,
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
            errorText =
                if (state.hasAttemptedSubmit && state.password.isBlank()) "Please enter your password."
                else null,
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

        if (!isSignUp) {
            TextButton(
                onClick = onForgotPassword,
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
            ) {
                Text(
                    text = "Forgot password?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.Underline,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Header() {
    val colors = MaterialTheme.colorScheme
    Text(
        text =
            buildAnnotatedString {
                withStyle(SpanStyle(color = colors.onBackground)) { append("Poly") }
                withStyle(SpanStyle(color = colors.primary)) { append("Loop") }
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

@Composable
private fun SegmentedTabs(mode: AuthMode, onModeChange: (AuthMode) -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                .padding(4.dp)
    ) {
        Tab("Sign up", mode == AuthMode.SIGN_UP, SIGNUP_TAB_TAG) { onModeChange(AuthMode.SIGN_UP) }
        Tab("Log in", mode == AuthMode.LOG_IN, LOGIN_TAB_TAG) { onModeChange(AuthMode.LOG_IN) }
    }
}

@Composable
private fun RowScope.Tab(text: String, selected: Boolean, testTag: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) colors.surface else colors.surfaceVariant)
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
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
        )
    }
}

/** Label + outlined field + a fixed-height slot for the error, so the layout does not jump. */
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
        placeholder = { Text(placeholder, color = colors.onSurfaceVariant, fontSize = 18.sp) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        isError = errorText != null,
        visualTransformation =
            if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = TextStyle(fontSize = 18.sp, color = colors.onSurface),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedBorderColor = colors.onSurface,
                unfocusedBorderColor = colors.outline,
                cursorColor = colors.primary,
            ),
        modifier =
            Modifier.fillMaxWidth().height(62.dp).testTag(testTag).onFocusChanged {
                onFocusChange(it.isFocused)
            },
    )
    Column(modifier = Modifier.height(20.dp)) {
        if (errorText != null) {
            Text(text = errorText, color = colors.error, fontSize = 13.sp)
        }
    }
}

@Composable
private fun AuthButton(text: String, onClick: () -> Unit, enabled: Boolean, testTag: String) {
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
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag(testTag),
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}

/** Fixed-height slot under the main button for the last action's error. */
@Composable
private fun ErrorMessage(error: AuthError?) {
    Column(modifier = Modifier.height(36.dp).fillMaxWidth()) {
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

private fun AuthError.message(): String =
    when (this) {
        AuthError.WRONG_CREDENTIALS -> "The email or password is incorrect."
        AuthError.WEAK_PASSWORD -> "Your password is too weak."
        AuthError.EMAIL_NOT_VERIFIED -> "Please verify your email address."
        AuthError.TOO_MANY_REQUESTS -> "Too many attempts. Please try again later."
        AuthError.NETWORK -> "A network error occurred. Please try again."
        AuthError.UNKNOWN -> "An unexpected error occurred. Please try again."
        AuthError.NAME_REQUIRED -> "Please enter your name."
        AuthError.INVALID_DOMAIN -> "Use your @epfl.ch address."
        AuthError.EMAIL_ALREADY_IN_USE -> "This email already has an account. Try logging in."
        AuthError.VERIFICATION_EMAIL_NOT_SENT ->
            "Account created, but the verification email could not be sent."
        AuthError.NAME_NOT_SAVED -> "Account created, but your name could not be saved."
    }

@Preview(showBackground = true)
@Composable
private fun AuthScreenLogInPreview() {
    val viewModel =
        remember { AuthViewModel(PreviewAuthRepository()).also { it.switchMode(AuthMode.LOG_IN) } }
    PolyLoopTheme {
        AuthScreen(
            onSignIn = {},
            onNavigateToVerification = {},
            viewModel = viewModel,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AuthScreenSignUpPreview() {
    val viewModel =
        remember { AuthViewModel(PreviewAuthRepository()).also { it.switchMode(AuthMode.SIGN_UP) } }
    PolyLoopTheme {
        AuthScreen(
            onSignIn = {},
            onNavigateToVerification = {},
            viewModel = viewModel,
        )
    }
}


@Preview(showBackground = true)
@Composable
private fun AuthScreenInteractivePreview() {
    val viewModel =
        remember { AuthViewModel(PreviewAuthRepository()).also { it.switchMode(AuthMode.SIGN_UP) } }
    PolyLoopTheme {
        AuthScreen(
            onSignIn = {},
            onNavigateToVerification = {},
            viewModel = viewModel,
        )
    }
}

private class PreviewAuthRepository : AuthRepository {
    override suspend fun signUp(name: String, email: String, password: String): AuthResult<Unit> =
        AuthResult.Success(Unit)

    override suspend fun signIn(email: String, password: String): AuthResult<AuthUser> =
        AuthResult.Success(AuthUser(uid = "preview", email = email, name = "Preview"))

    override suspend fun resendVerificationEmail(email: String, password: String): AuthResult<Unit> =
        AuthResult.Success(Unit)

    override fun getCurrentUser(): AuthUser? = null

    override fun signOut() = Unit
}