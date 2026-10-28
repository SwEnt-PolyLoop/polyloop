// Made with Claude.

package com.swent.polyloop.rules

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.storage
import com.google.firebase.storage.storageMetadata
import com.swent.polyloop.utils.FirebaseEmulator
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks storage.rules against the Storage emulator, which reads the listings from the Firestore
 * emulator to find their owner. Needs `firebase emulators:start`.
 */
@RunWith(AndroidJUnit4::class)
class StorageRulesTest {

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val listingId = "listing-${UUID.randomUUID()}"
  private val bytes = byteArrayOf(1, 2, 3, 4)
  private val jpeg = storageMetadata { contentType = "image/jpeg" }

  private lateinit var ownerEmail: String
  private lateinit var ownerUid: String

  @Before
  fun setUp() {
    FirebaseEmulator.connect()
    ownerEmail = "storage-${UUID.randomUUID()}@epfl.ch"
    ownerUid = runBlocking { FirebaseEmulator.signUpVerified(ownerEmail, PASSWORD) }
  }

  @After
  fun tearDown() {
    Firebase.auth.signOut()
  }

  private fun photo(name: String = "0.jpg") =
      Firebase.storage.reference.child("listings/$listingId/$name")

  /**
   * The listing document, as the batch in ListingRepositoryFirestore writes it after the upload.
   */
  private fun publishListing() {
    FirebaseEmulator.createDocumentAsAdmin(
        "listings/$listingId",
        mapOf("ownerId" to ownerUid, "title" to "Camping tent"),
    )
  }

  private suspend fun signInAsOwner() {
    Firebase.auth.signInWithEmailAndPassword(ownerEmail, PASSWORD).await()
  }

  /** Runs [block] and fails unless the rules refuse it. */
  private suspend fun assertDenied(block: suspend () -> Unit) {
    try {
      block()
      fail("The rules should have refused this request")
    } catch (e: StorageException) {
      assertEquals(StorageException.ERROR_NOT_AUTHORIZED, e.errorCode)
    }
  }

  @Test
  fun photosAreUploadedBeforeTheListingExistsAndReadByAnyVerifiedUser() =
      runBlocking<Unit> {
        photo().putBytes(bytes, jpeg).await()
        publishListing()

        FirebaseEmulator.signUpVerified()
        assertArrayEquals(bytes, photo().getBytes(MAX_BYTES).await())
      }

  @Test
  fun onceTheListingExistsOnlyItsOwnerChangesItsPhotos() =
      runBlocking<Unit> {
        photo().putBytes(bytes, jpeg).await()
        publishListing()

        FirebaseEmulator.signUpVerified()
        assertDenied { photo().putBytes(bytes, jpeg).await() }
        assertDenied { photo("1.jpg").putBytes(bytes, jpeg).await() }
        assertDenied { photo().delete().await() }

        signInAsOwner()
        photo("1.jpg").putBytes(bytes, jpeg).await()
        photo().delete().await()
      }

  @Test
  fun onlyImagesAreAccepted() =
      runBlocking<Unit> {
        assertDenied {
          photo("notes.pdf")
              .putBytes(bytes, storageMetadata { contentType = "application/pdf" })
              .await()
        }
      }

  @Test
  fun photosOf100MbOrMoreAreRefused() =
      runBlocking<Unit> {
        // A sparse file: it takes no space on the device.
        val file = File(context.cacheDir, "big-${UUID.randomUUID()}.jpg")
        try {
          RandomAccessFile(file, "rw").use { it.setLength(100L * 1024 * 1024) }
          assertDenied { photo().putFile(Uri.fromFile(file), jpeg).await() }
        } finally {
          file.delete()
        }
      }

  @Test
  fun filesOutsideTheListingFoldersAreRefused() =
      runBlocking<Unit> {
        val ref = Firebase.storage.reference.child("listing_images/${UUID.randomUUID()}.jpg")

        assertDenied { ref.putBytes(bytes, jpeg).await() }
      }

  private companion object {
    const val PASSWORD = "password123"
    const val MAX_BYTES = 1024L
  }
}
