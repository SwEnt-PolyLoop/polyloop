// Made with Claude.

package com.swent.polyloop.utils

import androidx.compose.ui.platform.UriHandler

/**
 * A [UriHandler] for tests: records the links it opens in [opened] instead of opening an app. Its
 * first [failures] calls throw [failure] and record nothing; by default, the error the platform's
 * handler throws when no app can open the link.
 */
internal class FakeUriHandler(
    private var failures: Int = 0,
    private val failure: (String) -> RuntimeException = {
      IllegalArgumentException("Can't open $it.")
    },
) : UriHandler {
  val opened = mutableListOf<String>()

  override fun openUri(uri: String) {
    if (failures > 0) {
      failures--
      throw failure(uri)
    }
    opened += uri
  }
}
