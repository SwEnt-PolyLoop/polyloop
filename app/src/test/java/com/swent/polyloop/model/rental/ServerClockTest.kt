// Made with Claude.

package com.swent.polyloop.model.rental

import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

// Robolectric only for SharedPreferencesServerClockStore, which needs a Context.
@RunWith(RobolectricTestRunner::class)
class ServerClockTest {

  private val repository = FakeRentalRepository()
  private val store = InMemoryServerClockStore()
  private var elapsed = 5_000L
  private var boot = 3

  private fun clock(store: ServerClock.Store = this.store) =
      ServerClock(repository, store, elapsedRealtime = { elapsed }, bootCount = { boot })

  private fun goOffline() {
    repository.failure = IOException("offline")
  }

  @Test
  fun successGivesTheServersDayInZurich() = runTest {
    repository.serverNow = Instant.parse("2026-11-11T10:00:00Z")

    assertEquals(LocalDate.of(2026, 11, 11), clock().today())
  }

  @Test
  fun lateEveningUtcInWinterIsAlreadyTheNextDayInZurich() = runTest {
    repository.serverNow = Instant.parse("2026-11-11T23:30:00Z")

    assertEquals(LocalDate.of(2026, 11, 12), clock().today())
  }

  @Test
  fun offlineAfterASuccessWorksOutTheDayFromTheElapsedTime() = runTest {
    val clock = clock()
    repository.serverNow = Instant.parse("2026-11-11T21:30:00Z") // 22:30 in Zurich
    clock.today()

    goOffline()
    elapsed += Duration.ofHours(2).toMillis()

    assertEquals(LocalDate.of(2026, 11, 12), clock.today())
  }

  @Test
  fun offlineWithNothingSavedIsUnknown() = runTest {
    goOffline()

    assertNull(clock().today())
  }

  @Test
  fun offlineWithAPairFromAnotherBootIsUnknown() = runTest {
    clock().today()

    goOffline()
    boot++
    elapsed = 1_000L

    assertNull(clock().today())
  }

  @Test
  fun offlineWithASmallerElapsedTimeIsUnknownEvenWithTheSameBootCount() = runTest {
    clock().today()

    goOffline()
    elapsed -= 1

    assertNull(clock().today())
  }

  @Test
  fun aNewClockOnTheSameStoreStillWorksOffline() = runTest {
    repository.serverNow = Instant.parse("2026-11-11T21:30:00Z")
    clock().today()

    // The app was closed and opened again, without a reboot.
    goOffline()
    elapsed += Duration.ofHours(3).toMillis()

    assertEquals(LocalDate.of(2026, 11, 12), clock().today())
  }

  @Test
  fun aNewerSuccessReplacesTheSavedPair() = runTest {
    repository.serverNow = Instant.parse("2026-11-11T10:00:00Z")
    clock().today()
    elapsed += 1_000L
    repository.serverNow = Instant.parse("2026-11-20T10:00:00Z")
    clock().today()

    assertEquals(
        ServerClock.Saved(Instant.parse("2026-11-20T10:00:00Z").toEpochMilli(), elapsed, boot),
        store.load(),
    )
    goOffline()
    assertEquals(LocalDate.of(2026, 11, 20), clock().today())
  }

  @Test
  fun sharedPreferencesStoreSavesAndLoadsThePair() {
    val context = RuntimeEnvironment.getApplication()
    assertNull(SharedPreferencesServerClockStore(context).load())

    val saved =
        ServerClock.Saved(serverTimeMs = 1_762_858_800_000L, elapsedRealtimeMs = 42L, bootCount = 7)
    SharedPreferencesServerClockStore(context).save(saved)

    // Another instance, as after the app was closed and opened again.
    assertEquals(saved, SharedPreferencesServerClockStore(context).load())
  }
}
