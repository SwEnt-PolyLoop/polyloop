package com.swent.polyloop.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val THEME_CONTENT_TAG = "themeContent"

@RunWith(AndroidJUnit4::class)
class PolyLoopThemeTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var colorScheme: ColorScheme
  private lateinit var typography: Typography

  /** Shows a [Text] inside [PolyLoopTheme] and keeps the colors and typography it provided. */
  private fun setThemedContent() {
    composeTestRule.setContent {
      PolyLoopTheme {
        colorScheme = MaterialTheme.colorScheme
        typography = MaterialTheme.typography
        Text("PolyLoop", modifier = Modifier.testTag(THEME_CONTENT_TAG))
      }
    }
    composeTestRule.waitForIdle()
  }

  @Test
  fun themeDisplaysContent() {
    setThemedContent()

    composeTestRule.onNodeWithTag(THEME_CONTENT_TAG).assertIsDisplayed()
  }

  @Test
  fun themeUsesPolyLoopColors() {
    // Dynamic color is off, so these hold on every Android version
    setThemedContent()

    assertEquals(PolyRed, colorScheme.primary)
    assertEquals(Color.White, colorScheme.onPrimary)
    assertEquals(BlushRed, colorScheme.primaryContainer)
    assertEquals(DeepRed, colorScheme.onPrimaryContainer)
    assertEquals(DeepRed, colorScheme.secondary)
    assertEquals(Sand, colorScheme.secondaryContainer)
    assertEquals(Ink, colorScheme.onSecondaryContainer)
    assertEquals(DeepRed, colorScheme.tertiary)
    assertEquals(OfflineOrange, colorScheme.tertiaryContainer)
    assertEquals(OfflineBrown, colorScheme.onTertiaryContainer)
    assertEquals(ErrorRed, colorScheme.error)
    assertEquals(Cream, colorScheme.background)
    assertEquals(Ink, colorScheme.onBackground)
    assertEquals(Color.White, colorScheme.surface)
    assertEquals(Ink, colorScheme.onSurface)
    assertEquals(Sand, colorScheme.surfaceVariant)
    assertEquals(Stone, colorScheme.onSurfaceVariant)
    assertEquals(Color.White, colorScheme.surfaceContainer)
    assertEquals(Border, colorScheme.outline)
    assertEquals(Border, colorScheme.outlineVariant)
  }

  @Test
  fun themeUsesPolyLoopTypography() {
    setThemedContent()

    assertEquals(36.sp, typography.headlineLarge.fontSize)
    assertEquals(FontWeight.ExtraBold, typography.headlineLarge.fontWeight)
    assertEquals(26.sp, typography.headlineMedium.fontSize)
    assertEquals(17.sp, typography.titleMedium.fontSize)
    assertEquals(FontWeight.SemiBold, typography.titleMedium.fontWeight)
    assertEquals(16.sp, typography.bodyLarge.fontSize)
    assertEquals(21.6.sp, typography.bodyLarge.lineHeight)
    assertEquals(16.sp, typography.labelLarge.fontSize)
  }
}
