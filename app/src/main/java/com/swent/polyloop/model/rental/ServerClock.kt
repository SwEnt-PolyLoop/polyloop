// Made with Claude.

package com.swent.polyloop.model.rental

import android.content.Context
import android.os.SystemClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Today's date by the server's clock, for the screens that must not trust the phone's date (Start
 * Pickup): the user can change the phone's date in Settings, but not the server's.
 *
 * Each time the server is reached, its time is saved next to [elapsedRealtime] at that moment.
 * [elapsedRealtime] counts from boot, cannot be changed in Settings and keeps running offline, so
 * the server's time now is the saved time plus the time elapsed since, with no signal needed. The
 * pair is saved with [bootCount] and dropped after a reboot, because [elapsedRealtime] restarts
 * from 0 at boot and an old pair would then give a wrong day.
 *
 * @param repository reads the server's time.
 * @param store keeps the last pair, on the phone in the app, so it survives the app being closed.
 * @param elapsedRealtime the milliseconds since the phone booted.
 * @param bootCount how many times the phone has booted; it changes at each reboot.
 */
class ServerClock(
    private val repository: RentalRepository,
    private val store: Store,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    private val bootCount: () -> Int,
) {

  /**
   * Today in Zurich by the server's clock: asked from the server, or worked out from the last saved
   * pair when the server cannot be reached. Null when it is unknown: the server cannot be reached
   * and nothing was saved since the phone booted. Callers then treat the day as unknown rather than
   * fall back on the phone's date, which airplane mode plus a changed date would otherwise move.
   */
  suspend fun today(): LocalDate? {
    val result = repository.serverNow()
    // Read once the answer arrives, after the server read its clock, so the worked-out time can
    // only lag behind the server's, never run ahead of it and open a day early.
    val elapsed = elapsedRealtime()
    result.onSuccess { now ->
      store.save(Saved(now.toEpochMilli(), elapsed, bootCount()))
      return dayOf(now)
    }
    val saved = store.load() ?: return null
    // A smaller elapsed time means the phone rebooted, even if the boot count did not change.
    if (saved.bootCount != bootCount() || elapsed < saved.elapsedRealtimeMs) return null
    return dayOf(Instant.ofEpochMilli(saved.serverTimeMs + (elapsed - saved.elapsedRealtimeMs)))
  }

  /**
   * The server's time [serverTimeMs] (epoch ms), read when [elapsedRealtimeMs] had elapsed since
   * the phone's boot number [bootCount].
   */
  data class Saved(val serverTimeMs: Long, val elapsedRealtimeMs: Long, val bootCount: Int)

  /** Keeps the last [Saved] pair. */
  interface Store {
    /** The last pair saved, or null if there is none. */
    fun load(): Saved?

    /** Replaces the saved pair with [saved]. */
    fun save(saved: Saved)
  }

  private companion object {
    // Rental dates are Swiss days, as in the serverToday function, wherever the phone is set.
    val ZURICH: ZoneId = ZoneId.of("Europe/Zurich")

    fun dayOf(instant: Instant): LocalDate = instant.atZone(ZURICH).toLocalDate()
  }
}

/**
 * [ServerClock.Store] in the app's shared preferences, so the pair survives the app being closed.
 * Clearing the app's data drops it, and the day is unknown until the server is reached again.
 */
class SharedPreferencesServerClockStore(context: Context) : ServerClock.Store {

  private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

  override fun load(): ServerClock.Saved? {
    if (!preferences.contains(SERVER_TIME)) return null
    return ServerClock.Saved(
        serverTimeMs = preferences.getLong(SERVER_TIME, 0),
        elapsedRealtimeMs = preferences.getLong(ELAPSED_REALTIME, 0),
        bootCount = preferences.getInt(BOOT_COUNT, 0),
    )
  }

  override fun save(saved: ServerClock.Saved) {
    preferences
        .edit()
        .putLong(SERVER_TIME, saved.serverTimeMs)
        .putLong(ELAPSED_REALTIME, saved.elapsedRealtimeMs)
        .putInt(BOOT_COUNT, saved.bootCount)
        .apply()
  }

  private companion object {
    const val FILE = "server_clock"
    const val SERVER_TIME = "server_time_ms"
    const val ELAPSED_REALTIME = "elapsed_realtime_ms"
    const val BOOT_COUNT = "boot_count"
  }
}

/**
 * In-memory [ServerClock.Store] for tests and previews; the app itself never uses it. It lives in
 * `main` so that unit tests, Compose tests and `@Preview`s can all share it.
 */
class InMemoryServerClockStore(private var saved: ServerClock.Saved? = null) : ServerClock.Store {

  override fun load(): ServerClock.Saved? = saved

  override fun save(saved: ServerClock.Saved) {
    this.saved = saved
  }
}
