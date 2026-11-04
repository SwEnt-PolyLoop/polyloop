// Made with Claude.

package com.swent.polyloop.rules

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageReference
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

  // A JPEG tagged with the uid of the user signed in when it is uploaded, as
  // ListingRepositoryFirestore tags listing photos. The profile photo rules ignore the tag.
  private val jpeg
    get() = photoMetadata(uploader = Firebase.auth.currentUser?.uid)

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

  /** The metadata of a file of [type], tagged with the uid of its [uploader] unless it is null. */
  private fun photoMetadata(uploader: String?, type: String = "image/jpeg") = storageMetadata {
    contentType = type
    uploader?.let { setCustomMetadata(UPLOADER, it) }
  }

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

  /** Uploads a sparse file of [size] bytes (it takes no space on the device) to [ref]. */
  private suspend fun uploadSparseFile(ref: StorageReference, size: Long) {
    val file = File(context.cacheDir, "big-${UUID.randomUUID()}.jpg")
    try {
      RandomAccessFile(file, "rw").use { it.setLength(size) }
      ref.putFile(Uri.fromFile(file), jpeg).await()
    } finally {
      file.delete()
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
  fun photosMustBeTaggedWithTheUidOfTheirUploader() =
      runBlocking<Unit> {
        assertDenied { photo().putBytes(bytes, photoMetadata(uploader = null)).await() }
        assertDenied { photo().putBytes(bytes, photoMetadata(uploader = OTHER_UID)).await() }
      }

  @Test
  fun onceTheListingIsDeletedOnlyTheUploaderChangesItsPhotos() =
      runBlocking<Unit> {
        photo().putBytes(bytes, jpeg).await()
        publishListing()
        FirebaseEmulator.deleteDocumentAsAdmin("listings/$listingId")

        FirebaseEmulator.signUpVerified()
        assertDenied { photo().putBytes(bytes, jpeg).await() }
        assertDenied { photo().updateMetadata(jpeg).await() }
        assertDenied { photo().delete().await() }

        signInAsOwner()
        photo().putBytes(bytes, jpeg).await()
        photo().delete().await()
      }

  @Test
  fun theUploaderCannotTagTheirPhotoWithSomeoneElse() =
      runBlocking<Unit> {
        photo().putBytes(bytes, jpeg).await()

        assertDenied { photo().putBytes(bytes, photoMetadata(uploader = OTHER_UID)).await() }
        assertDenied { photo().updateMetadata(photoMetadata(uploader = OTHER_UID)).await() }
        photo().updateMetadata(jpeg).await()
      }

  @Test
  fun onlyPhotosNamed0To9JpgAreAccepted() =
      runBlocking<Unit> {
        photo("9.jpg").putBytes(bytes, jpeg).await()

        assertDenied { photo("10.jpg").putBytes(bytes, jpeg).await() }
        assertDenied { photo("tent.jpg").putBytes(bytes, jpeg).await() }
        assertDenied { photo("0.png").putBytes(bytes, jpeg).await() }
      }

  @Test
  fun onlyImagesAreAccepted() =
      runBlocking<Unit> {
        assertDenied {
          photo().putBytes(bytes, photoMetadata(ownerUid, type = "application/pdf")).await()
        }
      }

  @Test
  fun photosJustUnder10MbAreAccepted() =
      runBlocking<Unit> { uploadSparseFile(photo(), 10L * 1024 * 1024 - 1) }

  @Test
  fun photosOf10MbOrMoreAreRefused() =
      runBlocking<Unit> { assertDenied { uploadSparseFile(photo(), 10L * 1024 * 1024) } }

  @Test
  fun filesOutsideTheListingFoldersAreRefused() =
      runBlocking<Unit> {
        val ref = Firebase.storage.reference.child("listing_images/${UUID.randomUUID()}.jpg")

        assertDenied { ref.putBytes(bytes, jpeg).await() }
      }

  // ---------- profile photos ----------

  private fun profilePhoto(uid: String = ownerUid, name: String = "profile.jpg") =
      Firebase.storage.reference.child("users/$uid/$name")

  @Test
  fun aUserUploadsTheirProfilePhotoAndAnyVerifiedUserReadsIt() =
      runBlocking<Unit> {
        profilePhoto().putBytes(bytes, jpeg).await()

        FirebaseEmulator.signUpVerified()
        assertArrayEquals(bytes, profilePhoto().getBytes(MAX_BYTES).await())
      }

  @Test
  fun aUserReplacesAndDeletesTheirOwnProfilePhoto() =
      runBlocking<Unit> {
        profilePhoto().putBytes(bytes, jpeg).await()
        val newBytes = byteArrayOf(5, 6, 7)

        profilePhoto().putBytes(newBytes, jpeg).await()
        assertArrayEquals(newBytes, profilePhoto().getBytes(MAX_BYTES).await())

        profilePhoto().delete().await()
      }

  @Test
  fun nobodyElseChangesOrDeletesAProfilePhoto() =
      runBlocking<Unit> {
        profilePhoto().putBytes(bytes, jpeg).await()

        FirebaseEmulator.signUpVerified()
        assertDenied { profilePhoto().putBytes(byteArrayOf(9), jpeg).await() }
        assertDenied { profilePhoto().delete().await() }
      }

  @Test
  fun anUnverifiedUserCannotUploadTheirProfilePhoto() =
      runBlocking<Unit> {
        val email = "unverified-${UUID.randomUUID()}@epfl.ch"
        val uid = Firebase.auth.createUserWithEmailAndPassword(email, PASSWORD).await().user!!.uid

        assertDenied { profilePhoto(uid).putBytes(bytes, jpeg).await() }
      }

  @Test
  fun onlyTheFileProfileJpgIsAcceptedInAUserFolder() =
      runBlocking<Unit> {
        assertDenied { profilePhoto(name = "other.jpg").putBytes(bytes, jpeg).await() }
      }

  @Test
  fun profilePhotosMustBeImages() =
      runBlocking<Unit> {
        assertDenied {
          profilePhoto()
              .putBytes(bytes, storageMetadata { contentType = "application/pdf" })
              .await()
        }
      }

  @Test
  fun profilePhotosJustUnder5MbAreAccepted() =
      runBlocking<Unit> { uploadSparseFile(profilePhoto(), 5L * 1024 * 1024 - 1) }

  @Test
  fun profilePhotosOf5MbOrMoreAreRefused() =
      runBlocking<Unit> { assertDenied { uploadSparseFile(profilePhoto(), 5L * 1024 * 1024) } }

  private companion object {
    const val PASSWORD = "password123"
    const val MAX_BYTES = 1024L

    // The custom metadata holding the uid of the user who uploaded a listing photo.
    const val UPLOADER = "uploader"
    const val OTHER_UID = "someone-else"
  }
}
