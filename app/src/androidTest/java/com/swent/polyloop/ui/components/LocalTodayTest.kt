// Made with Claude.

package com.swent.polyloop.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.resources.C
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalTodayTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun oct(day: Int) = LocalDate.of(2026, 10, day)

  private fun day(date: LocalDate) = composeTestRule.onNodeWithTag(C.Tag.datePickerDay(date))

  @Test
  fun datePickerGreysOutTodayOnceLocalTodayReachesIt() {
    var today by mutableStateOf(oct(5))
    composeTestRule.setContent {
      CompositionLocalProvider(LocalToday provides today) {
        PolyLoopDateRangePicker(
            window = oct(1)..oct(20),
            disabledDates = emptySet(),
            mode = DateRangePickerMode.Request(pricePerDay = 10, balance = 1000),
            onConfirm = { _, _ -> },
            onDismiss = {},
        )
      }
    }
    // A request starts tomorrow at the earliest.
    day(oct(5)).assertIsNotEnabled()
    day(oct(6)).assertIsEnabled()

    today = oct(6) // midnight

    day(oct(6)).assertIsNotEnabled()
    day(oct(7)).assertIsEnabled()
  }

  @Test
  fun provideTodayReadsTheDateAgainWhenTheAppComesBackToTheForeground() {
    var now = ZonedDateTime.of(2026, 10, 13, 15, 0, 0, 0, ZoneId.of("Europe/Zurich"))
    val tracker = TodayTracker { now }
    val owner =
        object : LifecycleOwner {
          override val lifecycle = LifecycleRegistry(this)
        }
    composeTestRule.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
    composeTestRule.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides owner) {
        ProvideToday(tracker) { Text(LocalToday.current.toString()) }
      }
    }
    composeTestRule.onNodeWithText("2026-10-13").assertIsDisplayed()

    // In the background for two days: the date is read again only once the app is back.
    composeTestRule.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.CREATED }
    composeTestRule.runOnIdle { now = now.plusDays(2) }
    composeTestRule.onNodeWithText("2026-10-13").assertIsDisplayed()

    composeTestRule.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
    composeTestRule.onNodeWithText("2026-10-15").assertIsDisplayed()
  }
}
