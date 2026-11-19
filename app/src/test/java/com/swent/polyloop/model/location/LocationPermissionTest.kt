// Made with Claude.

package com.swent.polyloop.model.location

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class LocationPermissionTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun isFalseWithoutThePermission() {
    shadowOf(context as Application).denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)

    assertFalse(hasCoarseLocationPermission(context))
  }

  @Test
  fun isTrueOnceTheCoarsePermissionIsGranted() {
    shadowOf(context as Application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)

    assertTrue(hasCoarseLocationPermission(context))
  }
}
