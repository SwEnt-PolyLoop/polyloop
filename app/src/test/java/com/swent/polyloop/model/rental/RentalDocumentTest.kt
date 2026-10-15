// Made with Claude.

package com.swent.polyloop.model.rental

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingStatus
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RentalDocumentTest {

  private val createdAt = Instant.parse("2026-10-14T10:00:00Z")
  private val lastMessageAt = Instant.parse("2026-10-14T12:30:00Z")

  private val validData: Map<String, Any> =
      mapOf(
          RentalFields.LISTING_ID to "listing-1",
          RentalFields.LISTING_TITLE to "Camping tent",
          RentalFields.OWNER_ID to "owner",
          RentalFields.OWNER_NAME to "Vida",
          RentalFields.BORROWER_ID to "borrower",
          RentalFields.BORROWER_NAME to "Loca",
          RentalFields.PARTICIPANTS to listOf("owner", "borrower"),
          RentalFields.START_DATE to "2026-10-20",
          RentalFields.END_DATE to "2026-10-22",
          RentalFields.STATUS to "ACCEPTED",
          RentalFields.PRICE_PER_DAY to 5L,
          RentalFields.TOTAL_PRICE to 15L,
          RentalFields.PICKUP_AREA to "Ecublens",
          RentalFields.DROP_OFF_AREA to "Renens",
          RentalFields.PICKUP_ADDRESS to "Route Cantonale 1",
          RentalFields.RETURN_ADDRESS to "Avenue du Tir-Fédéral 2",
          RentalFields.CREATED_AT to Timestamp(createdAt),
          RentalFields.LAST_MESSAGE to "See you at 6",
          RentalFields.LAST_MESSAGE_AT to Timestamp(lastMessageAt),
      )

  private val expectedRental =
      Rental(
          id = "rental-1",
          listingId = "listing-1",
          listingTitle = "Camping tent",
          ownerId = "owner",
          ownerName = "Vida",
          borrowerId = "borrower",
          borrowerName = "Loca",
          startDate = LocalDate.of(2026, 10, 20),
          endDate = LocalDate.of(2026, 10, 22),
          status = RentalStatus.ACCEPTED,
          pricePerDay = 5,
          totalPrice = 15,
          pickupArea = "Ecublens",
          dropOffArea = "Renens",
          pickupAddress = "Route Cantonale 1",
          returnAddress = "Avenue du Tir-Fédéral 2",
          createdAt = createdAt,
          lastMessage = "See you at 6",
          lastMessageAt = lastMessageAt,
      )

  // ---------- rentalFromDocument ----------

  @Test
  fun readsEveryField() {
    assertEquals(expectedRental, rentalFromDocument("rental-1", validData))
  }

  @Test
  fun missingCopiedTextsReadAsEmptyAndLastMessageAtAsCreatedAt() {
    val optional =
        listOf(
            RentalFields.LISTING_TITLE,
            RentalFields.OWNER_NAME,
            RentalFields.BORROWER_NAME,
            RentalFields.PICKUP_AREA,
            RentalFields.DROP_OFF_AREA,
            RentalFields.PICKUP_ADDRESS,
            RentalFields.RETURN_ADDRESS,
            RentalFields.LAST_MESSAGE,
            RentalFields.LAST_MESSAGE_AT,
        )

    val rental = rentalFromDocument("rental-1", validData - optional)

    assertEquals(
        expectedRental.copy(
            listingTitle = "",
            ownerName = "",
            borrowerName = "",
            pickupArea = "",
            dropOffArea = "",
            pickupAddress = "",
            returnAddress = "",
            lastMessage = "",
            lastMessageAt = createdAt,
        ),
        rental,
    )
  }

  @Test
  fun returnsNullWhenARequiredFieldIsMissing() {
    val required =
        listOf(
            RentalFields.LISTING_ID,
            RentalFields.OWNER_ID,
            RentalFields.BORROWER_ID,
            RentalFields.START_DATE,
            RentalFields.END_DATE,
            RentalFields.STATUS,
            RentalFields.PRICE_PER_DAY,
            RentalFields.TOTAL_PRICE,
            RentalFields.CREATED_AT,
        )

    required.forEach { field ->
      assertNull("without $field", rentalFromDocument("rental-1", validData - field))
    }
  }

  @Test
  fun returnsNullWhenAFieldHasTheWrongType() {
    val wrong =
        mapOf(
            RentalFields.STATUS to "UNKNOWN",
            RentalFields.START_DATE to "20.10.2026",
            RentalFields.PRICE_PER_DAY to 5.5,
            RentalFields.CREATED_AT to "2026-10-14T10:00:00Z",
            RentalFields.OWNER_ID to 42L,
        )

    wrong.forEach { (field, value) ->
      assertNull(
          "with $field = $value",
          rentalFromDocument("rental-1", validData + (field to value)),
      )
    }
  }

  // ---------- rentalRequestDocument ----------

  private val listing =
      Listing(
          id = "listing-1",
          ownerId = "owner",
          title = "Camping tent",
          description = "Light 2-person tent.",
          category = ListingCategory.SPORTS_OUTDOOR,
          photoUrls = List(3) { "https://example.com/$it.jpg" },
          pricePerDay = 5,
          itemValue = 120,
          availableFrom = LocalDate.of(2026, 10, 1),
          availableTo = LocalDate.of(2026, 12, 31),
          status = ListingStatus.PUBLISHED,
          pickupArea = "Ecublens",
          dropOffArea = "Renens",
      )

  private val borrower = AuthUser(uid = "borrower", email = "loca@epfl.ch", name = "Loca")

  @Test
  fun requestDocumentCopiesTheListingAndTheNames() {
    val document =
        rentalRequestDocument(
            listing,
            " Vida ",
            borrower,
            LocalDate.of(2026, 10, 20),
            LocalDate.of(2026, 10, 22),
        )

    assertEquals("listing-1", document[RentalFields.LISTING_ID])
    assertEquals("Camping tent", document[RentalFields.LISTING_TITLE])
    assertEquals("owner", document[RentalFields.OWNER_ID])
    assertEquals("Vida", document[RentalFields.OWNER_NAME])
    assertEquals("borrower", document[RentalFields.BORROWER_ID])
    assertEquals("Loca", document[RentalFields.BORROWER_NAME])
    assertEquals(listOf("owner", "borrower"), document[RentalFields.PARTICIPANTS])
    assertEquals("2026-10-20", document[RentalFields.START_DATE])
    assertEquals("2026-10-22", document[RentalFields.END_DATE])
    assertEquals("REQUESTED", document[RentalFields.STATUS])
    assertEquals(5, document[RentalFields.PRICE_PER_DAY])
    assertEquals(15, document[RentalFields.TOTAL_PRICE])
    assertEquals("Ecublens", document[RentalFields.PICKUP_AREA])
    assertEquals("Renens", document[RentalFields.DROP_OFF_AREA])
    assertEquals("", document[RentalFields.PICKUP_ADDRESS])
    assertEquals("", document[RentalFields.RETURN_ADDRESS])
    assertEquals("", document[RentalFields.LAST_MESSAGE])
  }

  @Test
  fun requestDocumentUsesOneServerTimestampForCreatedAtAndLastMessageAt() {
    val document =
        rentalRequestDocument(
            listing,
            "Vida",
            borrower,
            LocalDate.of(2026, 10, 20),
            LocalDate.of(2026, 10, 20),
        )

    assertTrue(document[RentalFields.CREATED_AT] is FieldValue)
    assertSame(document[RentalFields.CREATED_AT], document[RentalFields.LAST_MESSAGE_AT])
  }
}
