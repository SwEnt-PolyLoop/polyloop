// Made with Claude.

package com.swent.polyloop.ui.createlisting

import android.util.Size
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
  fun photoResolutionSelectorAsksForAbout1080pAndFallsBackToTheClosestSize() {
    val strategy = photoResolutionSelector().resolutionStrategy

    assertEquals(Size(1920, 1080), strategy?.boundSize)
    assertEquals(
        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
        strategy?.fallbackRule,
    )
  }

  @Test
  fun photoSizeAndQualityConstantsAreTheAgreedValues() {
    assertEquals(1920, PHOTO_LONG_SIDE_PX)
    assertEquals(1080, PHOTO_SHORT_SIDE_PX)
    assertEquals(80, PHOTO_JPEG_QUALITY)
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
