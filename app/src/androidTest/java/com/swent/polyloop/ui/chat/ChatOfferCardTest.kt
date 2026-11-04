// Made with Claude.

package com.swent.polyloop.ui.chat

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.polyloop.R
import com.swent.polyloop.resources.C
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatOfferCardTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private val received =
      ChatOffer(
          amount = 180,
          previousAmount = null,
          isMine = false,
          isCounter = false,
          expiresAt = Instant.parse("2026-10-22T10:00:00Z"),
          overlappingRequests = 0,
      )
  private val counter = received.copy(amount = 120, previousAmount = 180, isCounter = true)
  private val mine = received.copy(isMine = true)

  private val base =
      ChatUiState(
          header =
              ChatHeader(
                  "Ana",
                  "Camping tent",
                  LocalDate.of(2026, 10, 20),
                  LocalDate.of(2026, 10, 22),
              ),
          isLoading = false,
      )

  private var accepts = 0
  private var declines = 0
  private var starts = 0
  private var cancels = 0
  private var sends = 0
  private var draft: String? = null

  private fun setCard(offer: ChatOffer, state: ChatUiState = base.copy(offer = offer)) {
    composeTestRule.setContent {
      ChatOfferCard(
          offer = offer,
          uiState = state,
          onAccept = { accepts++ },
          onDecline = { declines++ },
          onStartCounter = { starts++ },
          onCancelCounter = { cancels++ },
          onCounterDraftChange = { draft = it },
          onSendCounter = { sends++ },
          zone = ZoneOffset.UTC,
      )
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  @Test
  fun aFirstOfferShowsItsAmountExpiryAndThreeAnswers() {
    setCard(received)

    node(C.Tag.chat_offer_title).assertTextEquals(context.getString(R.string.chat_offer_title))
    node(C.Tag.chat_offer_badge)
        .assertTextEquals(context.getString(R.string.chat_offer_badge_answer))
    node(C.Tag.chat_offer_amount).assertTextEquals("180 PP")
    node(C.Tag.chat_offer_note).assertTextEquals("Expires 22 Oct.")
    node(C.Tag.chat_offer_accept).assertIsEnabled()
    node(C.Tag.chat_offer_decline).assertIsEnabled()
    node(C.Tag.chat_offer_counter).assertIsEnabled()
  }

  @Test
  fun theAnswersCallTheirActions() {
    setCard(received)

    node(C.Tag.chat_offer_accept).performClick()
    node(C.Tag.chat_offer_decline).performClick()
    node(C.Tag.chat_offer_counter).performClick()

    assertEquals(1, accepts)
    assertEquals(1, declines)
    assertEquals(1, starts)
  }

  @Test
  fun aCounterofferShowsThePreviousAmountAndNoCounterButton() {
    setCard(counter)

    node(C.Tag.chat_offer_title)
        .assertTextEquals(context.getString(R.string.chat_offer_title_counter))
    node(C.Tag.chat_offer_amount).assertTextEquals("120 PP")
    node(C.Tag.chat_offer_note).assertTextEquals("Your offer was 180 PP. Expires 22 Oct.")
    node(C.Tag.chat_offer_counter).assertDoesNotExist()
    node(C.Tag.chat_offer_accept).assertIsEnabled()
  }

  @Test
  fun theWarningShowsOnlyWhenRequestsWouldBeDeclined() {
    setCard(counter.copy(overlappingRequests = 1))

    node(C.Tag.chat_offer_overlapping)
        .assertTextEquals(
            context.resources.getQuantityString(R.plurals.chat_offer_overlapping, 1, 1)
        )
  }

  @Test
  fun noWarningWithoutOverlappingRequests() {
    setCard(counter)

    node(C.Tag.chat_offer_overlapping).assertDoesNotExist()
  }

  @Test
  fun myOwnOfferWaitsWithNoAction() {
    setCard(mine)

    node(C.Tag.chat_offer_title).assertTextEquals(context.getString(R.string.chat_offer_title_mine))
    node(C.Tag.chat_offer_badge)
        .assertTextEquals(context.getString(R.string.chat_offer_badge_waiting))
    node(C.Tag.chat_offer_amount).assertTextEquals("180 PP deposit")
    node(C.Tag.chat_offer_note).assertTextEquals("Waiting for Ana. Expires 22 Oct.")
    node(C.Tag.chat_offer_accept).assertDoesNotExist()
    node(C.Tag.chat_offer_decline).assertDoesNotExist()
    node(C.Tag.chat_offer_counter).assertDoesNotExist()
  }

  @Test
  fun myOwnCounterofferHasItsOwnTitle() {
    setCard(counter.copy(isMine = true))

    node(C.Tag.chat_offer_title)
        .assertTextEquals(context.getString(R.string.chat_offer_title_my_counter))
  }

  @Test
  fun aBlankNameShowsThePlaceholder() {
    setCard(mine, base.copy(offer = mine, header = base.header?.copy(otherPersonName = "")))

    node(C.Tag.chat_offer_note)
        .assertTextContains(context.getString(R.string.chat_unknown_person), substring = true)
  }

  @Test
  fun theAnswersAreDisabledOffline() {
    setCard(received, base.copy(offer = received, isOffline = true))

    node(C.Tag.chat_offer_accept).assertIsNotEnabled()
    node(C.Tag.chat_offer_decline).assertIsNotEnabled()
    node(C.Tag.chat_offer_counter).assertIsNotEnabled()
  }

  @Test
  fun theFormReportsTheAmountAndCallsItsActions() {
    setCard(
        received,
        base.copy(offer = received, isCountering = true, counterDraft = "120"),
    )

    node(C.Tag.chat_offer_note)
        .assertTextEquals(context.getString(R.string.chat_offer_counter_hint, 180))
    node(C.Tag.chat_offer_counter_field).performTextInput("5")
    node(C.Tag.chat_offer_counter_send).performClick()
    node(C.Tag.chat_offer_counter_back).performClick()

    assertEquals("5120", draft)
    assertEquals(1, sends)
    assertEquals(1, cancels)
  }

  @Test
  fun sendIsDisabledWithoutAValidAmount() {
    setCard(received, base.copy(offer = received, isCountering = true))

    node(C.Tag.chat_offer_counter_send).assertIsNotEnabled()
    node(C.Tag.chat_offer_counter_back).assertIsEnabled()
  }

  @Test
  fun theFormIsLockedWhileItIsSent() {
    setCard(
        received,
        base.copy(offer = received, isCountering = true, counterDraft = "120", isAnswering = true),
    )

    node(C.Tag.chat_offer_counter_send).assertIsNotEnabled()
    node(C.Tag.chat_offer_counter_back).assertIsNotEnabled()
    node(C.Tag.chat_offer_counter_field).assertIsNotEnabled()
  }

  @Test
  fun showsTheErrorOrTheGenericText() {
    setCard(received, base.copy(offer = received, offerError = ""))

    node(C.Tag.chat_offer_error).assertTextEquals(context.getString(R.string.chat_offer_error))
  }

  @Test
  fun theChatShowsTheCardAboveTheBarOnlyWithAnOffer() {
    composeTestRule.setContent {
      ChatContent(
          uiState = base.copy(offer = received),
          onDraftChange = {},
          onSend = {},
          onRetry = {},
          onRentalDetailsClick = {},
          onAcceptOffer = { accepts++ },
      )
    }

    node(C.Tag.chat_input).assertIsEnabled()
    node(C.Tag.chat_offer_accept).performClick()

    assertEquals(1, accepts)
  }

  @Test
  fun theChatHasNoCardWithoutAnOffer() {
    composeTestRule.setContent {
      ChatContent(
          uiState = base,
          onDraftChange = {},
          onSend = {},
          onRetry = {},
          onRentalDetailsClick = {},
      )
    }

    node(C.Tag.chat_offer_card).assertDoesNotExist()
  }
}
