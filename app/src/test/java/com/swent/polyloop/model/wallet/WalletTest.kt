// Made with Claude.

package com.swent.polyloop.model.wallet

import org.junit.Assert.assertEquals
import org.junit.Test

class WalletTest {

  @Test
  fun reservedIsTheDepositsPlusTheRentals() {
    val wallet = Wallet(available = 50, heldAsDeposits = 20, heldForRentals = 15)

    assertEquals(35, wallet.reserved)
  }

  @Test
  fun heldForRentalsDefaultsToZeroWhenOmitted() {
    val wallet = Wallet(50, 20)

    assertEquals(0, wallet.heldForRentals)
    assertEquals(20, wallet.reserved)
  }

  @Test
  fun anEmptyWalletHasNothingAvailableOrReserved() {
    assertEquals(0, Wallet().available)
    assertEquals(0, Wallet().reserved)
  }

  @Test
  fun availablePlusReservedIsTheTotal() {
    val wallet = Wallet(available = 50, heldAsDeposits = 20, heldForRentals = 15)

    assertEquals(85, wallet.available + wallet.reserved)
  }
}
