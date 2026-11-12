// Made with Claude.

package com.swent.polyloop.e2e

import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.swent.polyloop.R
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingStatus
import com.swent.polyloop.model.profile.ProfileFields
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.navigation.Screen
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The signed-in user's profile, its edition, and someone else's profile. */
@RunWith(AndroidJUnit4::class)
class ProfileEndToEndTest : EndToEndTestBase() {

  @Test
  fun myListingsShowWhereEachListingStands() {
    val uid = signInWithoutTheUi()
    fun ownListing(name: String, status: ListingStatus = ListingStatus.PUBLISHED) =
        seedListing(ownerId = uid, title = "$name $titleWord", status = status)
    fun rentalOf(listing: Listing, status: RentalStatus) =
        seedRental(
            listing,
            borrowerId,
            BORROWER_NAME,
            USER_NAME,
            firstDay,
            firstDay.plusDays(1),
            status,
        )
    val requested = ownListing("Drill").also { rentalOf(it, RentalStatus.REQUESTED) }
    val booked = ownListing("Tent").also { rentalOf(it, RentalStatus.ACCEPTED) }
    val onLoan = ownListing("Guitar").also { rentalOf(it, RentalStatus.ACTIVE) }
    val draft = ownListing("Kettle", ListingStatus.DRAFT)
    setApp()

    composeTestRule.onNodeWithTag(C.Tag.top_bar_profile_button).performClick()
    waitForText(C.Tag.profile_listings_count, "4")
    assertPill(requested, R.string.rental_status_requested)
    assertPill(booked, R.string.profile_listing_status_booked)
    assertPill(onLoan, R.string.profile_listing_status_on_loan)
    // In the unmerged tree, like the pills: the clickable row merges its texts into itself.
    waitForText(
        C.Tag.profile_listing_draft_ + draft.id,
        context.getString(R.string.profile_listing_status_draft),
    )

    // A row opens its listing; Back returns to the profile.
    composeTestRule
        .onNodeWithTag(C.Tag.profile_listing_ + requested.id)
        .performScrollTo()
        .performClick()
    waitForText(C.Tag.listing_detail_title, requested.title)
    assertRoute(Screen.ListingDetail)
    pressBack()
    assertRoute(Screen.Profile)
  }

  @Test
  fun aNewNameIsSavedAndShowsOnTheProfileAndInTheDrawer() {
    val uid = signInWithoutTheUi()
    setApp()

    composeTestRule.onNodeWithTag(C.Tag.top_bar_profile_button).performClick()
    waitForText(C.Tag.profile_name, USER_NAME)
    composeTestRule.onNodeWithTag(C.Tag.profile_edit_button).performScrollTo().performClick()
    waitForText(C.Tag.edit_profile_name_field, USER_NAME)

    // A blank name is refused; the trimmed new one is saved and shown back on the profile.
    composeTestRule.onNodeWithTag(C.Tag.edit_profile_name_field).performTextReplacement(" ")
    composeTestRule.onNodeWithTag(C.Tag.edit_profile_save_button).performClick()
    waitForTag(C.Tag.edit_profile_name_error)
    composeTestRule
        .onNodeWithTag(C.Tag.edit_profile_name_field)
        .performTextReplacement("  $NEW_NAME ")
    composeTestRule.onNodeWithTag(C.Tag.edit_profile_save_button).performClick()
    waitForText(C.Tag.profile_name, NEW_NAME)
    assertRoute(Screen.Profile)
    val saved = runBlocking {
      Firebase.firestore
          .collection(ProfileFields.COLLECTION)
          .document(uid)
          .get(Source.SERVER)
          .await()
    }
    assertEquals(NEW_NAME, saved.getString(ProfileFields.NAME))

    // The drawer header reads the same profile.
    composeTestRule.onNodeWithTag(C.Tag.top_bar_menu_button).performClick()
    waitForText(C.Tag.drawer_name, NEW_NAME)
  }

  @Test
  fun someoneElsesProfileListsOnlyTheirPublishedListings() {
    seedProfile(lenderId, LENDER_NAME)
    val published = seedListing()
    val draft = seedListing(title = "Kettle $titleWord", status = ListingStatus.DRAFT)
    signInWithoutTheUi()
    setApp()

    // Browse finds only the published one; its lender card opens the lender's profile.
    val card = C.Tag.listing_card_ + published.id
    composeTestRule.onNodeWithTag(C.Tag.browse_search_bar).performTextInput(titleWord)
    waitForTag(card)
    composeTestRule.onNodeWithTag(C.Tag.listing_card_ + draft.id).assertDoesNotExist()
    composeTestRule.onNodeWithTag(card).performClick()
    waitForText(C.Tag.listing_detail_title, published.title)
    composeTestRule.onNodeWithTag(C.Tag.listing_detail_lender_card).performScrollTo().performClick()
    waitForText(C.Tag.profile_name, LENDER_NAME)
    assertRoute(Screen.UserProfile)

    // Their card lists the published listing, without a pill, and leaves the draft out.
    waitForTag(C.Tag.profile_listing_ + published.id)
    composeTestRule.onNodeWithTag(C.Tag.profile_listing_ + draft.id).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.profile_listing_status_ + published.id).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.profile_sign_out_button).assertDoesNotExist()
  }

  private fun assertPill(listing: Listing, labelRes: Int) =
      waitForText(C.Tag.profile_listing_status_ + listing.id, context.getString(labelRes))

  private companion object {
    const val NEW_NAME = "Renamed user"
  }
}
