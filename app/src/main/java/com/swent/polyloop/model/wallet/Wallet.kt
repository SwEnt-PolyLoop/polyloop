// Made with Claude.

package com.swent.polyloop.model.wallet

/**
 * A user's PolyPoints balance. Only Cloud Functions change it; the app reads it and shows it.
 *
 * The user's total is [available] + [reserved]; [available] never includes anything held.
 *
 * @property available the balance the user can spend, in whole PP (1 CHF = 1 PP). It excludes
 *   everything held. Never negative.
 * @property heldAsDeposits the deposits held, in whole PP: each is entered by the lender at
 *   acceptance and goes back to the borrower unless the item is damaged or lost. Never negative.
 * @property heldForRentals the rental prices reserved, in whole PP, from the moment a request is
 *   sent. It is the price only: the deposit is not known until the lender accepts. It stays held
 *   until it is paid to the lender or returned to [available]. Never negative.
 * @property reserved everything held, [heldAsDeposits] + [heldForRentals], in whole PP. Computed,
 *   so it is not stored in Firestore. Never negative.
 */
data class Wallet(
    val available: Int = 0,
    val heldAsDeposits: Int = 0,
    val heldForRentals: Int = 0,
) {
  val reserved: Int
    get() = heldAsDeposits + heldForRentals
}
