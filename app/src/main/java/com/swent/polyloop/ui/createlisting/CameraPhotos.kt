// Made with Claude.

package com.swent.polyloop.ui.createlisting

import android.util.Size
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import com.swent.polyloop.model.listing.Listing
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.UUID

/**
 * Folder inside the app's files directory where the in-app camera saves its photos, listing and
 * profile photos alike (the files are named `listing_*.jpg` either way). Keeping them in one folder
 * lets [deleteStalePhotos] remove a profile photo left behind, as it does for listings.
 */
internal const val LISTING_PHOTOS_DIR = "listing_photos"

/** Long side of the size listing photos are shot at, in pixels (4:3). */
internal const val PHOTO_LONG_SIDE_PX = 1440

/** Short side of the size listing photos are shot at, in pixels (4:3). */
internal const val PHOTO_SHORT_SIDE_PX = 1080

/** JPEG quality of listing photos, from 1 to 100. */
internal const val PHOTO_JPEG_QUALITY = 80

/**
 * Photo files older than this (24 hours) are leftovers from an app killed mid-form. They are
 * deleted once, when the camera opens, except the photos the open form still holds.
 */
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
 * than [STALE_PHOTO_AGE_MS] are deleted by [deleteStalePhotos], not here.
 *
 * @param photosDir the folder the new file goes in; created if it does not exist.
 * @param nowMillis the time of the shot in milliseconds, used in the name.
 * @param id a random id that makes the name unique.
 * @return the new file, which does not exist yet.
 * @throws IOException if [photosDir] does not exist and cannot be created.
 */
internal fun newPhotoFile(
    photosDir: File,
    nowMillis: Long = System.currentTimeMillis(),
    id: UUID = UUID.randomUUID(),
): File {
  // isDirectory again: mkdirs is false too when another call created the folder meanwhile.
  if (!photosDir.mkdirs() && !photosDir.isDirectory) throw IOException("Cannot create $photosDir")
  return File(photosDir, "listing_${nowMillis}_$id.jpg")
}

/**
 * Deletes the files in [photosDir] last modified more than [STALE_PHOTO_AGE_MS] before [nowMillis],
 * except the ones in [keep]. Each file goes through [deletePhotoFile], so only files directly
 * inside a folder named [LISTING_PHOTOS_DIR] are ever deleted; in any other folder nothing is.
 * Never throws. It lists and deletes files, so call it off the main thread.
 *
 * [keep] exists because a form can stay open longer than [STALE_PHOTO_AGE_MS] while the app is
 * alive (Create listing, or Edit profile before "Save"): its photos are then old but still needed,
 * and deleting them would make the upload fail.
 *
 * @param photosDir the folder to clean; a missing or empty folder is fine (it is not created).
 * @param nowMillis the current time in milliseconds, to compare with each file's last change.
 * @param keep the photos a form still holds, as `file:` URIs; they are never deleted. A URI that is
 *   not a `file:` URI keeps nothing.
 */
internal fun deleteStalePhotos(
    photosDir: File,
    nowMillis: Long,
    keep: Collection<String> = emptyList(),
) {
  runCatching {
    val kept = keep.mapNotNull(::canonicalPhotoFile).toSet()
    photosDir
        .listFiles()
        ?.filter { nowMillis - it.lastModified() > STALE_PHOTO_AGE_MS }
        ?.filter { it.canonicalFile !in kept }
        ?.forEach { deletePhotoFile(it.toURI().toString()) }
  }
}

/**
 * The file a `file:` [uri] points to, with its path resolved, or null for any other or malformed
 * URI.
 *
 * @param uri the photo's location as a URI string.
 * @return the resolved file, or null.
 */
private fun canonicalPhotoFile(uri: String): File? = runCatching {
  File(URI(uri)).canonicalFile
}
    .getOrNull()

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
