// Made with Claude.

package com.swent.polyloop.ui.createlisting

import com.swent.polyloop.model.listing.Listing
import java.io.File
import java.util.UUID

/** Folder inside the app's files directory where the in-app camera saves listing photos. */
internal const val LISTING_PHOTOS_DIR = "listing_photos"

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

/**
 * True when the shutter may be used: a shot is not already being saved and a photo still fits
 * (fewer than [maxPhotos] taken).
 */
internal fun canTakePhoto(
    photoCount: Int,
    isCapturing: Boolean,
    maxPhotos: Int = Listing.MAX_PHOTOS,
): Boolean = !isCapturing && photoCount < maxPhotos

/** True when the camera closes by itself, because no more photo fits. */
internal fun shouldCloseCamera(photoCount: Int, maxPhotos: Int = Listing.MAX_PHOTOS): Boolean =
    photoCount >= maxPhotos
