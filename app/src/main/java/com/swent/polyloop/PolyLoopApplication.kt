// Made with Claude.

package com.swent.polyloop

import android.app.Application
import com.swent.polyloop.model.RepositoryProvider

/**
 * Created by Android before any screen. Gives [RepositoryProvider] the app context, which the
 * repositories that read the phone (such as the network status) need.
 */
class PolyLoopApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    RepositoryProvider.init(this)
  }
}
