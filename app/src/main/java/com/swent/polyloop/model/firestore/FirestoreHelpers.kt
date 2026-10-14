// Made with Claude.

package com.swent.polyloop.model.firestore

import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

// Tools shared by the Firestore repositories, so they all read fields and handle errors the same
// way. New repositories use these; Listing, Profile and Chat still keep private copies, to be
// moved here in a follow-up.

/** How long a write waits for the server to confirm it; see [confirmWrite]. */
internal const val WRITE_TIMEOUT_MS = 10_000L

/** Firestore throws on these ids instead of failing the task, and no document can have them. */
internal fun isValidDocumentId(id: String): Boolean = id.isNotBlank() && '/' !in id

/** Firestore returns whole numbers as Long; anything else (e.g. 12.5) or too large gives null. */
internal fun intOrNull(value: Any?): Int? =
    (value as? Long)?.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()

/** The constant of [T] named [value], or null if there is none. */
internal inline fun <reified T : Enum<T>> enumOrNull(value: Any?): T? =
    enumValues<T>().firstOrNull { it.name == value }

/** A date stored as a `YYYY-MM-DD` string, or null if it is missing or malformed. */
internal fun dateOrNull(value: Any?): LocalDate? =
    try {
      (value as? String)?.let(LocalDate::parse)
    } catch (e: DateTimeParseException) {
      null
    }

/**
 * A date-time stored as a Firestore Timestamp, or null. Read the document with
 * `ServerTimestampBehavior.ESTIMATE`: otherwise a server timestamp that the server has not
 * confirmed yet reads as null.
 */
internal fun instantOrNull(value: Any?): Instant? = (value as? Timestamp)?.toInstant()

/**
 * Runs [block] and returns Firebase errors as failures. A cancellation is rethrown only if the
 * caller was cancelled (e.g. the screen closed); a task that Firebase cancelled itself is an
 * ordinary failure. Other exceptions are bugs and are not hidden.
 */
internal suspend fun <T> withFirebaseErrors(block: suspend () -> Result<T>): Result<T> =
    try {
      block()
    } catch (e: CancellationException) {
      currentCoroutineContext().ensureActive()
      Result.failure(e)
    } catch (e: FirebaseException) {
      Result.failure(e)
    }

/**
 * Runs [write] and returns whether the server confirmed it within [timeoutMs]. A write only
 * completes once the server confirms it, which never happens if the connection drops, so stop
 * waiting after a while. Firestore still sends the queued write later.
 */
internal suspend fun confirmWrite(
    timeoutMs: Long = WRITE_TIMEOUT_MS,
    write: () -> Task<*>,
): Boolean =
    withTimeoutOrNull(timeoutMs) {
      write().await()
      true // await() on a write returns null even on success
    } ?: false

/**
 * Listens to this query and emits [transform] of every snapshot. A read error closes the flow with
 * that error, so the collector's `catch` sees it (Firestore's own `snapshots()` cancels the flow
 * instead, which `catch` does not report as an error).
 */
internal fun <T> Query.snapshotsAsFlow(transform: (QuerySnapshot) -> T): Flow<T> = callbackFlow {
  val registration = addSnapshotListener { snapshot, error ->
    if (error != null) close(error) else if (snapshot != null) trySend(transform(snapshot))
  }
  awaitClose { registration.remove() }
}
