// Made with Claude.

package com.swent.polyloop.model.connectivity

import kotlinx.coroutines.flow.Flow

/**
 * The phone's network status. The app reads whether it is online only through this repository, to
 * show the offline banner and disable the actions that need a connection.
 */
interface ConnectivityRepository {

  /** Whether the phone is online. Emits the current state right away, then each change. */
  val isOnline: Flow<Boolean>
}
