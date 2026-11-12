// Made with Claude.

package com.swent.polyloop.e2e

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.swent.polyloop.R
import com.swent.polyloop.model.listing.Listing
import com.swent.polyloop.model.listing.ListingCategory
import com.swent.polyloop.model.listing.ListingFields
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.navigation.Screen
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Create listing against Firestore. Publishing needs photos from the in-app camera, which the tests
 * cannot take (the CI's emulator has no camera), so only the refusal without photos is covered.
 */
@RunWith(AndroidJUnit4::class)
class CreateListingEndToEndTest : EndToEndTestBase() {

  @Test
  fun publishWithoutPhotosIsRefusedAndWritesNothing() {
    val uid = signInWithoutTheUi()
    setApp()
    openFromMenu(Screen.CreateListing)
    waitForTag(C.Tag.create_listing_screen)

    fillField(C.Tag.create_listing_title_field, "Electric drill")
    fillField(C.Tag.create_listing_description_field, "Cordless, two batteries.")
    // Tapped through their click action: on a phone, the keyboard left open by the description can
    // cover the menu, and a real tap would then land on the keyboard.
    composeTestRule
        .onNodeWithTag(C.Tag.create_listing_category_field)
        .performScrollTo()
        .performSemanticsAction(SemanticsActions.OnClick)
    composeTestRule
        .onNodeWithTag(C.Tag.create_listing_category_option_ + ListingCategory.TOOLS_DIY.name)
        .performSemanticsAction(SemanticsActions.OnClick)
    fillField(C.Tag.create_listing_price_field, "4")
    fillField(C.Tag.create_listing_item_value_field, "90")
    composeTestRule
        .onNodeWithTag(C.Tag.create_listing_availability_field)
        .performScrollTo()
        .performClick()
    waitForTag(C.Tag.date_picker_sheet)
    pickDays(firstDay, firstDay.plusDays(1))
    composeTestRule.onNodeWithTag(C.Tag.date_picker_confirm_button).performScrollTo().performClick()
    composeTestRule.onNodeWithTag(C.Tag.date_picker_sheet).assertAbsent()
    fillField(C.Tag.create_listing_pickup_field, "Route Cantonale 1, Ecublens")
    fillField(C.Tag.create_listing_drop_off_field, "Avenue Piccard 2, Lausanne")

    // Publish is refused without photos: the form stays, says how many photos are missing, and
    // nothing reaches Firestore.
    composeTestRule.onNodeWithTag(C.Tag.create_listing_publish_button).performClick()
    assertRoute(Screen.CreateListing)
    // Checked by its text: on a phone the keyboard left open by the last field can cover it.
    composeTestRule
        .onNodeWithTag(C.Tag.create_listing_photos_hint)
        .assertTextEquals(
            context.resources.getQuantityString(
                R.plurals.create_listing_missing_photos,
                Listing.MIN_PHOTOS,
                Listing.MIN_PHOTOS,
            )
        )
    // A Publish that went through would still be running (the button disabled while it uploads)
    // or would already have written the listing.
    composeTestRule.onNodeWithTag(C.Tag.create_listing_publish_button).assertIsEnabled()
    val ownListings = runBlocking {
      Firebase.firestore
          .collection(ListingFields.COLLECTION)
          .whereEqualTo(ListingFields.OWNER_ID, uid)
          .get(Source.SERVER)
          .await()
    }
    assertTrue("Publish without photos wrote a listing", ownListings.isEmpty)
  }
}
