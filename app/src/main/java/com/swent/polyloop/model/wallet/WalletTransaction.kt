// Made with Claude.

package com.swent.polyloop.model.wallet

import java.time.Instant

/**
 * One line of the transaction history.
 *
 * @property id the identifier of the transaction, unique in the user's history.
 * @property type what kind of movement this is.
 * @property amount the change to the user's available balance in PP, signed: positive when it goes
 *   up, negative when it goes down. Whole PP, never zero. Starting from an empty wallet, a user's
 *   amounts add up to [Wallet.available] (not to the total, and not to [Wallet.reserved]).
 * @property itemTitle the title of the rented item this movement is about; "" for a recharge, and
 *   the screen then shows nothing.
 * @property date when the movement happened.
 */
data class WalletTransaction(
    val id: String,
    val type: WalletTransactionType,
    val amount: Int,
    val itemTitle: String,
    val date: Instant,
)
