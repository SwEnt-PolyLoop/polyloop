// Made with Claude.

package com.swent.polyloop.ui.createlisting

import android.util.Size
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import com.swent.polyloop.model.listing.Listing
import java.io.File
import java.net.URI
import java.util.UUID

/** Folder inside the app's files directory where the in-app camera saves listing photos. */
internal const val LISTING_PHOTOS_DIR = "listing_photos"

/** Long side of the size listing photos are shot at, in pixels (4:3). */
internal const val PHOTO_LONG_SIDE_PX = 1440

/** Short side of the size listing photos are shot at, in pixels (4:3). */
internal const val PHOTO_SHORT_SIDE_PX = 1080

/** JPEG quality of listing photos, from 1 to 100. */
internal const val PHOTO_JPEG_QUALITY = 80

/** Photo files older than this (24 hours) are leftovers from an app killed mid-form. */
internal const val STALE_PHOTO_AGE_MS = 24L * 60 * 60 * 1000

/**
 * The size the camera shoots listing photos at: [PHOTO_LONG_SIDE_PX] x [PHOTO_SHORT_SIDE_PX] in
 * 4:3, or the closest size the camera offers, below it first, so a photo never gets bigger than
 * asked unless nothing smaller exists.
 *
 * @return the selector to give the camera's image capture, with a 4:3 aspect ratio.
 */
internal fun photoResolutionSelector(): ResolutionSelector =
    ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
        .setResolutionStrategy(
            ResolutionStrategy(
                Size(PHOTO_LONG_SIDE_PX, PHOTO_SHORT_SIDE_PX),
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
            )
        )
        .build()

/**
 * Deletes the photo file at [uri]. Only a `file:` URI whose file sits directly in a folder named
 * [LISTING_PHOTOS_DIR] is deleted (the path is resolved first, so `..` cannot escape the folder);
 * anything else is left alone. A missing file or a malformed [uri] is ignored; it never throws.
 *
 * @param uri the photo's location as a URI string, such as `file:///data/.../listing_photos/a.jpg`.
 * @return true only if a file was deleted.
 */
internal fun deletePhotoFile(uri: String): Boolean = runCatching {
  val parsed = URI(uri)
  if (parsed.scheme != "file") return@runCatching false
  val file = File(parsed.path).canonicalFile
  file.parentFile?.name == LISTING_PHOTOS_DIR && file.delete()
}
    .getOrDefault(false)

/**
 * A new, unique JPEG file in [photosDir], which is created if needed. The name holds the time of
 * the shot and a random id, so two shots in the same millisecond never share a file. Files older
 * than [STALE_PHOTO_AGE_MS] are deleted first.
 *
 * @param photosDir the folder the new file goes in; created if it does not exist.
 * @param nowMillis the time of the shot in milliseconds, used in the name and to find stale files.
 * @param id a random id that makes the name unique.
 * @return the new file, which does not exist yet.
 */
internal fun newPhotoFile(
    photosDir: File,
    nowMillis: Long = System.currentTimeMillis(),
    id: UUID = UUID.randomUUID(),
): File {
  photosDir.mkdirs()
  deleteStalePhotos(photosDir, nowMillis)
  return File(photosDir, "listing_${nowMillis}_$id.jpg")
}

/**
 * Deletes the files in [photosDir] last modified more than [STALE_PHOTO_AGE_MS] before [nowMillis].
 * Never throws.
 *
 * @param photosDir the folder to clean; a missing or empty folder is fine.
 * @param nowMillis the current time in milliseconds, to compare with each file's last change.
 */
internal fun deleteStalePhotos(photosDir: File, nowMillis: Long) {
  runCatching {
    photosDir
        .listFiles()
        ?.filter { nowMillis - it.lastModified() > STALE_PHOTO_AGE_MS }
        ?.forEach { deletePhotoFile(it.toURI().toString()) }
  }
}

/** True when the shutter may be used: a shot is not already being saved and a photo still fits. */
internal fun canTakePhoto(photoCount: Int, isCapturing: Boolean): Boolean =
    !isCapturing && photoCount < Listing.MAX_PHOTOS

/** True when the camera closes by itself, because no more photo fits. */
internal fun shouldCloseCamera(photoCount: Int): Boolean = photoCount >= Listing.MAX_PHOTOS
