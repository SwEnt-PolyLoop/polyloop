// Made with Claude.

package com.swent.polyloop.model.wallet

import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeWalletRepositoryTest {

  private val now = Instant.parse("2026-10-30T10:00:00Z")
  private val earlier = Instant.parse("2026-10-01T10:00:00Z")

  private val deposit =
      WalletTransaction("tx-1", WalletTransactionType.DEPOSIT_HELD, -20, "Tent", earlier)

  private fun repository(
      wallet: Wallet = Wallet(available = 50, heldAsDeposits = 20),
      history: List<WalletTransaction> = listOf(deposit),
  ) = FakeWalletRepository(wallet = wallet, history = history, clock = { now })

  @Test
  fun observeWalletEmitsTheInitialWallet() = runTest {
    assertEquals(Wallet(50, 20), repository().observeWallet("me").first())
  }

  @Test
  fun observeHistoryEmitsTheInitialHistoryInTheOrderGiven() = runTest {
    val older = deposit.copy(id = "tx-2", date = earlier.minusSeconds(60))
    val repository = repository(history = listOf(deposit, older))

    assertEquals(listOf(deposit, older), repository.observeHistory("me").first())
  }

  @Test
  fun rechargeAddsTheAmountToTheAvailableBalanceAndReturnsTheNewWallet() = runTest {
    val repository = repository()

    val result = repository.recharge("me", 30)

    assertEquals(Wallet(available = 80, heldAsDeposits = 20), result.getOrNull())
    assertEquals(Wallet(available = 80, heldAsDeposits = 20), repository.wallet)
  }

  @Test
  fun rechargeAppendsARechargeTransactionWithAPositiveAmountAndNoItemTitle() = runTest {
    val repository = repository()

    repository.recharge("me", 30)

    assertEquals(
        listOf(
            deposit,
            WalletTransaction("tx-2", WalletTransactionType.RECHARGE, 30, "", now),
        ),
        repository.history,
    )
  }

  @Test
  fun rechargeGivesIdsThatDoNotClashWithTheInitialHistory() = runTest {
    val repository =
        repository(history = listOf(deposit.copy(id = "tx-1"), deposit.copy(id = "tx-2")))

    repository.recharge("me", 5)

    assertEquals("tx-3", repository.history.last().id)
  }

  @Test
  fun rechargesListsEveryUidAndAmountSentInOrder() = runTest {
    val repository = repository()

    repository.recharge("me", 10)
    repository.recharge("you", 25)

    assertEquals(listOf("me" to 10, "you" to 25), repository.recharges)
  }

  @Test
  fun observedUidsListsTheUidOfEveryObserveCallInOrder() {
    val repository = repository()

    repository.observeWallet("me")
    repository.observeHistory("you")

    assertEquals(listOf("me", "you"), repository.observedUids)
  }

  @Test
  fun observedUidsIsEmptyWhenNothingIsObserved() = runTest {
    val repository = repository()

    repository.recharge("me", 10)

    assertTrue(repository.observedUids.isEmpty())
  }

  @Test
  fun observeWalletEmitsAgainAfterARecharge() =
      runTest(UnconfinedTestDispatcher()) {
        val repository = repository()
        val emitted = mutableListOf<Wallet>()
        backgroundScope.launch { repository.observeWallet("me").collect { emitted += it } }

        repository.recharge("me", 30)

        assertEquals(listOf(Wallet(50, 20), Wallet(80, 20)), emitted)
      }

  @Test
  fun observeHistoryEmitsAgainAfterARecharge() =
      runTest(UnconfinedTestDispatcher()) {
        val repository = repository()
        val emitted = mutableListOf<List<WalletTransaction>>()
        backgroundScope.launch { repository.observeHistory("me").collect { emitted += it } }

        repository.recharge("me", 30)

        assertEquals(listOf(1, 2), emitted.map { it.size })
      }

  @Test
  fun emitReplacesTheWalletAndHistoryAndTheObserversSeeThem() =
      runTest(UnconfinedTestDispatcher()) {
        val repository = repository()
        val wallets = mutableListOf<Wallet>()
        val histories = mutableListOf<List<WalletTransaction>>()
        backgroundScope.launch { repository.observeWallet("me").collect { wallets += it } }
        backgroundScope.launch { repository.observeHistory("me").collect { histories += it } }
        val released = deposit.copy(id = "tx-9", type = WalletTransactionType.DEPOSIT_RELEASED)

        repository.emit(Wallet(70, 0), listOf(deposit, released))

        assertEquals(Wallet(70, 0), wallets.last())
        assertEquals(listOf(deposit, released), histories.last())
        assertEquals(Wallet(70, 0), repository.wallet)
        assertEquals(listOf(deposit, released), repository.history)
      }

  @Test
  fun emitRecordsNoRecharge() = runTest {
    val repository = repository()

    repository.emit(Wallet(70, 0), emptyList())

    assertTrue(repository.recharges.isEmpty())
  }

  @Test
  fun rechargeAfterEmitBuildsOnTheEmittedWallet() = runTest {
    val repository = repository()
    repository.emit(Wallet(70, 0), emptyList())

    val result = repository.recharge("me", 10)

    assertEquals(Wallet(80, 0), result.getOrNull())
  }

  @Test
  fun rechargeFailsWithTheFailureAndChangesNothing() = runTest {
    val error = IllegalStateException("offline")
    val repository = repository()
    repository.failure = error

    val result = repository.recharge("me", 30)

    assertEquals(error, result.exceptionOrNull())
    assertEquals(Wallet(50, 20), repository.wallet)
    assertEquals(listOf(deposit), repository.history)
  }

  @Test
  fun rechargesStillRecordsTheCallOfAFailedRecharge() = runTest {
    val repository = repository()
    repository.failure = IllegalStateException("offline")

    repository.recharge("me", 30)

    assertEquals(listOf("me" to 30), repository.recharges)
  }

  @Test
  fun rechargeOfLessThanOneChfFailsAndChangesNothing() = runTest {
    val repository = repository()

    val result = repository.recharge("me", 0)

    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    assertEquals(Wallet(50, 20), repository.wallet)
    assertEquals(listOf(deposit), repository.history)
  }

  @Test
  fun rechargeWithABlankUidFailsAndChangesNothing() = runTest {
    val repository = repository()

    val result = repository.recharge(" ", 30)

    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    assertEquals(Wallet(50, 20), repository.wallet)
    assertEquals(listOf(deposit), repository.history)
  }

  @Test
  fun rechargesRecordsTheCallOfARechargeWithABlankUid() = runTest {
    val repository = repository()

    repository.recharge("", 30)

    assertEquals(listOf("" to 30), repository.recharges)
  }

  @Test
  fun observeWalletThrowsForABlankUid() = runTest {
    val thrown = runCatching { repository().observeWallet("").first() }.exceptionOrNull()

    assertTrue(thrown is IllegalArgumentException)
  }

  @Test
  fun observeHistoryThrowsForABlankUid() = runTest {
    val thrown = runCatching { repository().observeHistory(" ").first() }.exceptionOrNull()

    assertTrue(thrown is IllegalArgumentException)
  }

  @Test
  fun rechargeWaitsForTheGateBeforeChangingTheWallet() =
      runTest(UnconfinedTestDispatcher()) {
        val gate = CompletableDeferred<Unit>()
        val repository = repository()
        repository.gate = gate

        val pending = async { repository.recharge("me", 30) }

        assertTrue(pending.isActive)
        assertEquals(Wallet(50, 20), repository.wallet)
        assertEquals(listOf("me" to 30), repository.recharges)

        gate.complete(Unit)

        assertEquals(Wallet(80, 20), pending.await().getOrNull())
      }

  @Test
  fun observeWalletThrowsTheFailureWhenItIsSet() = runTest {
    val error = IllegalStateException("unreadable")
    val repository = repository()
    repository.failure = error

    val thrown = runCatching { repository.observeWallet("me").first() }.exceptionOrNull()

    assertEquals(error, thrown)
  }

  @Test
  fun observeHistoryThrowsTheFailureWhenItIsSet() = runTest {
    val error = IllegalStateException("unreadable")
    val repository = repository()
    repository.failure = error

    val thrown = runCatching { repository.observeHistory("me").first() }.exceptionOrNull()

    assertEquals(error, thrown)
  }
}
