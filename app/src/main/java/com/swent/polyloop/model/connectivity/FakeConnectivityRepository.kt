// Made with Claude.

package com.swent.polyloop.model.connectivity

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [ConnectivityRepository] for tests and previews; the app itself never uses it. It lives
 * in `main` so that unit tests, Compose tests and `@Preview`s can all share it.
 *
 * Starts [online] (online by default); call [setOnline] to simulate the phone going offline or back
 * online.
 */
class FakeConnectivityRepository(online: Boolean = true) : ConnectivityRepository {

  private val state = MutableStateFlow(online)

  override val isOnline: Flow<Boolean> = state

  fun setOnline(online: Boolean) {
    state.value = online
  }
}
