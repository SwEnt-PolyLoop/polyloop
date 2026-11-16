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

  override suspend fun currentPosition(): MapPoint? = position
}
