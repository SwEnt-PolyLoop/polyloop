// Made with Claude.

package com.swent.polyloop.ui.rentaldetail

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.model.rental.DepositOffer
import com.swent.polyloop.model.rental.RentalStatus
import com.swent.polyloop.resources.C
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The deposit offer on Rental detail (Figma frame 13.10). */
@RunWith(AndroidJUnit4::class)
class RentalDetailOfferTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private val expiresAt = Instant.parse("2026-10-22T12:00:00Z")
  private val first = DepositOffer(180, "a", false, null, expiresAt)
  private val counter = DepositOffer(120, "b", true, 180, expiresAt)

  private val requested =
      RentalDetailUiState(
          itemTitle = "Camping tent",
          status = RentalStatus.REQUESTED,
          sentAt = Instant.parse("2026-10-01T10:00:00Z"),
          startDate = LocalDate.of(2026, 10, 15),
          endDate = LocalDate.of(2026, 10, 17),
          rentalPrice = 45,
          suggestedDeposit = 200,
          pickupArea = "Ecublens",
      )

  private fun setContent(state: RentalDetailUiState) {
    composeTestRule.setContent {
      RentalDetailContent(
          uiState = state,
          onRetry = {},
          onChatClick = {},
          onPersonClick = {},
          onStartPickupClick = {},
          onViewDisputeClick = {},
          today = LocalDate.of(2026, 10, 1),
          zone = ZoneOffset.UTC,
          // English is fixed so the expected dates pass on an emulator in any language.
          locale = Locale.ENGLISH,
      )
    }
  }

  private fun assertCard(headline: Int, body: String) {
    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_status_headline, useUnmergedTree = true)
        .assertTextEquals(context.getString(headline))
    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_status_body, useUnmergedTree = true)
        .assertTextEquals(body)
  }

  @Test
  fun anOfferReceivedAsksForAnAnswerInTheChat() {
    setContent(requested.copy(offer = first))

    assertCard(
        R.string.rental_detail_status_offer_received,
        "Deposit offer: 180 PP. Expires 22 Oct. Answer in the chat.",
    )
  }

  @Test
  fun anOfferSentWaitsForTheAnswer() {
    setContent(requested.copy(offer = first, isOfferMine = true))

    assertCard(
        R.string.rental_detail_status_offer_sent,
        "Deposit offer: 180 PP. Expires 22 Oct. Waiting for the answer.",
    )
  }

  @Test
  fun aCounterofferReceivedAsksForAnAnswerInTheChat() {
    setContent(requested.copy(offer = counter))

    assertCard(
        R.string.rental_detail_status_counter_received,
        "Counteroffer: 120 PP. Expires 22 Oct. Answer in the chat.",
    )
  }

  @Test
  fun aCounterofferSentWaitsForTheAnswer() {
    setContent(requested.copy(offer = counter, isOfferMine = true))

    assertCard(
        R.string.rental_detail_status_counter_sent,
        "Counteroffer: 120 PP. Expires 22 Oct. Waiting for the answer.",
    )
  }

  @Test
  fun theOfferReplacesTheSuggestedDepositInTheDetails() {
    setContent(requested.copy(offer = first))

    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_offer, useUnmergedTree = true)
        .assertTextEquals("180 PP")
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_deposit).assertDoesNotExist()
  }

  @Test
  fun withoutAnOfferTheCardAndDetailsStayTheSame() {
    setContent(requested)

    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_status_headline, useUnmergedTree = true)
        .assertTextEquals(context.getString(R.string.rental_detail_status_headline))
    composeTestRule.onNodeWithTag(C.Tag.rental_detail_offer).assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(C.Tag.rental_detail_deposit, useUnmergedTree = true)
        .assertTextEquals("200 PP")
  }
}
