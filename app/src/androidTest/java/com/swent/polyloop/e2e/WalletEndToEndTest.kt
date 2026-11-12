// Made with Claude.

package com.swent.polyloop.e2e

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.polyloop.R
import com.swent.polyloop.model.wallet.WalletFields
import com.swent.polyloop.model.wallet.WalletTransactionType
import com.swent.polyloop.resources.C
import com.swent.polyloop.ui.navigation.Screen
import com.swent.polyloop.utils.FirebaseEmulator
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The Wallet screen reading the balance and the history the Cloud Functions write. */
@RunWith(AndroidJUnit4::class)
class WalletEndToEndTest : EndToEndTestBase() {

  @Test
  fun walletShowsTheBalanceAndTheHistoryNewestFirst() {
    val uid = signInWithoutTheUi()
    seedWalletWithHistory(uid)
    setApp()

    openFromMenu(Screen.Wallet)
    waitForText(C.Tag.wallet_available, AVAILABLE.toString())
    composeTestRule
        .onNodeWithTag(C.Tag.wallet_held_deposits)
        .assertTextEquals(context.getString(R.string.wallet_pp_amount, DEPOSIT))
    composeTestRule
        .onNodeWithTag(C.Tag.wallet_held_rentals)
        .assertTextEquals(context.getString(R.string.wallet_pp_amount, HELD_FOR_RENTALS))
    composeTestRule.onNodeWithTag(C.Tag.wallet_recharge_button).assertIsEnabled()

    // Each row names its type and its item, or its date for a recharge, and its signed amount.
    val deposit = C.Tag.wallet_row_ + DEPOSIT_ROW
    val recharge = C.Tag.wallet_row_ + RECHARGE_ROW
    waitForTag(deposit)
    inNode(deposit, C.Tag.wallet_row_title)
        .assertTextEquals(
            context.getString(
                R.string.wallet_row_title,
                context.getString(R.string.wallet_type_deposit_held),
                ITEM,
            )
        )
    inNode(deposit, C.Tag.wallet_row_amount)
        .assertTextEquals(context.getString(R.string.wallet_amount_negative, DEPOSIT))
    inNode(recharge, C.Tag.wallet_row_amount)
        .assertTextEquals(context.getString(R.string.wallet_amount_positive, RECHARGE))
    // Newest first: the deposit, held after the recharge, comes above it.
    val depositTop = composeTestRule.onNodeWithTag(deposit).getUnclippedBoundsInRoot().top
    val rechargeTop = composeTestRule.onNodeWithTag(recharge).getUnclippedBoundsInRoot().top
    assertTrue("The newest row is not first", depositTop < rechargeTop)
  }

  /** The wallet of [uid] after a recharge and a deposit held, as the Cloud Functions write it. */
  private fun seedWalletWithHistory(uid: String) {
    val walletPath =
        seedWallet(
            uid,
            AVAILABLE,
            heldAsDeposits = DEPOSIT,
            heldForRentals = HELD_FOR_RENTALS,
        )
    val now = Instant.now()
    seedTransaction(
        "$walletPath/${WalletFields.TRANSACTIONS}/$RECHARGE_ROW",
        WalletTransactionType.RECHARGE,
        RECHARGE,
        "",
        now.minus(Duration.ofDays(2)),
    )
    seedTransaction(
        "$walletPath/${WalletFields.TRANSACTIONS}/$DEPOSIT_ROW",
        WalletTransactionType.DEPOSIT_HELD,
        -DEPOSIT,
        ITEM,
        now.minus(Duration.ofDays(1)),
    )
  }

  private fun seedTransaction(
      path: String,
      type: WalletTransactionType,
      amount: Int,
      itemTitle: String,
      date: Instant,
  ) {
    FirebaseEmulator.createDocumentAsAdmin(
        path,
        mapOf(
            WalletFields.TYPE to type.name,
            WalletFields.AMOUNT to amount,
            WalletFields.ITEM_TITLE to itemTitle,
            WalletFields.DATE to timestamp(date),
        ),
    )
    trackDocument(path)
  }

  private companion object {
    const val RECHARGE = 125
    const val DEPOSIT = 40
    const val HELD_FOR_RENTALS = 10
    // What the recharge left after the deposit and the rental price were held.
    const val AVAILABLE = RECHARGE - DEPOSIT - HELD_FOR_RENTALS
    const val ITEM = "Camping tent"
    const val RECHARGE_ROW = "recharge"
    const val DEPOSIT_ROW = "deposit_held"
  }
}
