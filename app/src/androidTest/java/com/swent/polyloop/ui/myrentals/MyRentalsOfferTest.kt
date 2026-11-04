// Made with Claude.

package com.swent.polyloop.ui.myrentals

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.model.rental.DepositOffer
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The requests with a deposit offer on My rentals (Figma frames 10.10 and 11.10). */
@RunWith(AndroidJUnit4::class)
class MyRentalsOfferTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private val expiresAt = Instant.parse("2026-10-22T12:00:00Z")
  private val first = DepositOffer(180, "a", false, null, expiresAt)
  private val counter = DepositOffer(120, "b", true, 180, expiresAt)

  private fun row(offer: DepositOffer?, isMine: Boolean = false) =
      RentalRow(
          rentalId = "tent",
          itemTitle = "Camping tent",
          otherPersonName = "Loca",
          startDate = LocalDate.of(2026, 10, 15),
          returnDate = LocalDate.of(2026, 10, 17),
          location = "Ecublens",
          status = RentalStatus.REQUESTED,
          totalPrice = 45,
          offer = offer,
          isOfferMine = isMine,
      )

  private var chats = mutableListOf<String>()
  private var reviews = 0

  private fun setLending(row: RentalRow) =
      setState(
          MyRentalsUiState(
              selectedTab = RentalsTab.LENDING,
              lendingRequests = listOf(row),
              isLoading = false,
          )
      )

  private fun setBorrowing(row: RentalRow) =
      setState(
          MyRentalsUiState(
              selectedTab = RentalsTab.BORROWING,
              borrowingRequests = listOf(row),
              isLoading = false,
          )
      )

  private fun setState(state: MyRentalsUiState) {
    composeTestRule.setContent {
      MyRentalsContent(
          uiState = state,
          onTabSelected = {},
          onRentalClick = {},
          onRetry = {},
          onReviewRequest = { reviews++ },
          today = LocalDate.of(2026, 10, 1),
          onOpenChat = { chats += it },
      )
    }
  }

  private fun inCard(tag: String) =
      composeTestRule.onNode(
          hasTestTag(tag) and hasAnyAncestor(hasTestTag(C.Tag.my_rentals_request_ + "tent")),
          useUnmergedTree = true,
      )

  private fun string(id: Int) = context.getString(id)

  @Test
  fun aLenderWhoSentAnOfferSeesItAndOpensTheChat() {
    setLending(row(first, isMine = true))

    inCard(C.Tag.my_rentals_row_status)
        .assertTextEquals(string(R.string.my_rentals_badge_offer_sent))
    inCard(C.Tag.my_rentals_row_offer).assertTextEquals("45 PP reserved · 180 PP deposit offered")
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_review_request_button).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_open_chat_button).performClick()

    assertEquals(listOf("tent"), chats)
    assertEquals(0, reviews)
  }

  @Test
  fun aBorrowerWithAnOfferIsAskedToAnswerInTheChat() {
    setBorrowing(row(first))

    inCard(C.Tag.my_rentals_row_status)
        .assertTextEquals(string(R.string.my_rentals_badge_offer_received))
    inCard(C.Tag.my_rentals_row_offer).assertTextEquals("180 PP deposit offered · expires 22 Oct")
    inCard(C.Tag.my_rentals_row_person)
        .assertTextEquals(
            context.getString(
                R.string.my_rentals_person_and_dates,
                context.getString(R.string.my_rentals_from, "Loca"),
                "15 – 17 Oct",
            )
        )
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_open_chat_button).performClick()

    assertEquals(listOf("tent"), chats)
  }

  @Test
  fun aLenderWithACounterofferIsAskedToAnswer() {
    setLending(row(counter))

    inCard(C.Tag.my_rentals_row_status)
        .assertTextEquals(string(R.string.my_rentals_badge_counter_received))
    inCard(C.Tag.my_rentals_row_offer).assertTextEquals("120 PP counteroffer · expires 22 Oct")
  }

  @Test
  fun aBorrowerWhoCounteredWaits() {
    setBorrowing(row(counter, isMine = true))

    inCard(C.Tag.my_rentals_row_status)
        .assertTextEquals(string(R.string.my_rentals_badge_counter_sent))
  }

  @Test
  fun aLendingRequestWithoutAnOfferKeepsReviewRequest() {
    setLending(row(null))

    composeTestRule.onNodeWithTag(C.Tag.my_rentals_open_chat_button).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.my_rentals_review_request_button).performClick()

    assertEquals(1, reviews)
  }
}
