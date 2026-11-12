// Made with Claude.

package com.swent.polyloop.e2e

import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.R
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.navigation.Screen
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

  /**
   * Presses "Request" once it is enabled, which it will only be once the wallet's balance is read,
   * when Listing detail reads the wallet (#413). It sits in a bar pinned at the bottom of the
   * screen, so there is nothing to scroll first.
   */
  private fun pressRequest() {
    waitUntilEnabled(C.Tag.listing_detail_request_button)
    composeTestRule.onNodeWithTag(C.Tag.listing_detail_request_button).performClick()
  }

  private companion object {
    // Far more than the requests cost (a few days at PRICE_PER_DAY), once the picker reads it.
    const val BALANCE = 100
  }
}
