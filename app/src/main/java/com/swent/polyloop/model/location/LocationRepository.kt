// Made with Claude.

package com.swent.polyloop.model.location

/**
 * The phone's position. The app reads the GPS only through this repository, to sort Browse by
 * distance.
 */
interface LocationRepository {

  /**
   * The phone's current position, or null when the coarse location permission is not granted or
   * there is no fix (no provider, a timeout, or no known location).
   */
  suspend fun currentPosition(): MapPoint?
}
