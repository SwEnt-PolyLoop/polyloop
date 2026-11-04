// Made with Claude.

package com.swent.polyloop.ui.wallet

import androidx.lifecycle.ViewModel
import com.swent.polyloop.model.auth.AuthUser
import com.swent.polyloop.model.auth.FakeAuthRepository
import com.swent.polyloop.model.connectivity.FakeConnectivityRepository
import com.swent.polyloop.model.wallet.FakeWalletRepository
import com.swent.polyloop.model.wallet.Wallet
import com.swent.polyloop.model.wallet.WalletRepository
import com.swent.polyloop.model.wallet.WalletTransaction
import com.swent.polyloop.model.wallet.WalletTransactionType
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModelTest {

  private val me = AuthUser(uid = "me", email = "me@epfl.ch", name = "Me")
  private val noon = Instant.parse("2026-10-30T12:00:00Z")

  private fun tx(id: String, date: Instant, amount: Int = 10) =
      WalletTransaction(id, WalletTransactionType.RECHARGE, amount, "", date)

  private lateinit var wallets: FakeWalletRepository
  private lateinit var auth: FakeAuthRepository
  private lateinit var connectivity: FakeConnectivityRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    wallets = FakeWalletRepository(wallet = Wallet(available = 50, heldAsDeposits = 20))
    auth = FakeAuthRepository(signedInUser = me)
    connectivity = FakeConnectivityRepository(online = true)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel() = WalletViewModel(wallets, auth, connectivity)

  @Test
  fun initialStateIsLoadingWithAnEmptyWalletAndNoRecharge() {
    val state = WalletUiState()

    assertTrue(state.isLoading)
    assertEquals(Wallet(), state.wallet)
    assertTrue(state.history.isEmpty())
    assertNull(state.errorMsg)
    assertFalse(state.isOffline)
    assertEquals(RechargeStatus.IDLE, state.rechargeStatus)
    assertNull(state.rechargeAmount)
    assertNull(state.rechargeNewBalance)
  }

  @Test
  fun stateIsLoadingUntilTheWalletAndHistoryAreRead() {
    val dispatcher = StandardTestDispatcher()
    Dispatchers.setMain(dispatcher)
    runTest(dispatcher) {
      val viewModel = viewModel()

      assertTrue(viewModel.uiState.value.isLoading)

      runCurrent()

      assertFalse(viewModel.uiState.value.isLoading)
    }
  }

  @Test
  fun balanceAndHeldAmountsAreShown() {
    val wallet = viewModel().uiState.value.wallet

    assertEquals(50, wallet.available)
    assertEquals(20, wallet.heldAsDeposits)
    assertEquals(0, wallet.heldForRentals)
    assertEquals(20, wallet.reserved)
  }

  @Test
  fun historyIsNewestFirstWhateverOrderTheRepositoryEmitsIt() {
    val old = tx("a", noon.minusSeconds(120))
    val mid = tx("b", noon.minusSeconds(60))
    val new = tx("c", noon)
    wallets.emit(Wallet(), listOf(mid, new, old))

    assertEquals(listOf(new, mid, old), viewModel().uiState.value.history)
  }

  @Test
  fun transactionsWithTheSameDateAreOrderedByHighestIdFirst() {
    val first = tx("tx-1", noon)
    val second = tx("tx-2", noon)
    val third = tx("tx-3", noon)
    wallets.emit(Wallet(), listOf(second, third, first))

    assertEquals(listOf(third, second, first), viewModel().uiState.value.history)
  }

  @Test
  fun stateIsAnErrorWhenTheWalletCannotBeRead() {
    wallets.failure = IllegalStateException("unreadable")

    val state = viewModel().uiState.value

    assertFalse(state.isLoading)
    assertEquals("", state.errorMsg)
  }

  @Test
  fun stateIsAnErrorWhenNobodyIsSignedIn() {
    auth = FakeAuthRepository(signedInUser = null)

    val state = viewModel().uiState.value

    assertFalse(state.isLoading)
    assertEquals("", state.errorMsg)
    assertTrue(wallets.observedUids.isEmpty())
  }

  @Test
  fun retryLoadsTheWalletAgainAfterAnError() {
    wallets.failure = IllegalStateException("unreadable")
    val viewModel = viewModel()
    wallets.failure = null

    viewModel.retry()

    assertNull(viewModel.uiState.value.errorMsg)
    assertEquals(50, viewModel.uiState.value.wallet.available)
  }

  @Test
  fun theLastWalletAndHistoryAreKeptWhenALaterReadFails() {
    val kept = listOf(tx("tx-1", noon))
    wallets.emit(Wallet(available = 50, heldAsDeposits = 20), kept)
    val viewModel = viewModel()
    wallets.failure = IllegalStateException("unreadable")

    wallets.emit(Wallet(available = 999), listOf(tx("tx-2", noon)))

    val state = viewModel.uiState.value
    assertEquals("", state.errorMsg)
    assertFalse(state.isLoading)
    assertEquals(Wallet(available = 50, heldAsDeposits = 20), state.wallet)
    assertEquals(kept, state.history)
  }

  @Test
  fun theSignedInUsersUidIsTheOneTheRepositoryReceives() {
    val viewModel = viewModel()

    viewModel.recharge(10)

    assertEquals(listOf("me" to 10), wallets.recharges)
    assertEquals(listOf("me", "me"), wallets.observedUids)
  }

  @Test
  fun aValidRechargeUpdatesTheBalanceAndEndsDone() {
    val viewModel = viewModel()

    viewModel.recharge(30)

    val state = viewModel.uiState.value
    assertEquals(RechargeStatus.DONE, state.rechargeStatus)
    assertEquals(30, state.rechargeAmount)
    assertEquals(80, state.rechargeNewBalance)
    assertEquals(80, state.wallet.available)
  }

  @Test
  fun rechargeAddsTheNewTransactionAtTheTopOfTheHistory() {
    val viewModel = viewModel()

    viewModel.recharge(30)

    assertEquals(30, viewModel.uiState.value.history.first().amount)
  }

  @Test
  fun theMinimumAndMaximumAmountsAreAccepted() {
    val viewModel = viewModel()

    viewModel.recharge(WalletRepository.MIN_RECHARGE)
    assertEquals(RechargeStatus.DONE, viewModel.uiState.value.rechargeStatus)
    viewModel.clearRechargeResult()
    viewModel.recharge(WalletRepository.MAX_RECHARGE)

    assertEquals(RechargeStatus.DONE, viewModel.uiState.value.rechargeStatus)
    assertEquals(
        listOf("me" to WalletRepository.MIN_RECHARGE, "me" to WalletRepository.MAX_RECHARGE),
        wallets.recharges,
    )
  }

  @Test
  fun invalidAmountsNeverReachTheRepositoryAndEndInAnError() {
    val invalid =
        listOf(0, -1, WalletRepository.MIN_RECHARGE - 1, WalletRepository.MAX_RECHARGE + 1)

    invalid.forEach { amount ->
      val viewModel = viewModel()

      viewModel.recharge(amount)

      val state = viewModel.uiState.value
      assertEquals(RechargeStatus.ERROR, state.rechargeStatus)
      assertEquals(amount, state.rechargeAmount)
      assertNull(state.rechargeNewBalance)
    }
    assertTrue(wallets.recharges.isEmpty())
    assertEquals(50, wallets.wallet.available)
  }

  @Test
  fun rechargeWithNobodySignedInSendsNothingAndEndsInAnError() {
    auth = FakeAuthRepository(signedInUser = null)
    val viewModel = viewModel()

    viewModel.recharge(10)

    assertEquals(RechargeStatus.ERROR, viewModel.uiState.value.rechargeStatus)
    assertTrue(wallets.recharges.isEmpty())
  }

  @Test
  fun aRepositoryFailureGivesAGenericErrorAndKeepsTheBalance() {
    val viewModel = viewModel()
    wallets.failure = IllegalStateException("secret server detail")

    viewModel.recharge(30)

    val state = viewModel.uiState.value
    assertEquals(RechargeStatus.ERROR, state.rechargeStatus)
    assertEquals(30, state.rechargeAmount)
    assertNull(state.rechargeNewBalance)
    assertNull(state.errorMsg)
    assertEquals(50, state.wallet.available)
    assertFalse(state.toString().contains("secret server detail"))
  }

  @Test
  fun aSecondTapWhileSendingIsIgnoredAndOnlyOneRechargeIsSent() {
    val gate = CompletableDeferred<Unit>()
    wallets.gate = gate
    val viewModel = viewModel()

    viewModel.recharge(10)
    assertEquals(RechargeStatus.SENDING, viewModel.uiState.value.rechargeStatus)
    viewModel.recharge(10)
    viewModel.recharge(99)

    assertEquals(listOf("me" to 10), wallets.recharges)
    assertEquals(10, viewModel.uiState.value.rechargeAmount)

    gate.complete(Unit)

    assertEquals(RechargeStatus.DONE, viewModel.uiState.value.rechargeStatus)
    assertEquals(60, wallets.wallet.available)
    assertEquals(1, wallets.recharges.size)
  }

  @Test
  fun anInvalidAmountWhileSendingDoesNotDisturbTheRechargeInProgress() {
    wallets.gate = CompletableDeferred()
    val viewModel = viewModel()
    viewModel.recharge(10)

    viewModel.recharge(0)

    assertEquals(RechargeStatus.SENDING, viewModel.uiState.value.rechargeStatus)
    assertEquals(10, viewModel.uiState.value.rechargeAmount)
  }

  @Test
  fun clearRechargeResultHidesADoneResult() {
    val viewModel = viewModel()
    viewModel.recharge(10)

    viewModel.clearRechargeResult()

    assertEquals(RechargeStatus.IDLE, viewModel.uiState.value.rechargeStatus)
  }

  @Test
  fun clearRechargeResultHidesAnErrorResult() {
    val viewModel = viewModel()
    viewModel.recharge(0)

    viewModel.clearRechargeResult()

    assertEquals(RechargeStatus.IDLE, viewModel.uiState.value.rechargeStatus)
  }

  @Test
  fun clearRechargeResultDoesNotCancelARechargeInProgress() {
    wallets.gate = CompletableDeferred()
    val viewModel = viewModel()
    viewModel.recharge(10)

    viewModel.clearRechargeResult()

    assertEquals(RechargeStatus.SENDING, viewModel.uiState.value.rechargeStatus)
  }

  @Test
  fun aNewRechargeAfterAnErrorStartsFromAFreshState() {
    val viewModel = viewModel()
    viewModel.recharge(0)

    viewModel.recharge(10)

    val state = viewModel.uiState.value
    assertEquals(RechargeStatus.DONE, state.rechargeStatus)
    assertEquals(10, state.rechargeAmount)
    assertEquals(60, state.rechargeNewBalance)
  }

  @Test
  fun isOfflineFollowsTheConnectivity() {
    val viewModel = viewModel()
    assertFalse(viewModel.uiState.value.isOffline)

    connectivity.setOnline(false)
    assertTrue(viewModel.uiState.value.isOffline)

    connectivity.setOnline(true)
    assertFalse(viewModel.uiState.value.isOffline)
  }

  @Test
  fun startingOfflineShowsTheOfflineState() {
    connectivity.setOnline(false)

    assertTrue(viewModel().uiState.value.isOffline)
  }

  @Test
  fun retryWhileOfflineKeepsTheOfflineStateAndStillFollowsTheConnectivity() {
    val viewModel = viewModel()
    connectivity.setOnline(false)

    viewModel.retry()

    assertTrue(viewModel.uiState.value.isOffline)

    connectivity.setOnline(true)

    assertFalse(viewModel.uiState.value.isOffline)

    connectivity.setOnline(false)
    viewModel.retry()
    viewModel.retry()

    assertTrue(viewModel.uiState.value.isOffline)
    assertEquals(50, viewModel.uiState.value.wallet.available)
  }

  @Test
  fun rechargeWhileOfflineSendsNothingAndEndsInAnError() {
    val viewModel = viewModel()
    connectivity.setOnline(false)

    viewModel.recharge(30)

    val state = viewModel.uiState.value
    assertEquals(RechargeStatus.ERROR, state.rechargeStatus)
    assertEquals(30, state.rechargeAmount)
    assertNull(state.rechargeNewBalance)
    assertTrue(wallets.recharges.isEmpty())
    assertEquals(50, state.wallet.available)
  }

  @Test
  fun rechargeWorksAgainAfterComingBackOnline() {
    val viewModel = viewModel()
    connectivity.setOnline(false)
    viewModel.recharge(30)

    connectivity.setOnline(true)
    viewModel.recharge(30)

    val state = viewModel.uiState.value
    assertEquals(RechargeStatus.DONE, state.rechargeStatus)
    assertEquals(80, state.rechargeNewBalance)
    assertEquals(listOf("me" to 30), wallets.recharges)
  }

  @Test
  fun theWalletAndHistoryAreStillShownWhileOffline() {
    val shown = listOf(tx("tx-1", noon))
    wallets.emit(Wallet(available = 50, heldAsDeposits = 20), shown)
    val viewModel = viewModel()

    connectivity.setOnline(false)

    val state = viewModel.uiState.value
    assertEquals(Wallet(available = 50, heldAsDeposits = 20), state.wallet)
    assertEquals(shown, state.history)
    assertNull(state.errorMsg)
  }

  @Test
  fun aRechargeAlreadySendingIsNotCancelledWhenThePhoneGoesOffline() {
    val gate = CompletableDeferred<Unit>()
    wallets.gate = gate
    val viewModel = viewModel()
    viewModel.recharge(10)

    connectivity.setOnline(false)

    assertEquals(RechargeStatus.SENDING, viewModel.uiState.value.rechargeStatus)

    gate.complete(Unit)

    assertEquals(RechargeStatus.DONE, viewModel.uiState.value.rechargeStatus)
    assertEquals(60, wallets.wallet.available)
  }

  @Test
  fun factoryCreatesAWalletViewModel() {
    connectivity.setOnline(false)
    val created =
        WalletViewModel.Factory(wallets, auth, connectivity).create(WalletViewModel::class.java)

    assertEquals(50, created.uiState.value.wallet.available)
    assertTrue(created.uiState.value.isOffline)
  }

  @Test(expected = IllegalArgumentException::class)
  fun factoryRejectsOtherViewModels() {
    WalletViewModel.Factory(wallets, auth, connectivity).create(OtherViewModel::class.java)
  }

  private class OtherViewModel : ViewModel()
}
