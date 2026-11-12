// Made with Claude.

package com.swent.polyloop.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.R
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.navigation.Screen
import java.time.Instant
import java.util.UUID
import org.junit.Test
import org.junit.runner.RunWith

/** The lender's side: a request arrives on one of their listings and they review it. */
@RunWith(AndroidJUnit4::class)
class LenderEndToEndTest : EndToEndTestBase() {

  @Test
  fun aRequestOpensReviewRequestWithItsOverlappingRequests() {
    val uid = signInWithoutTheUi()
    seedProfile(borrowerId, BORROWER_NAME, rating = RATING, reviews = REVIEWS)
    val listing = seedListing(ownerId = uid, to = firstDay.plusDays(LAST_TEST_DAY))
    val now = Instant.now()
    fun request(borrower: String, startOffset: Long, endOffset: Long) =
        seedRental(
            listing,
            borrower,
            BORROWER_NAME,
            USER_NAME,
            firstDay.plusDays(startOffset),
            firstDay.plusDays(endOffset),
            RentalStatus.REQUESTED,
            createdAt = now.minusSeconds(startOffset),
        )
    val reviewed = request(borrowerId, 0, 1)
    // Accepting the reviewed request would decline the first two: one shares a day with it, the
    // other starts on its buffer day. The last one is far enough.
    request("borrower-${UUID.randomUUID()}", 1, 2)
    request("borrower-${UUID.randomUUID()}", 2, 2)
    request("borrower-${UUID.randomUUID()}", 6, 7)
    setApp()
    val card = C.Tag.my_rentals_request_ + reviewed

    // My rentals: the request waits under Lending, with its Review request button.
    openFromMenu(Screen.MyRentals)
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_tab_lending).performClick()
    waitForTag(card)
    inNode(card, C.Tag.my_rentals_review_request_button).performClick()

    // Review request: the request, its borrower and the deposit suggested from the item value.
    waitForTag(C.Tag.review_request_summary)
    assertRoute(Screen.ReviewRequest)
    composeTestRule
        .onNodeWithTag(C.Tag.review_request_summary)
        .assertTextContains(listing.title, substring = true)
    composeTestRule
        .onNodeWithTag(C.Tag.review_request_price)
        .assertTextEquals(context.getString(R.string.rental_detail_price, 2 * PRICE_PER_DAY))
    composeTestRule
        .onNodeWithTag(C.Tag.review_request_suggested_deposit)
        .assertTextEquals(context.getString(R.string.listing_card_price, ITEM_VALUE))
    waitForText(C.Tag.rental_detail_person_name, BORROWER_NAME)
    val rating =
        context.resources.getQuantityString(R.plurals.profile_rating, REVIEWS, RATING, REVIEWS)
    val ratingTag = hasTestTag(C.Tag.rental_detail_person_rating)
    waitFor((ratingTag or hasAnyAncestor(ratingTag)) and hasText(rating))
    composeTestRule
        .onNodeWithTag(C.Tag.review_request_deposit)
        .assertTextContains(ITEM_VALUE.toString())
    waitForText(
        C.Tag.review_request_overlapping,
        context.resources.getQuantityString(R.plurals.review_request_overlapping, 2, 2),
    )

    // Send offer waits for both addresses; Decline does not.
    composeTestRule.onNodeWithTag(C.Tag.review_request_decline).assertIsEnabled()
    composeTestRule.onNodeWithTag(C.Tag.review_request_send).assertIsNotEnabled()
    fillField(C.Tag.review_request_pickup, "Route Cantonale 1, Ecublens")
    composeTestRule.onNodeWithTag(C.Tag.review_request_send).assertIsNotEnabled()
    fillField(C.Tag.review_request_return, "Avenue Piccard 2, Lausanne")
    composeTestRule.onNodeWithTag(C.Tag.review_request_send).assertIsEnabled()
    // Sending calls makeOffer, which needs the Functions emulator: not covered yet.
  }

  private companion object {
    const val RATING = 4.5
    const val REVIEWS = 2
  }
}
