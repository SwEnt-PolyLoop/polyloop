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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.polyloop.ui.theme.PolyLoopTheme

private val BgColor = Color(0xFFF6F4F0)
private val Ink = Color(0xFF1A1A1A)
private val Brand = Color(0xFFC8301E)
private val Muted = Color(0xFF6B6B66)
private val TabTrack = Color(0xFFEAE8E3)
private val FieldBorder = Color(0xFFE2E0DB)
private val ErrorColor = Color(0xFFA3261A)
private val DisabledText = Color(0xFF8C8A85)

@Composable
fun SignInScreen(
    onSignUp: (name: String, email: String, password: String) -> Unit = { _, _, _ -> },
    onLoginClick: () -> Unit = {},
) {
  var name by remember { mutableStateOf("") }
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  val normalizedEmail = email.trim()
  val isEpflEmail =
      Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches() &&
          normalizedEmail.endsWith("@epfl.ch")
  val showEmailError = normalizedEmail.isNotEmpty() && !isEpflEmail
  val canSubmit = isEpflEmail && password.isNotBlank()

  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(BgColor)
              .padding(horizontal = 14.dp)
              .padding(top = 64.dp)
  ) {
    Text(
        text =
            buildAnnotatedString {
              withStyle(SpanStyle(color = Ink)) { append("Poly") }
              withStyle(SpanStyle(color = Brand)) { append("Loop") }
            },
        fontSize = 44.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-1.5).sp,
    )
    Text(
        text = "Borrow and lend among EPFL students.",
        color = Muted,
        fontSize = 16.sp,
        modifier = Modifier.padding(top = 8.dp),
    )

    Spacer(Modifier.height(16.dp))

    Row(
        modifier =
            Modifier.fillMaxWidth().background(TabTrack, RoundedCornerShape(16.dp)).padding(4.dp)
    ) {
      Box(
          contentAlignment = Alignment.Center,
          modifier =
              Modifier.weight(1f)
                  .height(44.dp)
                  .background(Color.White, RoundedCornerShape(12.dp))
                  .clip(RoundedCornerShape(12.dp))
      ) {
        Text(
            text = "Sign up",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
        )
      }
      Box(
          contentAlignment = Alignment.Center,
          modifier =
              Modifier.weight(1f)
                  .height(44.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .clickable(
                      interactionSource = remember { MutableInteractionSource() },
                      indication = null,
                      onClick = onLoginClick,
                  ),
      ) {
        Text(
            text = "Log in",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = Muted,
        )
      }
    }

    Spacer(Modifier.height(16.dp))

    Text(
        text = "Name",
        color = Ink,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        textStyle = TextStyle(fontSize = 18.sp, color = Ink),
        placeholder = { Text("Enter your name", color = DisabledText, fontSize = 18.sp) },
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Ink,
                unfocusedBorderColor = FieldBorder,
                cursorColor = Ink,
            ),
        modifier = Modifier.fillMaxWidth().height(62.dp),
    )

    Spacer(Modifier.height(16.dp))

    Text(
        text = "EPFL email",
        color = Ink,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    Column {
      OutlinedTextField(
          value = email,
          onValueChange = { email = it },
          placeholder = { Text("Enter your EPFL email", color = DisabledText, fontSize = 18.sp) },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          isError = showEmailError,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
          textStyle = TextStyle(fontSize = 18.sp, color = Ink),
          colors =
              OutlinedTextFieldDefaults.colors(
                  focusedContainerColor = Color.White,
                  unfocusedContainerColor = Color.White,
                  focusedBorderColor = Ink,
                  unfocusedBorderColor = FieldBorder,
                  cursorColor = Ink,
              ),
          modifier = Modifier.fillMaxWidth().height(62.dp),
      )
      Box(modifier = Modifier.height(20.dp)) {
        if (showEmailError) {
          Text(
              text = "Use your @epfl.ch address.",
              color = ErrorColor,
              fontSize = 13.sp,
              modifier = Modifier.padding(top = 4.dp),
          )
        }
      }
    }

    Spacer(Modifier.height(16.dp))

    Text(
        text = "Password",
        color = Ink,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        placeholder = { Text("Enter your password", color = DisabledText, fontSize = 18.sp) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        textStyle = TextStyle(fontSize = 18.sp, color = Ink),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Ink,
                unfocusedBorderColor = FieldBorder,
                cursorColor = Ink,
            ),
        modifier = Modifier.fillMaxWidth().height(62.dp),
    )

    Spacer(Modifier.height(20.dp))

    Button(
        onClick = { onSignUp(name, normalizedEmail, password) },
        enabled = canSubmit,
        shape = RoundedCornerShape(14.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Brand,
                contentColor = Color.White,
                disabledContainerColor = FieldBorder,
                disabledContentColor = Muted,
            ),

        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
      Text("Sign up", fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
  }
}

@Preview
@Composable
private fun SignInScreenPreview() {
  PolyLoopTheme { SignInScreen() }
}
