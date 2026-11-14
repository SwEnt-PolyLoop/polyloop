// Made with Claude.

package com.swent.polyloop.ui.components

import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OfferExpiryTest {

  private val start = Instant.parse("2026-11-04T10:00:00Z")

  @Test
  fun emitsAtOnceThenAgainAtEachExpiryToComeAndStops() = runTest {
    val clock = { start.plusMillis(testScheduler.currentTime) }
    val first = start.plusSeconds(10)
    val second = start.plusSeconds(30)
    // The expiry already passed is skipped.
    val expiries = listOf(start.minusSeconds(5), second, first)

    val emitted = flowOf("v").withNowAtExpiries({ expiries }, clock).toList()

    assertEquals(listOf("v" to start, "v" to first, "v" to second), emitted)
  }

  @Test
  fun emitsOnceWhenNothingExpiresLater() = runTest {
    val clock = { start.plusMillis(testScheduler.currentTime) }

    val emitted = flowOf(1).withNowAtExpiries({ emptyList() }, clock).toList()

    assertEquals(listOf(1 to start), emitted)
  }

  @Test
  fun aNewValueReplacesTheOneWaitingForItsExpiry() = runTest {
    val clock = { start.plusMillis(testScheduler.currentTime) }
    val upstream = MutableStateFlow(start.plusSeconds(10))
    val emitted = mutableListOf<Pair<Instant, Instant>>()
    val job = launch { upstream.withNowAtExpiries({ listOf(it) }, clock).collect { emitted += it } }
    runCurrent()

    upstream.value = start.plusSeconds(60)
    runCurrent()
    advanceTimeBy(20_000)
    runCurrent()

    // Nothing at 10 s: that expiry was replaced before it came.
    assertEquals(listOf(start.plusSeconds(10) to start, start.plusSeconds(60) to start), emitted)
    job.cancel()
  }

  @Test
  fun anOfferExpiresExactlyAtItsExpiry() {
    assertFalse(isOfferExpired(start, start.minusMillis(1)))
    assertTrue(isOfferExpired(start, start))
    assertTrue(isOfferExpired(start, start.plusMillis(1)))
  }
}
