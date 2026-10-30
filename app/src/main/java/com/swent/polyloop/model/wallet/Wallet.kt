// Made with Claude.

package com.swent.polyloop.model.wallet

/**
 * A user's PolyPoints balance. Only Cloud Functions change it; the app reads it and shows it.
 *
 * @property available the balance the user can spend, in whole PP (1 CHF = 1 PP). Never negative.
 * @property heldAsDeposits the part of the user's money currently held as deposits, in whole PP. It
 *   is not part of [available]. Never negative.
 */
data class Wallet(val available: Int = 0, val heldAsDeposits: Int = 0)
