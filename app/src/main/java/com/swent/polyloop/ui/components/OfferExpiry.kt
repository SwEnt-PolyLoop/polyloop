// Made with Claude.

package com.swent.polyloop.ui.components

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformLatest

/**
 * Pairs each value of this flow with the current time from [clock], and emits the value again at
 * each of its [expiries] still to come, so a screen left open shows an offer as expired right when
 * it expires, without waiting for the hourly `expireOffers` job to remove it. A new value replaces
 * the waiting one.
 *
 * @param expiries the times at which the value must be shown again, e.g. its offers' expiries.
 * @param clock the current time; the phone's clock, as the server refuses an expired offer anyway.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<T>.withNowAtExpiries(
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
