package com.swent.polyloop.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val THEME_CONTENT_TAG = "themeContent"

@RunWith(AndroidJUnit4::class)
class PolyLoopThemeTest {

  @get:Rule val composeTestRule = createComposeRule()

  /** Shows a [Text] inside [PolyLoopTheme] and returns the primary color the theme provided. */
  private fun setContentAndGetPrimary(darkTheme: Boolean, dynamicColor: Boolean): Color {
    var primary = Color.Unspecified
    composeTestRule.setContent {
      PolyLoopTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
        primary = MaterialTheme.colorScheme.primary
        Text("PolyLoop", modifier = Modifier.testTag(THEME_CONTENT_TAG))
      }
    }
    composeTestRule.waitForIdle()
    return primary
  }

  @Test
  fun darkThemeUsesDarkPrimaryColor() {
    val primary = setContentAndGetPrimary(darkTheme = true, dynamicColor = false)

    composeTestRule.onNodeWithTag(THEME_CONTENT_TAG).assertIsDisplayed()
    assertEquals(Purple80, primary)
  }

  @Test
  fun lightThemeUsesLightPrimaryColor() {
    val primary = setContentAndGetPrimary(darkTheme = false, dynamicColor = false)

    composeTestRule.onNodeWithTag(THEME_CONTENT_TAG).assertIsDisplayed()
    assertEquals(Purple40, primary)
  }

  @Test
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
  fun darkThemeWithDynamicColorUsesDynamicDarkPrimaryColor() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val primary = setContentAndGetPrimary(darkTheme = true, dynamicColor = true)

    composeTestRule.onNodeWithTag(THEME_CONTENT_TAG).assertIsDisplayed()
    // The exact color depends on the device wallpaper, so compare with what the system provides
    assertEquals(dynamicDarkColorScheme(context).primary, primary)
  }

  @Test
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
  fun lightThemeWithDynamicColorUsesDynamicLightPrimaryColor() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val primary = setContentAndGetPrimary(darkTheme = false, dynamicColor = true)

    composeTestRule.onNodeWithTag(THEME_CONTENT_TAG).assertIsDisplayed()
    assertEquals(dynamicLightColorScheme(context).primary, primary)
  }
}
