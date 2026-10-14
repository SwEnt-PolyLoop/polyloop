// Made with Claude.

package com.swent.polyloop

import com.swent.polyloop.model.RepositoryProvider
import com.swent.polyloop.model.connectivity.ConnectivityRepositoryAndroid
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric creates the PolyLoopApplication named in the manifest before each test.
@RunWith(RobolectricTestRunner::class)
class PolyLoopApplicationTest {

  @After
  fun tearDown() {
    RepositoryProvider.reset()
    RepositoryProvider.clearContext()
  }

  @Test
  fun onCreateGivesTheAppContextToRepositoryProvider() {
    RepositoryProvider.reset()

    assertTrue(RepositoryProvider.connectivityRepository is ConnectivityRepositoryAndroid)
  }
}
