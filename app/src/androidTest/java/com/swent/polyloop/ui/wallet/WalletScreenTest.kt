// Made with Claude.

package com.swent.polyloop.ui.wallet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.wallet.FakeWalletRepository
import com.swent.polyloop.model.wallet.Wallet
import com.swent.polyloop.model.wallet.WalletTransaction
import com.swent.polyloop.model.wallet.WalletTransactionType
import com.swent.polyloop.resources.C
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val today = LocalDate.of(2026, 10, 30)

  // Noon UTC is the same calendar day in every time zone from UTC-11 to UTC+11.
  private val threeOct = Instant.parse("2026-10-03T12:00:00Z")

  private fun tx(
      id: String,
      type: WalletTransactionType,
      amount: Int,
      item: String = "",
      date: Instant = threeOct,
  ) = WalletTransaction(id, type, amount, item, date)

  private fun state(
      wallet: Wallet = Wallet(available = 120, heldAsDeposits = 150),
      history: List<WalletTransaction> = emptyList(),
      isLoading: Boolean = false,
      errorMsg: String? = null,
      isOffline: Boolean = false,
  ) =
      WalletUiState(
          wallet = wallet,
          history = history,
          isLoading = isLoading,
          errorMsg = errorMsg,
          isOffline = isOffline,
      )

  private fun setContent(
      state: WalletUiState,
      onRetry: () -> Unit = {},
      onRecharge: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      WalletContent(uiState = state, onRetry = onRetry, onRecharge = onRecharge, today = today)
    }
  }

  private fun inRow(id: String, tag: String) =
      composeTestRule.onNode(
          hasTestTag(tag) and hasAnyAncestor(hasTestTag(C.Tag.wallet_row_ + id)),
          useUnmergedTree = true,
      )

  @Test
  fun availableBalanceAndHeldAsDepositsAreShown() {
    setContent(state())

    composeTestRule.onNodeWithTag(C.Tag.wallet_available).assertTextEquals("120")
    composeTestRule.onNodeWithTag(C.Tag.wallet_held_deposits).assertTextEquals("150 PP")
  }

  @Test
  fun heldForRentalsIsHiddenWhenItIsZero() {
    setContent(state())

    composeTestRule.onNodeWithTag(C.Tag.wallet_held_rentals).assertDoesNotExist()
  }

  @Test
  fun heldForRentalsShowsItsAmountWhenAboveZero() {
    setContent(state(wallet = Wallet(available = 120, heldAsDeposits = 150, heldForRentals = 30)))

    composeTestRule.onNodeWithTag(C.Tag.wallet_held_rentals).assertTextEquals("30 PP")
  }

  @Test
  fun rechargeRowShowsItsDateInsteadOfAnItem() {
    setContent(state(history = listOf(tx("1", WalletTransactionType.RECHARGE, 50))))

    inRow("1", C.Tag.wallet_row_title).assertTextEquals("Recharge · 3 Oct")
  }

  @Test
  fun rowWithAnItemShowsTheItemTitle() {
    setContent(
        state(history = listOf(tx("1", WalletTransactionType.DEPOSIT_HELD, -150, "Camping tent")))
    )

    inRow("1", C.Tag.wallet_row_title).assertTextEquals("Deposit held · Camping tent")
  }

  @Test
  fun aDateOfAnotherYearShowsTheYear() {
    val lastYear = Instant.parse("2025-10-03T12:00:00Z")
    setContent(
        state(history = listOf(tx("1", WalletTransactionType.RECHARGE, 50, date = lastYear)))
    )

    inRow("1", C.Tag.wallet_row_title).assertTextEquals("Recharge · 3 Oct 2025")
  }

  @Test
  fun everyTransactionTypeHasItsOwnLabel() {
    val labels =
        mapOf(
            WalletTransactionType.RECHARGE to "Recharge",
            WalletTransactionType.RENTAL_PAYMENT to "Rental payment",
            WalletTransactionType.LATE_FEE to "Late fee received",
            WalletTransactionType.DEPOSIT_HELD to "Deposit held",
            WalletTransactionType.DEPOSIT_RELEASED to "Deposit released",
            WalletTransactionType.DEPOSIT_SPLIT to "Deposit split",
            WalletTransactionType.DEPOSIT_FORFEITED to "Lost item deposit received",
        )
    assertEquals(WalletTransactionType.entries.toSet(), labels.keys)
    setContent(state(history = labels.keys.map { tx(it.name, it, 10, item = "Drill") }))

    labels.forEach { (type, label) ->
      inRow(type.name, C.Tag.wallet_row_title).assertTextEquals("$label · Drill")
    }
  }

  @Test
  fun positiveAmountHasAPlusSignAndNegativeARealMinusSign() {
    setContent(
        state(
            history =
                listOf(
                    tx("1", WalletTransactionType.RECHARGE, 50),
                    tx("2", WalletTransactionType.RENTAL_PAYMENT, -45, "Camping tent"),
                )
        )
    )

    inRow("1", C.Tag.wallet_row_amount).assertTextEquals("+50 PP")
    inRow("2", C.Tag.wallet_row_amount).assertTextEquals("−45 PP")
  }

  @Test
  fun emptyHistoryShowsTheEmptyText() {
    setContent(state())

    composeTestRule
        .onNodeWithTag(C.Tag.wallet_history_empty)
        .assertTextEquals("No transactions yet.")
  }

  @Test
  fun loadingShowsASpinnerInsteadOfTheWallet() {
    setContent(state(isLoading = true))

    composeTestRule.onNodeWithTag(C.Tag.wallet_loading).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.wallet_available).assertDoesNotExist()
  }

  @Test
  fun reloadingKeepsTheHistoryAndShowsTheProgressBar() {
    setContent(
        state(isLoading = true, history = listOf(tx("1", WalletTransactionType.RECHARGE, 50)))
    )

    composeTestRule.onNodeWithTag(C.Tag.wallet_loading).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.wallet_row_ + "1").assertIsDisplayed()
  }

  @Test
  fun aBlankErrorShowsTheGenericTextAndHidesTheBalance() {
    setContent(state(errorMsg = ""))

    composeTestRule.onNodeWithTag(C.Tag.wallet_error).assertTextEquals("Could not load your wallet")
    composeTestRule.onNodeWithTag(C.Tag.wallet_available).assertDoesNotExist()
  }

  @Test
  fun retryButtonCallsOnRetry() {
    var retried = 0
    setContent(state(errorMsg = ""), onRetry = { retried++ })

    composeTestRule.onNodeWithTag(C.Tag.wallet_retry_button).performClick()

    assertEquals(1, retried)
  }

  @Test
  fun theHistoryStaysUnderAnError() {
    setContent(state(errorMsg = "", history = listOf(tx("1", WalletTransactionType.RECHARGE, 50))))

    composeTestRule.onNodeWithTag(C.Tag.wallet_error).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.wallet_row_ + "1").assertIsDisplayed()
  }

  @Test
  fun rechargeIsEnabledOnlineAndCallsOnRecharge() {
    var recharged = 0
    setContent(state(), onRecharge = { recharged++ })

    composeTestRule.onNodeWithTag(C.Tag.wallet_recharge_button).assertIsEnabled().performClick()

    assertEquals(1, recharged)
  }

  @Test
  fun rechargeIsDisabledOffline() {
    setContent(state(isOffline = true))

    composeTestRule.onNodeWithTag(C.Tag.wallet_recharge_button).assertIsNotEnabled()
  }

  @Test
  fun theWalletAndHistoryStayReadableOfflineUnderTheBanner() {
    setContent(
        state(isOffline = true, history = listOf(tx("1", WalletTransactionType.RECHARGE, 50)))
    )

    composeTestRule.onNodeWithTag(C.Tag.offline_banner).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.wallet_available).assertTextEquals("120")
    composeTestRule.onNodeWithTag(C.Tag.wallet_row_ + "1").assertIsDisplayed()
  }

  @Test
  fun noBannerIsShownOnline() {
    setContent(state())

    composeTestRule.onNodeWithTag(C.Tag.offline_banner).assertDoesNotExist()
  }

  @Test
  fun footerExplainsThePolyPointRate() {
    setContent(state())

    composeTestRule
        .onNodeWithTag(C.Tag.wallet_footer)
        .assertTextEquals("1 CHF = 1 PolyPoint. PolyPoints can't be withdrawn.")
  }

  @Test
  fun walletScreenShowsTheStateOfItsViewModelAndForwardsRecharge() {
    var recharged = false
    val viewModel =
        WalletViewModel(
            FakeWalletRepository(wallet = Wallet(available = 50)),
            FakeAuthRepository(signedInUser = AuthUser("me", "me@epfl.ch", "Me")),
            FakeConnectivityRepository(),
        )
    composeTestRule.setContent { WalletScreen(viewModel, onRecharge = { recharged = true }) }
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag(C.Tag.wallet_available).fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag(C.Tag.wallet_available).assertTextEquals("50")
    composeTestRule.onNodeWithTag(C.Tag.wallet_recharge_button).performClick()

    assertTrue(recharged)
  }
}
