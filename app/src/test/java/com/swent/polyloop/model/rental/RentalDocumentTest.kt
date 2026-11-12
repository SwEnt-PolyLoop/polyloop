// Made with Claude.

package com.swent.polyloop.model.rental

import com.google.firebase.Timestamp
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RentalDocumentTest {

  private val expiresAt = Instant.parse("2026-10-21T10:00:00Z")

  /** The stored rental with [offer] and a deposit of 60. */
  private fun withOffer(offer: Any?) =
      validData + (RentalFields.OFFER to offer) + (RentalFields.DEPOSIT to 60L)

  @Test
  fun readsTheDepositAndAFirstOffer() {
    val rental =
        rentalFromDocument(
            "rental-1",
            withOffer(
                mapOf(
                    RentalFields.OFFER_AMOUNT to 180L,
                    RentalFields.OFFER_AUTHOR_ID to "owner",
                    RentalFields.OFFER_IS_COUNTER to false,
                    RentalFields.OFFER_EXPIRES_AT to Timestamp(expiresAt),
                )
            ),
        )

    assertEquals(60, rental?.deposit)
    assertEquals(DepositOffer(180, "owner", false, null, expiresAt), rental?.offer)
  }

  @Test
  fun readsACounterofferWithThePreviousAmount() {
    val rental =
        rentalFromDocument(
            "rental-1",
            withOffer(
                mapOf(
                    RentalFields.OFFER_AMOUNT to 120L,
                    RentalFields.OFFER_AUTHOR_ID to "borrower",
                    RentalFields.OFFER_IS_COUNTER to true,
                    RentalFields.OFFER_PREVIOUS_AMOUNT to 180L,
                    RentalFields.OFFER_EXPIRES_AT to Timestamp(expiresAt),
                )
            ),
        )

    assertEquals(DepositOffer(120, "borrower", true, 180, expiresAt), rental?.offer)
  }

  @Test
  fun aMissingDepositAndOfferReadAsNone() {
    val rental = rentalFromDocument("rental-1", validData)

    assertEquals(0, rental?.deposit)
    assertNull(rental?.offer)
  }

  @Test
  fun readsTheLateFees() {
    val rental = rentalFromDocument("rental-1", validData + (RentalFields.LATE_FEES to 14L))

    assertEquals(14, rental?.lateFees)
  }

  @Test
  fun missingLateFeesReadAsZero() {
    val rental = rentalFromDocument("rental-1", validData)

    assertEquals(0, rental?.lateFees)
  }

  @Test
  fun aMalformedOfferReadsAsNoOfferAndKeepsTheRental() {
    val complete =
        mapOf(
            RentalFields.OFFER_AMOUNT to 180L,
            RentalFields.OFFER_AUTHOR_ID to "owner",
            RentalFields.OFFER_IS_COUNTER to false,
            RentalFields.OFFER_EXPIRES_AT to Timestamp(expiresAt),
        )
    val malformed =
        listOf<Any>("not a map") +
            complete.keys.map { field -> complete - field } +
            listOf(complete + (RentalFields.OFFER_AMOUNT to 1.5))

    for (offer in malformed) {
      val rental = rentalFromDocument("rental-1", withOffer(offer))
      assertEquals("rental-1", rental?.id)
      assertNull(rental?.offer)
    }
  }

  private val createdAt = Instant.parse("2026-10-14T10:00:00Z")
  private val lastMessageAt = Instant.parse("2026-10-14T12:30:00Z")

  private val validData: Map<String, Any> =
      mapOf(
          RentalFields.LISTING_ID to "listing-1",
          RentalFields.LISTING_TITLE to "Camping tent",
          RentalFields.LISTING_PHOTO_URL to "https://example.com/tent.jpg",
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
          listingPhotoUrl = "https://example.com/tent.jpg",
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
            RentalFields.LISTING_PHOTO_URL,
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
            listingPhotoUrl = "",
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
}
