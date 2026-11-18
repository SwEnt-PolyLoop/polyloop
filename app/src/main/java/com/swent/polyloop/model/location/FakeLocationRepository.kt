// Made with Claude.

package com.swent.polyloop.model.location

/**
 * In-memory [LocationRepository] for tests and previews; the app itself never uses it. It lives in
 * `main` so that unit tests, Compose tests and `@Preview`s can all share it.
 *
 * Reports [position] (none by default, as when the permission is refused); set it to simulate a fix
 * or a lost one.
 */
class FakeLocationRepository(var position: MapPoint? = null) : LocationRepository {

  /** How many times the position was read. */
  var reads = 0
    private set

  override suspend fun currentPosition(): MapPoint? {
    reads++
    return position
  }
}
