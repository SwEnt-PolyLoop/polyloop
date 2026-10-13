package com.swent.polyloop.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Light theme only, and no dynamic color, so the app always shows the PolyLoop palette.
// If these values ever change, modify the asserts in PolyLoopThemeTest.
private val LightColorScheme =
    lightColorScheme(
        primary = PolyRed,
        onPrimary = Color.White,
        primaryContainer = BlushRed,
        onPrimaryContainer = DeepRed,
        secondary = DeepRed,
        onSecondary = Color.White,
        secondaryContainer = Sand,
        onSecondaryContainer = Ink,
        tertiary = DeepRed,
        tertiaryContainer = OfflineOrange,
        onTertiaryContainer = OfflineBrown,
        error = ErrorRed,
        onError = Color.White,
        background = Cream,
        onBackground = Ink,
        surface = Color.White,
        onSurface = Ink,
        surfaceVariant = Sand,
        onSurfaceVariant = Stone,
        surfaceContainer = Color.White,
        outline = Border,
        outlineVariant = Border,
    )

@Composable
fun PolyLoopTheme(content: @Composable () -> Unit) {
  val colorScheme = LightColorScheme
  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as Activity).window
      WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
    }
  }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
