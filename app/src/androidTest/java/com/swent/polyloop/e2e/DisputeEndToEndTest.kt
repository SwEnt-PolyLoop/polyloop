// Made with Claude.

package com.swent.polyloop.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.R
import com.swent.polyloop.model.dispute.DisputeFields
import com.swent.polyloop.model.dispute.DisputeStage
import com.swent.polyloop.model.rental.RentalFields
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.navigation.Screen
import com.swent.polyloop.utils.FirebaseEmulator
import java.time.Duration
import java.time.Instant
import org.junit.Test
import org.junit.runner.RunWith

/** An open dispute, followed by the borrower from My rentals to the Dispute screen. */
@RunWith(AndroidJUnit4::class)
class DisputeEndToEndTest : EndToEndTestBase() {

  @Test
  fun anOpenDisputeShowsTheLendersProposalAndPausesTheChat() {
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
            RentalStatus.DISPUTED,
            extra = mapOf(RentalFields.DEPOSIT to DEPOSIT),
        )
    seedDispute(rentalId)
    setApp()
    val row = C.Tag.my_rentals_row_ + rentalId

    // My rentals: the rental shows as disputed and opens Rental detail.
    openFromMenu(Screen.MyRentals)
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_tab_borrowing).performClick()
    waitForTag(row)
    inNode(row, C.Tag.my_rentals_row_status)
        .assertTextEquals(context.getString(R.string.rental_status_disputed))
    composeTestRule.onNodeWithTag(row).performClick()

    // Rental detail: the chat is paused, and the main action opens the dispute.
    waitForText(C.Tag.rental_detail_title, listing.title)
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_chat_button).assertIsNotEnabled()
    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_chat_paused)
        .performScrollTo()
        .assertIsDisplayed()
    pressWhenEnabled(C.Tag.rental_detail_action_button)

    // Dispute: the item and deposit, the lender's split to answer, and the days left to settle.
    waitForTag(C.Tag.dispute_proposal_card)
    assertRoute(Screen.Dispute)
    composeTestRule
        .onNodeWithTag(C.Tag.dispute_item_deposit)
        .assertTextEquals(context.getString(R.string.dispute_item_deposit, listing.title, DEPOSIT))
    composeTestRule
        .onNodeWithTag(C.Tag.dispute_proposal_title)
        .assertTextEquals(context.getString(R.string.dispute_they_propose, LENDER_NAME))
    val split =
        context.getString(R.string.dispute_history_split, LENDER_PERCENT, 100 - LENDER_PERCENT)
    waitFor(hasText(split) and hasAnyAncestor(hasTestTag(C.Tag.dispute_history_row)))
    composeTestRule.onNodeWithTag(C.Tag.dispute_accept_button).assertIsEnabled()
    composeTestRule.onNodeWithTag(C.Tag.dispute_counter_button).assertIsEnabled()
    composeTestRule
        .onNodeWithTag(C.Tag.dispute_escalation)
        .performScrollTo()
        .assertTextEquals(
            context.resources.getQuantityString(
                R.plurals.dispute_auto_escalates,
                DAYS_LEFT.toInt(),
                DAYS_LEFT.toInt(),
            )
        )
    composeTestRule.onNodeWithTag(C.Tag.dispute_escalate_button).assertIsEnabled()
    // Accepting, countering and escalating call Cloud Functions: not covered yet.
  }

  /**
   * The dispute of [rentalId] in direct settlement, with the lender's proposal, as the Cloud
   * Functions write it. It escalates by itself in a little less than [DAYS_LEFT] days, which the
   * screen rounds up.
   */
  private fun seedDispute(rentalId: String) {
    val path = "${DisputeFields.COLLECTION}/$rentalId"
    val now = Instant.now()
    FirebaseEmulator.createDocumentAsAdmin(
        path,
        mapOf(
            DisputeFields.STAGE to DisputeStage.SETTLE.name,
            DisputeFields.PROPOSALS to
                listOf(
                    mapOf(
                        DisputeFields.PROPOSAL_AUTHOR_ID to lenderId,
                        DisputeFields.PROPOSAL_LENDER_PERCENT to LENDER_PERCENT,
                        DisputeFields.PROPOSAL_CREATED_AT to timestamp(now),
                    )
                ),
            DisputeFields.ESCALATES_AT to
                timestamp(now.plus(Duration.ofDays(DAYS_LEFT)).minus(Duration.ofHours(1))),
            DisputeFields.ESCALATED_BY to null,
            DisputeFields.AGREED_AT to null,
        ),
    )
    trackDocument(path)
  }

  private companion object {
    const val DEPOSIT = 40
    const val LENDER_PERCENT = 70
    const val DAYS_LEFT = 10L
  }
}
