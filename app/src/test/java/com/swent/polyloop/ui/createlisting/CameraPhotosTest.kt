// Made with Claude.

package com.swent.polyloop.ui.createlisting

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

class CameraPhotosTest {

  @get:Rule val tempFolder = TemporaryFolder()

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

  @Test
  fun aSinglePhotoCameraTakesOnlyOnePhoto() {
    assertTrue(canTakePhoto(photoCount = 0, isCapturing = false, maxPhotos = 1))
    assertFalse(canTakePhoto(photoCount = 1, isCapturing = false, maxPhotos = 1))
    assertFalse(canTakePhoto(photoCount = 0, isCapturing = true, maxPhotos = 1))
  }

  @Test
  fun aSinglePhotoCameraClosesByItselfAfterOnePhoto() {
    assertFalse(shouldCloseCamera(0, maxPhotos = 1))
    assertTrue(shouldCloseCamera(1, maxPhotos = 1))
  }
}
