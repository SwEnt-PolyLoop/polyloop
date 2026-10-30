// Made with Claude.

package com.swent.polyloop.model.wallet

/** The kinds of line in the transaction history of the Wallet screen. */
enum class WalletTransactionType {
  /** The user added PP by recharging (simulated payment). The amount is positive. */
  RECHARGE,

  /** The user paid, or got back, the price of a rental. The amount is negative when paying. */
  RENTAL_PAYMENT,

  /** A late-return fee was taken from a deposit and paid to the lender. */
  LATE_FEE,

  /** A deposit was reserved from the balance. The amount is negative. */
  DEPOSIT_HELD,

  /** A deposit was released back to the balance. The amount is positive. */
  DEPOSIT_RELEASED,

  /** A deposit was split between the two parties after a dispute. */
  DEPOSIT_SPLIT,
}
