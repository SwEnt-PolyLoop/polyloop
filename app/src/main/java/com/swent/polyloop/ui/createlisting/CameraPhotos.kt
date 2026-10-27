// Made with Claude.

package com.swent.polyloop.ui.createlisting

import android.util.Size
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import com.swent.polyloop.model.listing.Listing
import java.io.File
import java.net.URI
import java.util.UUID

/** Folder inside the app's files directory where the in-app camera saves listing photos. */
internal const val LISTING_PHOTOS_DIR = "listing_photos"

/** Long side of the size listing photos are shot at, in pixels (about 1080p). */
internal const val PHOTO_LONG_SIDE_PX = 1920

/** Short side of the size listing photos are shot at, in pixels (about 1080p). */
internal const val PHOTO_SHORT_SIDE_PX = 1080

/** JPEG quality of listing photos, from 1 to 100. */
internal const val PHOTO_JPEG_QUALITY = 80

/**
 * The size the camera shoots listing photos at: [PHOTO_LONG_SIDE_PX] x [PHOTO_SHORT_SIDE_PX], or
 * the closest size the camera offers, below it first, so a photo never gets bigger than asked
 * unless nothing smaller exists.
 */
internal fun photoResolutionSelector(): ResolutionSelector =
    ResolutionSelector.Builder()
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
 */
internal fun deletePhotoFile(uri: String) {
  runCatching {
    val parsed = URI(uri)
    if (parsed.scheme != "file") return
    val file = File(parsed.path).canonicalFile
    if (file.parentFile?.name == LISTING_PHOTOS_DIR) file.delete()
  }
}

/**
 * A new, unique JPEG file in [photosDir], which is created if needed. The name holds the time of
 * the shot and a random id, so two shots in the same millisecond never share a file.
 */
internal fun newPhotoFile(
    photosDir: File,
    nowMillis: Long = System.currentTimeMillis(),
    id: UUID = UUID.randomUUID(),
): File {
  photosDir.mkdirs()
  return File(photosDir, "listing_${nowMillis}_$id.jpg")
}

/** True when the shutter may be used: a shot is not already being saved and a photo still fits. */
internal fun canTakePhoto(photoCount: Int, isCapturing: Boolean): Boolean =
    !isCapturing && photoCount < Listing.MAX_PHOTOS

/** True when the camera closes by itself, because no more photo fits. */
internal fun shouldCloseCamera(photoCount: Int): Boolean = photoCount >= Listing.MAX_PHOTOS
