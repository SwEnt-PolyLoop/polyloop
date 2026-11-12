// Made with Claude.

package com.swent.polyloop.e2e

import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.swent.polyloop.R
import com.swent.polyloop.model.listing.BookedRange
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingFields
import com.swent.polyloop.model.rental.RentalFields
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.navigation.Screen
import com.swent.polyloop.utils.FirebaseEmulator
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The borrower's side: finding an item, requesting it, and following the request. */
@RunWith(AndroidJUnit4::class)
class BorrowerEndToEndTest : EndToEndTestBase() {

  @Test
  fun browseFindsAListingAndSendsARequest() {
    seedProfile(lenderId, LENDER_NAME)
    val listing = seedListing()
    seedWallet(signInWithoutTheUi(), BALANCE)
    setApp()
    val card = C.Tag.listing_card_ + listing.id
    val lastDay = listing.availableTo

    // Search: the made-up word finds the listing, an unknown word finds nothing.
    composeTestRule.onNodeWithTag(C.Tag.browse_search_bar).performTextInput(titleWord)
    waitForTag(card)
    composeTestRule.onNodeWithTag(C.Tag.browse_clear_search).performClick()
    composeTestRule.onNodeWithTag(C.Tag.browse_search_bar).performTextInput("zzqqxxnotaword")
    waitForTag(C.Tag.browse_empty)
    composeTestRule.onNodeWithTag(C.Tag.browse_clear_search).performClick()

    // Categories: another category hides it, "All" brings it back.
    composeTestRule.onNodeWithTag(C.Tag.browse_search_bar).performTextInput(titleWord)
    waitForTag(card)
    composeTestRule
        .onNodeWithTag(C.Tag.browseCategoryChip(ListingCategory.ELECTRONICS))
        .performScrollTo()
        .performClick()
    composeTestRule.onNodeWithTag(card).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.browse_category_all).performScrollTo().performClick()
    composeTestRule.onNodeWithTag(card).assertIsDisplayed()

    // Listing detail.
    composeTestRule.onNodeWithTag(card).performClick()
    waitForText(C.Tag.listing_detail_title, listing.title)
    assertRoute(Screen.ListingDetail)
    composeTestRule.onNodeWithTag(C.Tag.listing_detail_price).performScrollTo().assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(C.Tag.listing_detail_deposit)
        .performScrollTo()
        .assertIsDisplayed()

    // Request: ending on the last available day leaves no free day after it, so it is refused.
    pressRequest()
    waitForTag(C.Tag.date_picker_sheet)
    pickDays(firstDay, lastDay)
    composeTestRule.onNodeWithTag(C.Tag.date_picker_error).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.date_picker_confirm_button).assertIsNotEnabled()

    // Two days instead: both picked, priced at two days; sending opens the new rental's chat.
    pickDay(firstDay.plusDays(1))
    composeTestRule.onNodeWithTag(C.Tag.date_picker_error).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.datePickerDay(firstDay)).assertIsSelected()
    composeTestRule.onNodeWithTag(C.Tag.datePickerDay(firstDay.plusDays(1))).assertIsSelected()
    composeTestRule.onNodeWithTag(C.Tag.datePickerDay(lastDay)).assertIsNotSelected()
    composeTestRule
        .onNodeWithTag(C.Tag.date_picker_summary_total)
        .assertTextEquals(context.getString(R.string.listing_card_price, 2 * PRICE_PER_DAY))
    // Send request is enabled: the placeholder balance covers the price for now, and the seeded
    // wallet will once Listing detail reads the wallet (#413).
    composeTestRule.onNodeWithTag(C.Tag.date_picker_confirm_button).performScrollTo().performClick()
    waitForText(C.Tag.chat_header_item, listing.title)
    assertRoute(Screen.Chat)
    pressBack()
    assertRoute(Screen.ListingDetail)

    // The lender card opens the lender's profile; Back returns to the listing.
    composeTestRule.onNodeWithTag(C.Tag.listing_detail_lender_card).performScrollTo().performClick()
    waitForText(C.Tag.profile_name, LENDER_NAME)
    assertRoute(Screen.UserProfile)
    pressBack()
    assertRoute(Screen.ListingDetail)
  }

  @Test
  fun bookedDaysAreGreyedAndDatesBookedMeanwhileAreRefusedThenFreeDatesAreSent() {
    seedProfile(lenderId, LENDER_NAME)
    val booked = BookedRange(firstDay.plusDays(4), firstDay.plusDays(5))
    val listing = seedListing(to = firstDay.plusDays(LAST_TEST_DAY), bookedRanges = listOf(booked))
    val uid = signInWithoutTheUi()
    seedWallet(uid, BALANCE)
    setApp()
    openRequestPicker(listing)

    // The booked days and the buffer day on each side of them cannot be picked.
    assertDay(booked.start, R.string.date_picker_day_booked)
    assertDay(booked.end, R.string.date_picker_day_booked)
    assertDay(booked.start.minusDays(1), R.string.date_picker_day_buffer)
    assertDay(booked.end.plusDays(1), R.string.date_picker_day_buffer)
    showMonthOf(firstDay)
    composeTestRule.onNodeWithTag(C.Tag.datePickerDay(firstDay)).assertIsEnabled()

    // Someone else's rental of the same days is accepted while the picker is open.
    pickDays(firstDay, firstDay.plusDays(1))
    val takenMeanwhile = BookedRange(firstDay, firstDay.plusDays(1))
    FirebaseEmulator.updateDocumentAsAdmin(
        "${ListingFields.COLLECTION}/${listing.id}",
        mapOf(ListingFields.BOOKED_RANGES to bookedRangesFields(listOf(booked, takenMeanwhile))),
    )

    // Sending reads the booked days again: nothing is sent, and the new booked days are greyed.
    composeTestRule.onNodeWithTag(C.Tag.date_picker_confirm_button).performScrollTo().performClick()
    waitForText(
        C.Tag.date_picker_send_error,
        context.getString(R.string.listing_detail_dates_taken),
    )
    assertRoute(Screen.ListingDetail)
    assertDay(firstDay, R.string.date_picker_day_booked)
    val sent = runBlocking {
      Firebase.firestore
          .collection(RentalFields.COLLECTION)
          .whereEqualTo(RentalFields.BORROWER_ID, uid)
          .get(Source.SERVER)
          .await()
    }
    assertTrue("A request was sent for booked days", sent.isEmpty)

    // Free days after the booking go through: the request is sent and its chat opens.
    val retryStart = booked.end.plusDays(2)
    pickDays(retryStart, retryStart.plusDays(1))
    composeTestRule.onNodeWithTag(C.Tag.date_picker_send_error).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.date_picker_confirm_button).performScrollTo().performClick()
    waitForText(C.Tag.chat_header_item, listing.title)
    assertRoute(Screen.Chat)
  }

  @Test
  fun aSentRequestShowsInMyRentalsAndTheChatList() {
    seedProfile(lenderId, LENDER_NAME)
    val listing = seedListing()
    val uid = signInWithoutTheUi()
    val rentalId =
        seedRental(
            listing,
            uid,
            USER_NAME,
            LENDER_NAME,
            firstDay,
            firstDay.plusDays(1),
            RentalStatus.REQUESTED,
        )
    setApp()
    val row = C.Tag.my_rentals_row_ + rentalId

    // My rentals: the request is under Borrowing, Lending is empty.
    openFromMenu(Screen.MyRentals)
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_tab_borrowing).performClick()
    waitForTag(row)
    inNode(row, C.Tag.my_rentals_row_title).assertTextEquals(listing.title)
    inNode(row, C.Tag.my_rentals_row_person)
        .assertTextEquals(context.getString(R.string.my_rentals_from, LENDER_NAME))
    inNode(row, C.Tag.my_rentals_row_status)
        .assertTextEquals(context.getString(R.string.rental_status_requested))
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_tab_lending).performClick()
    waitForTag(C.Tag.my_rentals_empty)
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_tab_borrowing).performClick()
    waitForTag(row)

    // Tapping the request opens its Rental detail (a wrong rental would show another title); Back
    // returns to My rentals.
    composeTestRule.onNodeWithTag(row).performClick()
    waitForText(C.Tag.rental_detail_title, listing.title)
    assertRoute(Screen.RentalDetail)
    pressBack()
    assertRoute(Screen.MyRentals)

    // Chat list: the rental's conversation opens its chat, whose header names the lender and the
    // item (a wrong rental would show another title), and links back to Rental detail.
    openFromMenu(Screen.ChatList)
    val conversation = C.Tag.chat_list_row_ + rentalId
    waitForTag(conversation)
    composeTestRule.onNodeWithTag(conversation).performClick()
    waitForText(C.Tag.chat_header_item, listing.title)
    assertRoute(Screen.Chat)
    composeTestRule.onNodeWithTag(C.Tag.chat_header_name).assertTextEquals(LENDER_NAME)
    composeTestRule.onNodeWithTag(C.Tag.chat_rental_details).performClick()
    waitForText(C.Tag.rental_detail_title, listing.title)
    assertRoute(Screen.RentalDetail)
  }

  @Test
  fun aReceivedOfferShowsInMyRentalsTheChatAndRentalDetail() {
    seedProfile(lenderId, LENDER_NAME)
    val listing = seedListing()
    val uid = signInWithoutTheUi()
    // The offer fields the app reads, as makeOffer writes them; the CI cannot call it (no
    // Functions).
    val offer =
        mapOf(
            RentalFields.OFFER_AMOUNT to OFFER,
            RentalFields.OFFER_AUTHOR_ID to lenderId,
            RentalFields.OFFER_IS_COUNTER to false,
            // An offer expires on the rental's first day when that comes before a week.
            RentalFields.OFFER_EXPIRES_AT to
                timestamp(firstDay.atStartOfDay(ZoneId.systemDefault()).toInstant()),
        )
    val rentalId =
        seedRental(
            listing,
            uid,
            USER_NAME,
            LENDER_NAME,
            firstDay,
            firstDay.plusDays(1),
            RentalStatus.REQUESTED,
            extra = mapOf(RentalFields.OFFER to offer),
        )
    setApp()
    val card = C.Tag.my_rentals_request_ + rentalId
    val amount = context.getString(R.string.listing_card_price, OFFER)

    // My rentals: the request says an offer waits for the borrower, and its button opens the chat.
    openFromMenu(Screen.MyRentals)
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_tab_borrowing).performClick()
    waitForTag(card)
    inNode(card, C.Tag.my_rentals_row_status)
        .assertTextEquals(context.getString(R.string.my_rentals_badge_offer_received))
    inNode(card, C.Tag.my_rentals_open_chat_button).performClick()

    // Chat: the offer card gives the borrower the three answers.
    waitForTag(C.Tag.chat_offer_card)
    assertRoute(Screen.Chat)
    composeTestRule
        .onNodeWithTag(C.Tag.chat_offer_title)
        .assertTextEquals(context.getString(R.string.chat_offer_title))
    composeTestRule.onNodeWithTag(C.Tag.chat_offer_amount).assertTextEquals(amount)
    composeTestRule.onNodeWithTag(C.Tag.chat_offer_accept).assertIsEnabled()
    composeTestRule.onNodeWithTag(C.Tag.chat_offer_decline).assertIsEnabled()

    // Counter asks for an amount: Send waits for one, Back returns to the offer.
    composeTestRule.onNodeWithTag(C.Tag.chat_offer_counter).performClick()
    composeTestRule.onNodeWithTag(C.Tag.chat_offer_counter_send).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.chat_offer_counter_field).performTextInput("60")
    composeTestRule.onNodeWithTag(C.Tag.chat_offer_counter_send).assertIsEnabled()
    composeTestRule.onNodeWithTag(C.Tag.chat_offer_counter_back).performClick()
    composeTestRule.onNodeWithTag(C.Tag.chat_offer_amount).assertTextEquals(amount)

    // Rental detail shows the same offer.
    composeTestRule.onNodeWithTag(C.Tag.chat_rental_details).performClick()
    waitForText(C.Tag.rental_detail_offer, amount)
    assertRoute(Screen.RentalDetail)
  }

  /** Searches [listing] on Browse, opens it and opens the date picker of its Request button. */
  private fun openRequestPicker(listing: Listing) {
    val card = C.Tag.listing_card_ + listing.id
    composeTestRule.onNodeWithTag(C.Tag.browse_search_bar).performTextInput(titleWord)
    waitForTag(card)
    composeTestRule.onNodeWithTag(card).performClick()
    waitForText(C.Tag.listing_detail_title, listing.title)
    pressRequest()
    waitForTag(C.Tag.date_picker_sheet)
  }

  /**
   * Presses "Request" once it is enabled, which it will only be once the wallet's balance is read,
   * when Listing detail reads the wallet (#413). It sits in a bar pinned at the bottom of the
   * screen, so there is nothing to scroll first.
   */
  private fun pressRequest() {
    waitUntilEnabled(C.Tag.listing_detail_request_button)
    composeTestRule.onNodeWithTag(C.Tag.listing_detail_request_button).performClick()
  }

  /**
   * Checks that [day] cannot be picked and is read out with [descriptionRes] (booked or buffer).
   */
  private fun assertDay(day: LocalDate, descriptionRes: Int) {
    showMonthOf(day)
    composeTestRule
        .onNodeWithTag(C.Tag.datePickerDay(day))
        .assertIsNotEnabled()
        .assertContentDescriptionEquals(context.getString(descriptionRes, day.dayOfMonth))
  }

  private companion object {
    const val OFFER = 80

    // Far more than the requests cost (a few days at PRICE_PER_DAY), once the picker reads it.
    const val BALANCE = 100
  }
}
