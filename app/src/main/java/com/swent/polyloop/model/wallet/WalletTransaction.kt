// Made with Claude.

package com.swent.polyloop.model.wallet

import java.time.Instant

/**
 * One line of the transaction history.
 *
 * @property id the identifier of the transaction, unique in the user's history.
 * @property type what kind of movement this is.
 * @property amount the change in PP, signed: positive when money comes in, negative when it goes
 *   out. Whole PP, never zero.
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
