// Made with Claude.

package com.swent.polyloop.rules

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.model.listing.listingToDocument
import com.swent.polyloop.utils.FirebaseEmulator
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks firestore.rules against the Firestore emulator, which enforces them (see firebase.json).
 * Each test signs up new accounts, so data left by earlier runs never gets in the way. Data that
 * belongs to someone else, or that only Cloud Functions may write, is seeded as an admin. Needs
 * `firebase emulators:start`.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreRulesTest {

  private val db
    get() = Firebase.firestore

  private val start = LocalDate.of(2026, 11, 2)
  private val end = LocalDate.of(2026, 11, 4)

  /** An account the test can sign in as again. */
  private data class Account(val uid: String, val email: String)

  @Before
  fun setUp() {
    FirebaseEmulator.connect()
  }

  @After
  fun tearDown() {
    Firebase.auth.signOut()
  }

  /** Signs up a new verified EPFL account and leaves it signed in. */
  private suspend fun newAccount(): Account {
    val email = "rules-${UUID.randomUUID()}@epfl.ch"
    return Account(FirebaseEmulator.signUpVerified(email, PASSWORD), email)
  }

  private suspend fun signInAs(account: Account) {
    Firebase.auth.signInWithEmailAndPassword(account.email, PASSWORD).await()
  }

  /** A listing as the app creates it: its areas stay empty until geocodeListingAddresses runs. */
  private fun listing(ownerId: String, id: String = "listing-${UUID.randomUUID()}") =
      Listing(
          id = id,
          ownerId = ownerId,
          title = "Camping tent",
          description = "Light 2-person tent.",
          category = ListingCategory.SPORTS_OUTDOOR,
          photoUrls = List(Listing.MIN_PHOTOS) { "https://example.com/$it.jpg" },
          pricePerDay = 5,
          itemValue = 120,
          availableFrom = LocalDate.of(2026, 11, 1),
          availableTo = LocalDate.of(2026, 11, 30),
          status = ListingStatus.PUBLISHED,
      )

  /** A listing of [ownerId], written as an admin. */
  private fun seedListing(ownerId: String) =
      listing(ownerId).also {
        FirebaseEmulator.createDocumentAsAdmin("listings/${it.id}", listingToDocument(it))
      }

  private fun listingRef(id: String) = db.collection("listings").document(id)

  private fun addressesRef(listingId: String) =
      listingRef(listingId).collection("private").document("addresses")

  /** A request from [borrower] for [listing], as the requestRental Cloud Function writes it. */
  private fun request(listing: Listing, borrower: Account): Map<String, Any> =
      mapOf(
          "listingId" to listing.id,
          "listingTitle" to listing.title,
          "listingPhotoUrl" to listing.photoUrls.firstOrNull().orEmpty(),
          "ownerId" to listing.ownerId,
          "ownerName" to "Ana",
          "borrowerId" to borrower.uid,
          "borrowerName" to "Ben",
          "participants" to listOf(listing.ownerId, borrower.uid),
          "startDate" to start.toString(),
          "endDate" to end.toString(),
          "status" to "REQUESTED",
          "pricePerDay" to listing.pricePerDay,
          "totalPrice" to listing.pricePerDay * 3,
          "pickupArea" to listing.pickupArea,
          "dropOffArea" to listing.dropOffArea,
          "pickupAddress" to "",
          "returnAddress" to "",
          "createdAt" to FieldValue.serverTimestamp(),
          "lastMessage" to "",
          "lastMessageAt" to FieldValue.serverTimestamp(),
      )

  /** A rental between [owner] and [borrower], written as an admin. Returns its reference. */
  private fun seedRental(
      owner: String,
      borrower: String,
      status: String = "REQUESTED",
  ): DocumentReference {
    val id = "rental-${UUID.randomUUID()}"
    FirebaseEmulator.createDocumentAsAdmin(
        "rentals/$id",
        mapOf(
            "listingId" to "listing-x",
            "ownerId" to owner,
            "borrowerId" to borrower,
            "participants" to listOf(owner, borrower),
            "status" to status,
            "startDate" to start.toString(),
            "endDate" to end.toString(),
            "lastMessage" to "",
            "lastMessageAt" to Timestamp(1_000, 0),
        ),
    )
    return db.collection("rentals").document(id)
  }

  private fun message(senderId: String, type: String = "USER") =
      mapOf(
          "senderId" to senderId,
          "text" to "Hi",
          "sentAt" to FieldValue.serverTimestamp(),
          "type" to type,
      )

  /** Runs [block] and fails unless the rules refuse it. */
  private suspend fun assertDenied(block: suspend () -> Unit) {
    try {
      block()
      fail("The rules should have refused this request")
    } catch (e: FirebaseFirestoreException) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
    }
  }

  // ---------- accounts ----------

  @Test
  fun unverifiedUserCannotReadAnything() =
      runBlocking<Unit> {
        val owner = newAccount()
        val listing = seedListing(owner.uid)
        Firebase.auth
            .createUserWithEmailAndPassword("rules-${UUID.randomUUID()}@epfl.ch", PASSWORD)
            .await()

        assertDenied { listingRef(listing.id).get(Source.SERVER).await() }
        assertDenied { db.collection("users").document(owner.uid).get(Source.SERVER).await() }
      }

  @Test
  fun verifiedNonEpflUserCannotReadListings() =
      runBlocking<Unit> {
        val owner = newAccount()
        val listing = seedListing(owner.uid)
        FirebaseEmulator.signUpVerified("rules-${UUID.randomUUID()}@gmail.com", PASSWORD)

        assertDenied { listingRef(listing.id).get(Source.SERVER).await() }
      }

  // ---------- users ----------

  @Test
  fun userCreatesOnlyTheirOwnProfileWithTheirEmail() =
      runBlocking<Unit> {
        val other = newAccount()
        val me = newAccount()
        val users = db.collection("users")
        val profile = mapOf("name" to "Ben", "email" to me.email, "photoUrl" to "")

        assertDenied { users.document(other.uid).set(profile).await() }
        assertDenied { users.document(me.uid).set(profile + ("email" to other.email)).await() }
        assertDenied { users.document(me.uid).set(profile + ("averageRating" to 5.0)).await() }
        users.document(me.uid).set(profile).await()

        // Others may read it.
        signInAs(other)
        assertEquals("Ben", users.document(me.uid).get(Source.SERVER).await().getString("name"))
      }

  @Test
  fun userUpdatesOnlyTheirNameAndPhoto() =
      runBlocking<Unit> {
        val me = newAccount()
        FirebaseEmulator.createDocumentAsAdmin(
            "users/${me.uid}",
            mapOf("name" to "Ben", "email" to me.email, "averageRating" to 4.5, "reviewCount" to 2),
        )
        val profile = db.collection("users").document(me.uid)

        profile
            .update(mapOf("name" to "Benji", "photoUrl" to "https://example.com/ben.jpg"))
            .await()
        assertDenied { profile.update("averageRating", 5.0).await() }
        assertDenied { profile.update("reviewCount", 3).await() }
        assertDenied { profile.update("email", "someone@epfl.ch").await() }

        newAccount() // someone else
        assertDenied { profile.update("name", "Hacked").await() }
      }

  // ---------- listings ----------

  @Test
  fun ownerCreatesAListingWithItsAddressesInOneBatch() =
      runBlocking<Unit> {
        val owner = newAccount()
        val listing = listing(owner.uid)

        db.batch()
            .set(listingRef(listing.id), listingToDocument(listing))
            .set(addressesRef(listing.id), mapOf("pickup" to "Route 1", "dropOff" to "Avenue 2"))
            .commit()
            .await()

        assertEquals(
            "Route 1",
            addressesRef(listing.id).get(Source.SERVER).await().getString("pickup"),
        )
      }

  @Test
  fun deletedListingWhoseAddressesAreLeftCannotBeCreatedAgain() =
      runBlocking<Unit> {
        val owner = newAccount()
        val listing = listing(owner.uid)
        // The listing was deleted without its addresses: only they are left.
        FirebaseEmulator.createDocumentAsAdmin(
            "listings/${listing.id}/private/addresses",
            mapOf("pickup" to "Route 1", "dropOff" to "Avenue 2"),
        )
        val other = newAccount()

        val claimed = listing.copy(ownerId = other.uid)
        assertDenied { listingRef(listing.id).set(listingToDocument(claimed)).await() }
        assertDenied { addressesRef(listing.id).get(Source.SERVER).await() }
      }

  @Test
  fun listingCannotBeCreatedForSomeoneElse() =
      runBlocking<Unit> {
        val other = newAccount()
        newAccount()
        val listing = listing(other.uid)

        assertDenied { listingRef(listing.id).set(listingToDocument(listing)).await() }
      }

  @Test
  fun listingThatTheAppCouldNotPublishIsRefused() =
      runBlocking<Unit> {
        val owner = newAccount()
        val valid = listing(owner.uid)
        val invalid =
            listOf(
                valid.copy(title = " "),
                valid.copy(description = ""),
                valid.copy(photoUrls = valid.photoUrls.take(Listing.MIN_PHOTOS - 1)),
                valid.copy(photoUrls = List(Listing.MAX_PHOTOS + 1) { "https://example.com/$it" }),
                valid.copy(pricePerDay = 0),
                valid.copy(itemValue = Listing.MAX_AMOUNT + 1),
                valid.copy(availableTo = valid.availableFrom),
            )

        invalid.forEach { listing ->
          assertDenied { listingRef(listing.id).set(listingToDocument(listing)).await() }
        }
        val document = listingToDocument(valid)
        assertDenied { listingRef(valid.id).set(document + ("category" to "WEAPONS")).await() }
        assertDenied { listingRef(valid.id).set(document + ("status" to "SOLD")).await() }
        assertDenied { listingRef(valid.id).set(document + ("unknownField" to 1)).await() }
        // Photo URLs that are not strings, first and last of 10.
        listOf(listOf<Any>(1, "b", "c"), List<Any>(9) { "url$it" } + 10).forEach { photoUrls ->
          assertDenied { listingRef(valid.id).set(document + ("photoUrls" to photoUrls)).await() }
        }
        assertDenied {
          listingRef(valid.id)
              .set(document + ("bookedRanges" to listOf(mapOf("start" to "a", "end" to "b"))))
              .await()
        }
        // The most photos, all strings, is accepted.
        listingRef(valid.id)
            .set(document + ("photoUrls" to List(Listing.MAX_PHOTOS) { "url$it" }))
            .await()
      }

  @Test
  fun ownerEditsTheirListingButNotItsOwnerOrBookings() =
      runBlocking<Unit> {
        val owner = newAccount()
        val listing = seedListing(owner.uid)
        val ref = listingRef(listing.id)

        ref.update("title", "Bigger tent").await()
        assertDenied { ref.update("bookedRanges", emptyList<Any>()).await() }
        assertDenied { ref.update("ownerId", "someone-else").await() }
        assertDenied { ref.update("pricePerDay", -1).await() }
      }

  @Test
  fun onlyCloudFunctionsWriteTheAreasAndPoints() =
      runBlocking<Unit> {
        val owner = newAccount()
        val listing = listing(owner.uid)
        val document = listingToDocument(listing)
        val ecublens = GeoPoint(46.52, 6.57)

        // A new listing has empty areas and no points.
        listOf(
                "pickupArea" to "Ecublens",
                "dropOffArea" to "Renens",
                "pickupPoint" to ecublens,
                "dropOffPoint" to ecublens,
            )
            .forEach { field ->
              assertDenied {
                listingRef("listing-${UUID.randomUUID()}").set(document + field).await()
              }
            }

        // Once geocoded, the owner still edits the listing, but not its areas or points.
        FirebaseEmulator.createDocumentAsAdmin(
            "listings/${listing.id}",
            document +
                mapOf(
                    "pickupArea" to "Ecublens",
                    "pickupPoint" to ecublens,
                    "dropOffArea" to "Renens",
                    "dropOffPoint" to GeoPoint(46.54, 6.59),
                ),
        )
        val ref = listingRef(listing.id)
        ref.update("title", "Bigger tent").await()
        assertDenied { ref.update("pickupArea", "Lausanne").await() }
        assertDenied { ref.update("dropOffPoint", GeoPoint(46.2, 6.1)).await() }
        assertDenied { ref.update("pickupPoint", FieldValue.delete()).await() }
      }

  @Test
  fun onlyTheOwnerEditsOrDeletesAListingAndReadsItsAddresses() =
      runBlocking<Unit> {
        val owner = newAccount()
        val listing = seedListing(owner.uid)
        FirebaseEmulator.createDocumentAsAdmin(
            "listings/${listing.id}/private/addresses",
            mapOf("pickup" to "Route 1", "dropOff" to "Avenue 2"),
        )
        newAccount()

        // Anyone verified reads the public listing.
        assertEquals("Camping tent", listingRef(listing.id).get(Source.SERVER).await().get("title"))
        assertDenied { addressesRef(listing.id).get(Source.SERVER).await() }
        assertDenied { addressesRef(listing.id).update("pickup", "Elsewhere").await() }
        assertDenied { listingRef(listing.id).update("title", "Mine now").await() }
        assertDenied { listingRef(listing.id).delete().await() }

        signInAs(owner)
        addressesRef(listing.id).delete().await()
        listingRef(listing.id).delete().await()
      }

  // ---------- rentals ----------

  @Test
  fun noClientCreatesARentalOnlyRequestRentalDoes() =
      runBlocking<Unit> {
        val owner = newAccount()
        val listing = seedListing(owner.uid)
        val borrower = newAccount()
        val rentals = db.collection("rentals")

        // Even a request matching its listing exactly, as the Cloud Function writes it.
        assertDenied { rentals.document().set(request(listing, borrower)).await() }
        signInAs(owner)
        assertDenied { rentals.document().set(request(listing, borrower)).await() }
      }

  @Test
  fun bothParticipantsReadARequestButAStrangerCannot() =
      runBlocking<Unit> {
        val owner = newAccount()
        val borrower = newAccount()
        val rental = seedRental(owner.uid, borrower.uid)

        assertEquals("REQUESTED", rental.get(Source.SERVER).await().getString("status"))
        signInAs(owner)
        assertEquals(borrower.uid, rental.get(Source.SERVER).await().getString("borrowerId"))
        newAccount() // a stranger
        assertDenied { rental.get(Source.SERVER).await() }
      }

  @Test
  fun participantsUpdateOnlyTheChatPreview() =
      runBlocking<Unit> {
        val owner = newAccount()
        val borrower = newAccount()
        val rental = seedRental(owner.uid, borrower.uid)

        rental
            .update(mapOf("lastMessage" to "Hi", "lastMessageAt" to FieldValue.serverTimestamp()))
            .await()
        assertDenied { rental.update("status", "ACCEPTED").await() }
        assertDenied { rental.update("lastMessageAt", Timestamp(5_000, 0)).await() }
        assertDenied { rental.delete().await() }
        // An empty preview would read as "no message yet" and hide the other person's red dot.
        assertDenied {
          rental
              .update(mapOf("lastMessage" to "", "lastMessageAt" to FieldValue.serverTimestamp()))
              .await()
        }
        // An update that changes nothing is refused too.
        assertDenied { rental.update("lastMessage", "Hi").await() }

        newAccount() // a stranger
        assertDenied {
          rental
              .update(
                  mapOf("lastMessage" to "Spam", "lastMessageAt" to FieldValue.serverTimestamp())
              )
              .await()
        }
      }

  @Test
  fun participantsSaveOnlyTheirOwnReadTimeAsTheServerTime() =
      runBlocking<Unit> {
        val owner = newAccount()
        val borrower = newAccount()
        val rental = seedRental(owner.uid, borrower.uid) // no lastReadAt yet

        rental.update("lastReadAt.${borrower.uid}", FieldValue.serverTimestamp()).await()
        rental.update("lastReadAt.${borrower.uid}", FieldValue.serverTimestamp()).await() // again
        assertDenied {
          rental.update("lastReadAt.${owner.uid}", FieldValue.serverTimestamp()).await()
        }
        assertDenied { rental.update("lastReadAt.${borrower.uid}", Timestamp(5_000, 0)).await() }
        assertDenied { rental.update("lastReadAt.${borrower.uid}", FieldValue.delete()).await() }
        assertDenied { rental.update("lastReadAt", "yesterday").await() }
        assertDenied {
          rental
              .update(
                  mapOf(
                      "lastReadAt.${borrower.uid}" to FieldValue.serverTimestamp(),
                      "status" to "ACCEPTED",
                  )
              )
              .await()
        }

        // Sending a message also saves the sender's read time, in the same update.
        signInAs(owner)
        rental
            .update(
                mapOf(
                    "lastMessage" to "Hi",
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "lastReadAt.${owner.uid}" to FieldValue.serverTimestamp(),
                )
            )
            .await()

        val stranger = newAccount()
        assertDenied {
          rental.update("lastReadAt.${stranger.uid}", FieldValue.serverTimestamp()).await()
        }
      }

  // ---------- messages ----------

  @Test
  fun onlyParticipantsSendAndReadMessagesAsThemselves() =
      runBlocking<Unit> {
        val owner = newAccount()
        val borrower = newAccount()
        val rental = seedRental(owner.uid, borrower.uid)
        val messages = rental.collection("messages")

        messages.document().set(message(borrower.uid)).await()
        assertDenied { messages.document().set(message(owner.uid)).await() } // not the sender
        assertDenied { messages.document().set(message(borrower.uid, type = "SYSTEM")).await() }
        assertDenied { messages.document().set(message(borrower.uid) + ("text" to "")).await() }

        signInAs(owner)
        assertEquals(1, messages.get(Source.SERVER).await().size())

        val stranger = newAccount()
        assertDenied { messages.get(Source.SERVER).await() }
        assertDenied { messages.document().set(message(stranger.uid)).await() }
      }

  @Test
  fun onlyCloudFunctionsWriteTheOfferTheDepositAndTheAddresses() =
      runBlocking<Unit> {
        val owner = newAccount()
        val borrower = newAccount()
        val rental = seedRental(owner.uid, borrower.uid)
        val offer = mapOf("amount" to 1, "authorId" to borrower.uid, "isCounter" to true)

        for (account in listOf(borrower, owner)) {
          signInAs(account)
          assertDenied { rental.update("offer", offer).await() }
          assertDenied { rental.update("deposit", 1).await() }
          assertDenied { rental.update("pickupAddress", "Route Cantonale 1").await() }
          assertDenied { rental.update("returnAddress", "Route Cantonale 1").await() }
        }
      }

  @Test
  fun noClientReadsOrWritesTheAddressesOfAnOffer() =
      runBlocking<Unit> {
        val owner = newAccount()
        val borrower = newAccount()
        val rental = seedRental(owner.uid, borrower.uid)
        val addresses = rental.collection("private").document("addresses")
        FirebaseEmulator.createDocumentAsAdmin(addresses.path, mapOf("pickup" to "Secret 1"))
        val stolen = mapOf("pickup" to "Elsewhere 2")

        for (account in listOf(borrower, owner)) {
          signInAs(account)
          assertDenied { addresses.get(Source.SERVER).await() }
          assertDenied { addresses.set(stolen).await() }
          assertDenied { addresses.delete().await() }
        }
      }

  // ---------- handover photos ----------

  private fun photo(takenBy: String, phase: String = "PICKUP") =
      mapOf(
          "takenBy" to takenBy,
          "phase" to phase,
          "takenAt" to FieldValue.serverTimestamp(),
          "url" to "https://example.com/p.jpg",
      )

  @Test
  fun participantsAddOnlyTheirOwnPickupPhotosAndNeverChangeThem() =
      runBlocking<Unit> {
        val owner = newAccount()
        val borrower = newAccount()
        val photos = seedRental(owner.uid, borrower.uid, status = "ACCEPTED").collection("photos")

        val mine = photos.document()
        mine.set(photo(borrower.uid)).await()
        assertDenied { photos.document().set(photo(owner.uid)).await() } // someone else's
        assertDenied {
          photos.document().set(photo(borrower.uid) + ("takenAt" to Timestamp(5_000, 0))).await()
        }
        assertDenied { photos.document().set(photo(borrower.uid) + ("url" to 5)).await() }
        assertDenied { photos.document().set(photo(borrower.uid) + ("extra" to 1)).await() }
        assertDenied { mine.update("url", "https://example.com/other.jpg").await() }
        assertDenied { mine.delete().await() }

        signInAs(owner)
        photos.document().set(photo(owner.uid)).await()
        assertEquals(2, photos.get(Source.SERVER).await().size())

        val stranger = newAccount()
        assertDenied { photos.get(Source.SERVER).await() }
        assertDenied { photos.document().set(photo(stranger.uid)).await() }
      }

  @Test
  fun pickupPhotosAreAddedOnlyWhileTheRentalIsAccepted() =
      runBlocking<Unit> {
        val owner = newAccount()
        val borrower = newAccount()
        val accepted = seedRental(owner.uid, borrower.uid, status = "ACCEPTED").collection("photos")
        val active = seedRental(owner.uid, borrower.uid, status = "ACTIVE").collection("photos")
        val requested = seedRental(owner.uid, borrower.uid).collection("photos")

        assertDenied { accepted.document().set(photo(borrower.uid, phase = "RETURN")).await() }
        assertDenied { active.document().set(photo(borrower.uid)).await() } // pickup is over
        assertDenied { requested.document().set(photo(borrower.uid)).await() }
      }

  // ---------- wallets ----------

  /**
   * A wallet and one transaction of [uid], written as an admin (only Cloud Functions write them).
   */
  private fun seedWallet(uid: String): Pair<DocumentReference, DocumentReference> {
    FirebaseEmulator.createDocumentAsAdmin("wallets/$uid", mapOf("available" to 50))
    FirebaseEmulator.createDocumentAsAdmin(
        "wallets/$uid/transactions/t1",
        mapOf(
            "type" to "RECHARGE",
            "amount" to 50,
            "itemTitle" to "",
            "date" to Timestamp(1_000, 0),
        ),
    )
    val wallet = db.collection("wallets").document(uid)
    return wallet to wallet.collection("transactions").document("t1")
  }

  @Test
  fun userReadsTheirOwnWalletAndTransactions() =
      runBlocking<Unit> {
        val me = newAccount()
        val (wallet, transaction) = seedWallet(me.uid)

        assertEquals(50L, wallet.get(Source.SERVER).await().getLong("available"))
        assertEquals(50L, transaction.get(Source.SERVER).await().getLong("amount"))
        assertEquals(1, wallet.collection("transactions").get(Source.SERVER).await().size())
      }

  @Test
  fun userCannotReadSomeoneElsesWalletOrTransactions() =
      runBlocking<Unit> {
        val owner = newAccount()
        val (wallet, transaction) = seedWallet(owner.uid)
        newAccount() // someone else

        assertDenied { wallet.get(Source.SERVER).await() }
        assertDenied { transaction.get(Source.SERVER).await() }
        assertDenied { wallet.collection("transactions").get(Source.SERVER).await() }
      }

  @Test
  fun unverifiedNonEpflAndSignedOutUsersCannotReadAWallet() =
      runBlocking<Unit> {
        val owner = newAccount()
        val (wallet, transaction) = seedWallet(owner.uid)

        // Accounts that must be refused: an unverified EPFL account, a verified non-EPFL one, and
        // nobody.
        Firebase.auth
            .createUserWithEmailAndPassword("rules-${UUID.randomUUID()}@epfl.ch", PASSWORD)
            .await()
        assertDenied { wallet.get(Source.SERVER).await() }
        assertDenied { transaction.get(Source.SERVER).await() }

        FirebaseEmulator.signUpVerified("rules-${UUID.randomUUID()}@gmail.com", PASSWORD)
        assertDenied { wallet.get(Source.SERVER).await() }
        assertDenied { transaction.get(Source.SERVER).await() }

        Firebase.auth.signOut()
        assertDenied { wallet.get(Source.SERVER).await() }
        assertDenied { transaction.get(Source.SERVER).await() }
      }

  @Test
  fun ownEmailAccountThatIsNotVerifiedCannotReadItsOwnWallet() =
      runBlocking<Unit> {
        val email = "rules-${UUID.randomUUID()}@epfl.ch"
        val uid = Firebase.auth.createUserWithEmailAndPassword(email, PASSWORD).await().user!!.uid
        val (wallet, _) = seedWallet(uid)

        assertDenied { wallet.get(Source.SERVER).await() }
      }

  @Test
  fun noClientWritesAWalletOrATransactionTheOwnerIncluded() =
      runBlocking<Unit> {
        val me = newAccount()
        val (wallet, transaction) = seedWallet(me.uid)

        assertDenied { wallet.set(mapOf("available" to 1_000_000)).await() }
        assertDenied { wallet.update("available", 1_000_000).await() }
        assertDenied { wallet.delete().await() }
        assertDenied { transaction.set(mapOf("amount" to 1)).await() }
        assertDenied { transaction.update("amount", 1).await() }
        assertDenied { transaction.delete().await() }
        assertDenied {
          wallet.collection("transactions").document("t2").set(mapOf("amount" to 1)).await()
        }

        // Nothing changed.
        assertEquals(50L, wallet.get(Source.SERVER).await().getLong("available"))
        assertEquals(50L, transaction.get(Source.SERVER).await().getLong("amount"))
      }

  @Test
  fun newUserWithoutAWalletCannotCreateTheirOwnWalletOrTransaction() =
      runBlocking<Unit> {
        val me = newAccount()
        val wallet = db.collection("wallets").document(me.uid)

        assertDenied { wallet.set(mapOf("available" to 1_000_000)).await() }
        assertDenied {
          wallet.collection("transactions").document("t1").set(mapOf("amount" to 1)).await()
        }

        // Nothing was created.
        assertFalse(wallet.get(Source.SERVER).await().exists())
      }

  // ---------- disputes ----------

  /** The dispute of [rental], written as an admin as the Cloud Functions write it. */
  private fun seedDispute(rental: DocumentReference): DocumentReference {
    FirebaseEmulator.createDocumentAsAdmin(
        "disputes/${rental.id}",
        mapOf(
            "stage" to "SETTLE",
            "proposals" to emptyList<Any>(),
            "escalatesAt" to Timestamp(2_000_000_000, 0),
        ),
    )
    return db.collection("disputes").document(rental.id)
  }

  @Test
  fun onlyTheTwoParticipantsReadTheDispute() =
      runBlocking<Unit> {
        val owner = newAccount()
        val borrower = newAccount()
        val dispute = seedDispute(seedRental(owner.uid, borrower.uid))
        val noDispute = db.collection("disputes").document(seedRental(owner.uid, borrower.uid).id)

        signInAs(owner)
        dispute.get(Source.SERVER).await()
        signInAs(borrower)
        dispute.get(Source.SERVER).await()
        noDispute.get(Source.SERVER).await() // a rental without a dispute reads as missing
        // A dispute whose rental is missing has no participants, so nobody reads it.
        assertDenied { seedDispute(db.collection("rentals").document()).get(Source.SERVER).await() }
        newAccount()
        assertDenied { dispute.get(Source.SERVER).await() }
      }

  @Test
  fun aParticipantWithoutAVerifiedEpflAccountCannotReadTheDispute() =
      runBlocking<Unit> {
        val owner = newAccount()
        // Each sign-up signs the new account in, so each dispute is read as its borrower.
        val gmail =
            FirebaseEmulator.signUpVerified("rules-${UUID.randomUUID()}@gmail.com", PASSWORD)
        val gmailDispute = seedDispute(seedRental(owner.uid, gmail))
        assertDenied { gmailDispute.get(Source.SERVER).await() }

        val unverified =
            Firebase.auth
                .createUserWithEmailAndPassword("rules-${UUID.randomUUID()}@epfl.ch", PASSWORD)
                .await()
                .user!!
                .uid
        val unverifiedDispute = seedDispute(seedRental(owner.uid, unverified))
        assertDenied { unverifiedDispute.get(Source.SERVER).await() }
      }

  @Test
  fun onlyCloudFunctionsWriteADispute() =
      runBlocking<Unit> {
        val owner = newAccount()
        val borrower = newAccount()
        val dispute = seedDispute(seedRental(owner.uid, borrower.uid))
        val noDispute = db.collection("disputes").document(seedRental(owner.uid, borrower.uid).id)

        for (account in listOf(owner, borrower)) {
          signInAs(account)
          assertDenied { dispute.update("agreedAt", FieldValue.serverTimestamp()).await() }
          assertDenied { dispute.update("stage", "COURT").await() }
          assertDenied { dispute.delete().await() }
          assertDenied { noDispute.set(mapOf("stage" to "SETTLE")).await() }
        }
      }

  private companion object {
    const val PASSWORD = "password123"
  }
}
