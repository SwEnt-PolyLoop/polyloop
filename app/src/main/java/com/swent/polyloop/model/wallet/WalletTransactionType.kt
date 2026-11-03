// Made with Claude.

package com.swent.polyloop.model.wallet

/** The kinds of line in the transaction history of the Wallet screen. */
enum class WalletTransactionType {
  /** The user added PP by recharging (simulated payment). The amount is positive. */
  RECHARGE,

  /** The user paid, or got back, the price of a rental. The amount is negative when paying. */
  RENTAL_PAYMENT,

  /**
   * A late-return fee was taken from the held deposit and paid to the lender. It is recorded only
   * for the lender, with a positive amount; the borrower gets no line for it, because the fee comes
   * out of the held deposit.
   */
  LATE_FEE,

  /** A deposit was reserved from the balance. The amount is negative. */
  DEPOSIT_HELD,

  /**
   * A deposit was released back to the balance. The amount is positive and is only what remains of
   * the deposit after late fees.
   */
  DEPOSIT_RELEASED,

  /** A deposit was split between the two parties after a dispute. */
  DEPOSIT_SPLIT,

  /**
   * A deposit went to the lender because the item is Lost: what remained after the late fees (see
   * "Late returns" in ARCHITECTURE.md). It is recorded only for the lender, with a positive amount;
   * the borrower gets no line, because the deposit already left `available` when it was held.
   */
  DEPOSIT_FORFEITED,
}
