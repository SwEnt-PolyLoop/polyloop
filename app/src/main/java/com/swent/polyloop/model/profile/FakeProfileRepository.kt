// Made with Claude.

package com.swent.polyloop.model.profile

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

/**
 * In-memory [ProfileRepository] for tests and previews; the app itself never uses it. It lives in
 * `main` so that unit tests, Compose tests and `@Preview`s can all share it.
 *
 * Holds the profiles in memory: every change is emitted by [observeProfile]. When [failure] is set,
 * every call fails with it; an observer throws it when it subscribes or at the next change, not as
 * soon as it is set. When [gate] is set, every call waits for it to complete (an observer before
 * its first value), so tests can see a loading state.
 */
class FakeProfileRepository(
    profiles: List<UserProfile> = emptyList(),
    var failure: Throwable? = null,
    var gate: CompletableDeferred<Unit>? = null,
) : ProfileRepository {

  private val stored = MutableStateFlow(profiles.associateBy { it.uid })

  /** The profiles currently stored, e.g. to check what a ViewModel saved. */
  val profiles: List<UserProfile>
    get() = stored.value.values.toList()

  override suspend fun createProfileIfMissing(
      uid: String,
      email: String,
      name: String,
  ): Result<Unit> {
    gate?.await()
    failure?.let {
      return Result.failure(it)
    }
    stored.update { if (uid in it) it else it + (uid to UserProfile(uid, name, email)) }
    return Result.success(Unit)
  }

  override suspend fun getProfile(uid: String): Result<UserProfile?> {
    gate?.await()
    return failure?.let { Result.failure(it) } ?: Result.success(stored.value[uid])
  }

  override fun observeProfile(uid: String): Flow<UserProfile?> =
      stored
          .onStart { gate?.await() }
          .map { profiles ->
            failure?.let { throw it }
            profiles[uid]
          }
          .distinctUntilChanged()

  override suspend fun updateProfile(uid: String, name: String, photoUrl: String): Result<Unit> {
    gate?.await()
    failure?.let {
      return Result.failure(it)
    }
    val profile =
        stored.value[uid] ?: return Result.failure(NoSuchElementException("No profile for $uid"))
    stored.update { it + (uid to profile.copy(name = name, photoUrl = photoUrl)) }
    return Result.success(Unit)
  }
}
