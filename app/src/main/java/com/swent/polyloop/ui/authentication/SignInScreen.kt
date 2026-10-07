package com.swent.polyloop.ui.authentication

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.polyloop.model.auth.AuthError
import com.swent.polyloop.ui.theme.PolyLoopTheme

@Composable
fun SignInScreen(
    onLogin: (email: String, password: String) -> Unit = { _, _ -> },
    authError: AuthError? = null,
    onForgotPassword: () -> Unit = {},
    onNavSignup: () -> Unit = {},
) {
  val colors = MaterialTheme.colorScheme
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var isEmailFocused by remember { mutableStateOf(false) }
  var hasAttemptedLogin by remember { mutableStateOf(false) }
  var showPasswordError by remember { mutableStateOf(false) }
  val normalizedEmail = email.trim()
  val isEpflEmail =
      Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches() &&
          normalizedEmail.endsWith("@epfl.ch")
  val showEmailError = (isEmailFocused || hasAttemptedLogin) && !isEpflEmail
  val canSubmit = isEpflEmail && password.isNotBlank()

  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(colors.background)
              .padding(horizontal = 14.dp)
              .padding(top = 64.dp)
  ) {
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

    Spacer(Modifier.height(16.dp))

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(colors.surfaceVariant, RoundedCornerShape(16.dp))
                .padding(4.dp)
    ) {
      Box(
          contentAlignment = Alignment.Center,
          modifier =
              Modifier.weight(1f)
                  .height(44.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .clickable(
                      interactionSource = remember { MutableInteractionSource() },
                      indication = null,
                      onClick = onNavSignup,
                  ),
      ) {
        Text(
            text = "Sign up",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurfaceVariant,
        )
      }
      Box(
          contentAlignment = Alignment.Center,
          modifier =
              Modifier.weight(1f)
                  .height(44.dp)
                  .background(colors.surface, RoundedCornerShape(12.dp))
                  .clip(RoundedCornerShape(12.dp)),
      ) {
        Text(
            text = "Log in",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
        )
      }
    }

    Spacer(Modifier.height(16.dp))

    Text(
        text = "EPFL email",
        color = colors.onSurface,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    Column {
      OutlinedTextField(
          value = email,
          onValueChange = { email = it },
          placeholder = {
            Text("Enter your EPFL email", color = colors.onSurfaceVariant, fontSize = 18.sp)
          },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          isError = showEmailError,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
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
              Modifier.fillMaxWidth()
                  .height(62.dp)
                  .onFocusChanged { isEmailFocused = it.isFocused },
      )
      Box(modifier = Modifier.height(20.dp)) {
        if (showEmailError) {
          Text(
              text =
                  if (hasAttemptedLogin && normalizedEmail.isBlank())
                      "Please enter your email address."
                  else "Use your @epfl.ch address.",
              color = colors.error,
              fontSize = 13.sp,
          )
        }
      }
    }

    Spacer(Modifier.height(16.dp))

    Text(
        text = "Password",
        color = colors.onSurface,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    OutlinedTextField(
        value = password,
        onValueChange = {
          password = it
          if (it.isNotBlank()) showPasswordError = false
        },
        placeholder = {
          Text("Enter your password", color = colors.onSurfaceVariant, fontSize = 18.sp)
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        isError = showPasswordError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        textStyle = TextStyle(fontSize = 18.sp, color = colors.onSurface),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedBorderColor = colors.onSurface,
                unfocusedBorderColor = colors.outline,
                cursorColor = colors.primary,
            ),
        modifier = Modifier.fillMaxWidth().height(62.dp),
    )
    Box(modifier = Modifier.height(20.dp)) {
      if (showPasswordError) {
        Text(
            text = "Please enter your password.",
            color = colors.error,
            fontSize = 13.sp,
        )
      }
    }

    Spacer(Modifier.height(20.dp))

    Button(
        onClick = {
          hasAttemptedLogin = true
          showPasswordError = password.isBlank()
          if (canSubmit) onLogin(normalizedEmail, password)
        },
        shape = RoundedCornerShape(14.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (canSubmit) colors.primary else colors.surfaceVariant,
                contentColor = if (canSubmit) colors.onPrimary else colors.onSurfaceVariant,
                disabledContainerColor = if (canSubmit) colors.primary else colors.surfaceVariant,
                disabledContentColor = if (canSubmit) colors.onPrimary else colors.onSurfaceVariant,
            ),
        elevation =
            ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
                disabledElevation = 0.dp,
            ),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
      Text("Log in", fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
    Box(modifier = Modifier.height(36.dp).fillMaxWidth()) {
      authError?.warningMessage()?.let { message ->
        Text(
            text = message,
            color = colors.error,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
      }
    }

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

private fun AuthError.warningMessage(): String? =
    when (this) {
      AuthError.WRONG_CREDENTIALS -> "The email or password is incorrect."
      AuthError.WEAK_PASSWORD -> "Your password is too weak."
      AuthError.EMAIL_NOT_VERIFIED -> "Please verify your email address."
      AuthError.TOO_MANY_REQUESTS -> "Too many attempts. Please try again later."
      AuthError.NETWORK -> "A network error occurred. Please try again."
      AuthError.UNKNOWN -> "An unexpected error occurred. Please try again."
      AuthError.NAME_REQUIRED,
      AuthError.INVALID_DOMAIN,
      AuthError.EMAIL_ALREADY_IN_USE,
      AuthError.VERIFICATION_EMAIL_NOT_SENT,
      AuthError.NAME_NOT_SAVED -> null
    }

@Preview
@Composable
private fun SignInScreenPreview() {
  PolyLoopTheme { SignInScreen() }
}
