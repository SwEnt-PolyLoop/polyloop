// Made with Claude.

package com.swent.polyloop.ui.createlisting

import android.util.Size
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionStrategy
import com.swent.polyloop.model.listing.Listing
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric because the photo size is an `android.util.Size`. */
@RunWith(RobolectricTestRunner::class)
class CameraPhotosTest {

  @get:Rule val tempFolder = TemporaryFolder()

  private fun photoIn(dirName: String, fileName: String = "photo.jpg"): File {
    val dir = tempFolder.newFolder(dirName)
    return File(dir, fileName).apply { writeText("jpeg") }
  }

  @Test
  fun deletePhotoFileDeletesAFileInTheListingPhotosFolder() {
    val photo = photoIn(LISTING_PHOTOS_DIR)

    assertTrue(deletePhotoFile(photo.toURI().toString()))

    assertFalse(photo.exists())
  }

  @Test
  fun deletePhotoFileKeepsAFileOutsideTheListingPhotosFolder() {
    val photo = photoIn("other_folder")

    assertFalse(deletePhotoFile(photo.toURI().toString()))

    assertTrue(photo.exists())
  }

  @Test
  fun deletePhotoFileKeepsAFileReachedThroughDotDotOutOfTheFolder() {
    photoIn(LISTING_PHOTOS_DIR)
    val outside = File(tempFolder.root, "outside.jpg").apply { writeText("jpeg") }
    val sneaky = "file://${tempFolder.root.absolutePath}/$LISTING_PHOTOS_DIR/../outside.jpg"

    assertFalse(deletePhotoFile(sneaky))

    assertTrue(outside.exists())
  }

  @Test
  fun deletePhotoFileIgnoresAMissingFile() {
    val missing = File(File(tempFolder.root, LISTING_PHOTOS_DIR), "missing.jpg")

    assertFalse(deletePhotoFile(missing.toURI().toString()))

    assertFalse(missing.exists())
  }

  @Test
  fun deletePhotoFileIgnoresANonFileUri() {
    val photo = photoIn(LISTING_PHOTOS_DIR)

    assertFalse(deletePhotoFile("content://media/$LISTING_PHOTOS_DIR/${photo.name}"))
    assertFalse(deletePhotoFile("https://example.com/$LISTING_PHOTOS_DIR/${photo.name}"))
    assertFalse(deletePhotoFile(photo.absolutePath))

    assertTrue(photo.exists())
  }

  @Test
  fun deletePhotoFileNeverThrowsOnAMalformedUri() {
    assertFalse(deletePhotoFile("not a uri"))
    assertFalse(deletePhotoFile(""))
    assertFalse(deletePhotoFile("file://"))
  }

  @Test
  fun photoResolutionSelectorAsks1440x1080In4by3AndFallsBackToTheClosestSize() {
    val selector = photoResolutionSelector()

    assertEquals(Size(1440, 1080), selector.resolutionStrategy?.boundSize)
    assertEquals(
        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
        selector.resolutionStrategy?.fallbackRule,
    )
    assertEquals(
        AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY,
        selector.aspectRatioStrategy,
    )
  }

  @Test
  fun newPhotoFileDeletesFilesOlderThanADay() {
    val dir = tempFolder.newFolder(LISTING_PHOTOS_DIR)
    val now = 10 * STALE_PHOTO_AGE_MS
    val old = File(dir, "old.jpg").apply { writeText("jpeg") }
    old.setLastModified(now - STALE_PHOTO_AGE_MS - 1)

    newPhotoFile(dir, nowMillis = now)

    assertFalse(old.exists())
  }

  @Test
  fun newPhotoFileKeepsRecentFiles() {
    val dir = tempFolder.newFolder(LISTING_PHOTOS_DIR)
    val now = 10 * STALE_PHOTO_AGE_MS
    val recent = File(dir, "recent.jpg").apply { writeText("jpeg") }
    recent.setLastModified(now - STALE_PHOTO_AGE_MS + 60_000)

    newPhotoFile(dir, nowMillis = now)

    assertTrue(recent.exists())
  }

  @Test
  fun newPhotoFileIsFineWithAnEmptyFolder() {
    val dir = tempFolder.newFolder(LISTING_PHOTOS_DIR)

    val file = newPhotoFile(dir)

    assertEquals(dir, file.parentFile)
  }

  @Test
  fun deleteStalePhotosIgnoresAMissingFolder() {
    deleteStalePhotos(File(tempFolder.root, "missing"), nowMillis = 10 * STALE_PHOTO_AGE_MS)
  }

  @Test
  fun newPhotoFileIsAJpegInThePhotosFolder() {
    val dir = File(tempFolder.root, LISTING_PHOTOS_DIR)
    val id = UUID.fromString("00000000-0000-0000-0000-000000000001")

    val file = newPhotoFile(dir, nowMillis = 1234L, id = id)

    assertEquals(dir, file.parentFile)
    assertEquals("listing_1234_$id.jpg", file.name)
  }

  @Test
  fun newPhotoFileCreatesTheFolder() {
    val dir = File(tempFolder.root, "nested/$LISTING_PHOTOS_DIR")

    newPhotoFile(dir)

    assertTrue(dir.isDirectory)
  }

  @Test
  fun newPhotoFileNamesAreUniqueEvenInTheSameMillisecond() {
    val dir = tempFolder.root

    val first = newPhotoFile(dir, nowMillis = 1L)
    val second = newPhotoFile(dir, nowMillis = 1L)

    assertNotEquals(first.name, second.name)
  }

  @Test
  fun canTakePhotoWhileIdleAndBelowMaxPhotos() {
    assertTrue(canTakePhoto(photoCount = 0, isCapturing = false))
    assertTrue(canTakePhoto(photoCount = Listing.MAX_PHOTOS - 1, isCapturing = false))
  }

  @Test
  fun cannotTakePhotoAtMaxPhotos() {
    assertFalse(canTakePhoto(photoCount = Listing.MAX_PHOTOS, isCapturing = false))
  }

  @Test
  fun cannotTakePhotoWhileAShotIsBeingSaved() {
    assertFalse(canTakePhoto(photoCount = 1, isCapturing = true))
  }

  @Test
  fun cameraClosesByItselfOnlyAtMaxPhotos() {
    assertFalse(shouldCloseCamera(Listing.MAX_PHOTOS - 1))
    assertTrue(shouldCloseCamera(Listing.MAX_PHOTOS))
  }
}
