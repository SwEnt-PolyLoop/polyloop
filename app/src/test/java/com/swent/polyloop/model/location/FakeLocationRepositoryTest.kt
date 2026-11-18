// Made with Claude.

package com.swent.polyloop.model.location

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakeLocationRepositoryTest {

  @Test
  fun hasNoPositionByDefault() = runTest { assertNull(FakeLocationRepository().currentPosition()) }

  @Test
  fun returnsThePositionItWasGiven() = runTest {
    val point = MapPoint(46.52, 6.57)

    assertEquals(point, FakeLocationRepository(point).currentPosition())
  }

  @Test
  fun countsTheReads() = runTest {
    val repository = FakeLocationRepository()
    assertEquals(0, repository.reads)

    repository.currentPosition()
    repository.currentPosition()

    assertEquals(2, repository.reads)
  }

  @Test
  fun returnsTheNewPositionOnceSet() = runTest {
    val repository = FakeLocationRepository(MapPoint(46.52, 6.57))

    repository.position = null
    assertNull(repository.currentPosition())

    repository.position = MapPoint(46.0, 7.0)
    assertEquals(MapPoint(46.0, 7.0), repository.currentPosition())
  }
}
