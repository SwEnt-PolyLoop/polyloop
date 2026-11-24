// Made with Claude.

package com.swent.polyloop.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.LocalDate

/**
 * Today's date on the phone, for the screens that grey out past days or write dates relative to
 * today. [ProvideToday] keeps it up to date in the app, and anything that shows dates must be under
 * it: without it, it is the date the first time it is read in the process (Compose computes a
 * default only once), so the default is for tests and previews only. The days that open the pickup,
 * the return or the cancel window go by the server's date instead (`ServerClock`).
 */
val LocalToday = compositionLocalOf<LocalDate> { LocalDate.now() }

/**
 * Gives [content] today's date through [LocalToday], from [tracker]. It is read when [content]
 * first shows, again each time the lifecycle of [LocalLifecycleOwner] starts (in the app, the
 * activity's: each time the app comes back to the foreground, as the phone's clock or time zone may
 * have changed meanwhile), and at each midnight while it stays started. A change made while it
 * stays started is read only at that midnight (see [TodayTracker]).
 */
@Composable
fun ProvideToday(
    tracker: TodayTracker = remember { TodayTracker() },
    content: @Composable () -> Unit,
) {
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  LaunchedEffect(tracker, lifecycle) {
    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { tracker.followMidnights() }
  }
  val today by tracker.today.collectAsState()
  CompositionLocalProvider(LocalToday provides today, content = content)
}
