// Made with Claude.

package com.swent.polyloop.ui.components

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest

/**
 * Pairs each value of this flow with the current time from [clock], and emits the value again at
 * each of its [expiries] still to come, so a screen left open shows an offer as expired right when
 * it expires, without waiting for the hourly `expireOffers` job to remove it. A new value replaces
 * the waiting one.
 *
 * The wait runs on the uptime clock, which stops while the phone sleeps, so after a sleep it can
 * end late. Each emission of [rechecks] emits the latest value again with the time from [clock] and
 * plans the next wait from there; screens send one when they come back to the front.
 *
 * @param expiries the times at which the value must be shown again, e.g. its offers' expiries.
 * @param clock the current time; the phone's clock, as the server refuses an expired offer anyway.
 * @param rechecks asks to read [clock] again, e.g. when the screen comes back after a sleep.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<T>.withNowAtExpiries(
    expiries: (T) -> List<Instant>,
    clock: () -> Instant,
    rechecks: Flow<Unit> = emptyFlow(),
): Flow<Pair<T, Instant>> =
    combine(this, rechecks.onStart { emit(Unit) }) { value, _ -> value }
        .nowAtExpiries(expiries, clock)

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> Flow<T>.nowAtExpiries(
    expiries: (T) -> List<Instant>,
    clock: () -> Instant,
): Flow<Pair<T, Instant>> = transformLatest { value ->
  while (true) {
    val now = clock()
    emit(value to now)
    val next = expiries(value).filter { it.isAfter(now) }.minOrNull() ?: break
    // At least 1 ms, so a clock that has not moved yet cannot make this spin.
    delay(Duration.between(now, next).toMillis().coerceAtLeast(1))
  }
}

/**
 * True once an offer expiring at [expiresAt] can no longer be answered at [now], as on the server.
 */
fun isOfferExpired(expiresAt: Instant, now: Instant): Boolean = !expiresAt.isAfter(now)
