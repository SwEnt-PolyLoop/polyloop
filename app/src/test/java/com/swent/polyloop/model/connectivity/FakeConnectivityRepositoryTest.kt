// Made with Claude.

package com.swent.polyloop.model.connectivity

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeConnectivityRepositoryTest {

  @Test
  fun startsOnlineByDefault() = runTest {
    assertTrue(FakeConnectivityRepository().isOnline.first())
  }

  @Test
  fun startsOfflineWhenAsked() = runTest {
    assertFalse(FakeConnectivityRepository(online = false).isOnline.first())
  }

  @Test
  fun setOnlineEmitsEachChange() = runTest {
    val repository = FakeConnectivityRepository()
    val values = mutableListOf<Boolean>()
    val job = launch(UnconfinedTestDispatcher(testScheduler)) { repository.isOnline.toList(values) }

    repository.setOnline(false)
    repository.setOnline(true)

    assertEquals(listOf(true, false, true), values)
    job.cancel()
  }
}
