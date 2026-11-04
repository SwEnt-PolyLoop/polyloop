// Made with Claude.

package com.swent.polyloop.model.listing

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import com.swent.polyloop.utils.FirebaseEmulator
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real [ListingRepositoryFirestore] against the Firestore and Storage emulators (not
 * mocks), so these tests see how Firebase really behaves. Needs `firebase emulators:start`.
 */
@RunWith(AndroidJUnit4::class)
class ListingRepositoryFirestoreEmulatorTest {

  // Created here (not in setUp): creating it does not contact Firebase yet, so connect() can still
  // switch it to the emulators.
  private val repository = ListingRepositoryFirestore(storageProvider = { Firebase.storage })

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  // A new owner for each test, so listings left by earlier runs never get in the way. The signed-in
  // user: the rules let only them create their listings.
  private lateinit var ownerId: String

  private val listing
    get() =
        Listing(
            id = "",
            ownerId = ownerId,
            title = "Camping tent, 2 people",
            description = "Light 2-person tent, used three times.",
            category = ListingCategory.SPORTS_OUTDOOR,
            photoUrls = emptyList(),
            pricePerDay = 15,
            itemValue = 180,
            availableFrom = LocalDate.of(2026, 10, 6),
            availableTo = LocalDate.of(2026, 10, 20),
            status = ListingStatus.PUBLISHED,
        )

  private val addresses =
      ListingAddresses(
          pickup = "Route Cantonale 1, Ecublens",
          dropOff = "Avenue Piccard 2, Lausanne",
      )

  private val createdIds = mutableListOf<String>()

  @Before
  fun setUp() {
    FirebaseEmulator.connect()
    // The rules only accept writes from a verified @epfl.ch user.
    ownerId = runBlocking { FirebaseEmulator.signUpVerified() }
  }

  @After
  fun tearDown() {
    runBlocking {
      createdIds.forEach { id ->
        runCatching {
          Firebase.storage.reference.child("listings/$id").listAll().await().items.forEach {
            it.delete().await()
          }
        }
      }
    }
    Firebase.auth.signOut()
  }

  /** [count] small files in the app's cache, as the camera would leave them. */
  private fun photos(count: Int): List<Uri> =
      List(count) {
        val file = File(context.cacheDir, "photo-${UUID.randomUUID()}.jpg")
        file.writeBytes(byteArrayOf(it.toByte(), 1, 2, 3))
        Uri.fromFile(file)
      }

  private suspend fun create(photos: List<Uri>): Result<String> =
      repository.createListing(listing, addresses, photos).onSuccess { createdIds += it }

  private suspend fun listingCountForOwner(): Int =
      Firebase.firestore
          .collection(ListingFields.COLLECTION)
          .whereEqualTo(ListingFields.OWNER_ID, ownerId)
          .get()
          .await()
          .size()

  @Test
  fun createdListingReadsBackWithItsPhotoUrls() {
    runBlocking {
      val id = create(photos(Listing.MIN_PHOTOS)).getOrThrow()

      val saved = repository.getListing(id).getOrThrow()!!

      assertEquals(listing.copy(id = id, photoUrls = saved.photoUrls), saved)
      assertEquals(Listing.MIN_PHOTOS, saved.photoUrls.size)
      saved.photoUrls.forEachIndexed { index, url ->
        // Download URLs encode the Storage path, "/" included.
        assertTrue(url, url.contains("listings%2F$id%2F$index.jpg"))
      }
    }
  }

  @Test
  fun addressesAreOnlyInThePrivateDocument() {
    runBlocking {
      val id = create(photos(Listing.MIN_PHOTOS)).getOrThrow()
      val listingRef = Firebase.firestore.collection(ListingFields.COLLECTION).document(id)

      val public = listingRef.get().await().data!!
      val private =
          listingRef
              .collection(ListingAddressFields.COLLECTION)
              .document(ListingAddressFields.DOCUMENT)
              .get()
              .await()
              .data

      assertFalse(public.containsKey(ListingAddressFields.PICKUP))
      assertFalse(public.containsKey(ListingAddressFields.DROP_OFF))
      assertFalse(public.values.contains(addresses.pickup))
      assertFalse(public.values.contains(addresses.dropOff))
      assertEquals(
          mapOf(
              ListingAddressFields.PICKUP to addresses.pickup,
              ListingAddressFields.DROP_OFF to addresses.dropOff,
          ),
          private,
      )
    }
  }

  @Test
  fun invalidListingFailsAndWritesNothing() {
    runBlocking {
      val result = create(photos(Listing.MIN_PHOTOS - 1))

      assertTrue(result.exceptionOrNull() is IllegalArgumentException)
      assertEquals(0, listingCountForOwner())
    }
  }

  @Test
  fun missingPhotoFileFailsAndWritesNothing() {
    runBlocking {
      val missing = Uri.fromFile(File(context.cacheDir, "missing-${UUID.randomUUID()}.jpg"))

      val result = create(photos(Listing.MIN_PHOTOS - 1) + missing)

      assertTrue(result.isFailure)
      assertEquals(0, listingCountForOwner())
    }
  }

  @Test
  fun listingsByOwnerAreTheOnesTheyCreated() {
    runBlocking {
      val first = create(photos(Listing.MIN_PHOTOS)).getOrThrow()
      val second = create(photos(Listing.MIN_PHOTOS)).getOrThrow()

      val owned = repository.getListingsByOwner(ownerId).getOrThrow()

      assertEquals(setOf(first, second), owned.map { it.id }.toSet())
    }
  }

  @Test
  fun ownerWithoutListingsGetsAnEmptyList() {
    runBlocking {
      val owned = repository.getListingsByOwner("test-${UUID.randomUUID()}").getOrThrow()

      assertTrue(owned.isEmpty())
    }
  }
}
