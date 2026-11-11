// Made with Claude.

package com.swent.polyloop.ui.createlisting

import android.content.Context
import java.io.File

/**
 * A file in the app's photo folder last changed two days ago, so the cleanup of old photos finds it
 * stale. The caller deletes it when the test ends.
 *
 * @param context the app context, to find the photo folder in its files directory.
 * @param name the file's name, which should be unique to the test.
 * @return the new file, which exists and holds a few bytes.
 */
internal fun staleFile(context: Context, name: String): File {
  val dir = File(context.filesDir, LISTING_PHOTOS_DIR).apply { mkdirs() }
  return File(dir, name).apply {
    writeText("jpeg")
    setLastModified(System.currentTimeMillis() - 2 * STALE_PHOTO_AGE_MS)
  }
}
