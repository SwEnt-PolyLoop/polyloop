// Made with Claude.

package com.swent.polyloop.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.wallet.Wallet
import com.swent.polyloop.model.wallet.WalletRepository
import com.swent.polyloop.model.wallet.WalletTransaction
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where a recharge stands. */
enum class RechargeStatus {
  /** No recharge in progress and no result to show. */
  IDLE,

  /** A recharge was sent and the answer has not come back yet. */
  SENDING,

  /** The last recharge went through. */
  DONE,

  /** The last recharge was refused or never sent. The screen shows a generic message. */
  ERROR,
}

/**
 * State of the Wallet screen. [errorMsg] is non-null when the wallet or the history cannot be
 * loaded (or nobody is signed in); it is blank when there is no message to show, and the screen
 * then shows a generic text. On an error the last wallet and history are kept.
 *
 * @property wallet the balance and the amounts held, as last observed from the repository.
 * @property history the transaction history, newest first. Transactions with the same date are
 *   ordered by id, highest first, so the order is always the same.
 * @property isLoading true until the wallet and the history have both been read once, or failed.
 * @property errorMsg null without an error, blank for the generic text.
 * @property rechargeStatus where the last recharge stands.
 * @property rechargeAmount the amount of the last recharge in whole PP, sent or refused; null
 *   before the first one.
 * @property rechargeNewBalance the available balance returned by the last successful recharge; null
 *   until one succeeds, and reset when the next recharge starts.
 */
data class WalletUiState(
    val wallet: Wallet = Wallet(),
    val history: List<WalletTransaction> = emptyList(),
    val isLoading: Boolean = true,
    val errorMsg: String? = null,
    val rechargeStatus: RechargeStatus = RechargeStatus.IDLE,
    val rechargeAmount: Int? = null,
    val rechargeNewBalance: Int? = null,
)

/**
 * Shows the wallet and the history of the signed-in user and sends recharges. It never changes a
 * balance itself: only the repository does, and the wallet shown is the one it observes.
 *
 * @param walletRepository the wallet, the history and recharges.
 * @param authRepository gives the signed-in user.
 */
class WalletViewModel(
    private val walletRepository: WalletRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(WalletUiState())

  /** The state of the Wallet screen. */
  val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

  private var observing: Job? = null

  init {
    observe()
  }

  /** Starts following the wallet and the history again, e.g. after an error. */
  fun retry() = observe()

  /**
   * Sends a recharge of [amount] CHF (the same number of PP). An amount outside
   * [WalletRepository.MIN_RECHARGE]..[WalletRepository.MAX_RECHARGE], or no signed-in user, sets
   * [RechargeStatus.ERROR] without calling the repository. Ignored while a recharge is sending.
   *
   * @param amount the amount in whole CHF.
   */
  fun recharge(amount: Int) {
    if (_uiState.value.rechargeStatus == RechargeStatus.SENDING) return
    val uid = authRepository.getCurrentUser()?.uid
    if (uid == null || amount !in WalletRepository.MIN_RECHARGE..WalletRepository.MAX_RECHARGE) {
      _uiState.update {
        it.copy(
            rechargeStatus = RechargeStatus.ERROR,
            rechargeAmount = amount,
            rechargeNewBalance = null,
        )
      }
      return
    }
    _uiState.update {
      it.copy(
          rechargeStatus = RechargeStatus.SENDING,
          rechargeAmount = amount,
          rechargeNewBalance = null,
      )
    }
    viewModelScope.launch {
      walletRepository
          .recharge(uid, amount)
          .onSuccess { wallet ->
            _uiState.update {
              it.copy(rechargeStatus = RechargeStatus.DONE, rechargeNewBalance = wallet.available)
            }
          }
          .onFailure { _uiState.update { it.copy(rechargeStatus = RechargeStatus.ERROR) } }
    }
  }

  /** Hides the result of the last recharge ([RechargeStatus.DONE] or [RechargeStatus.ERROR]). */
  fun clearRechargeResult() {
    _uiState.update {
      if (it.rechargeStatus == RechargeStatus.SENDING) it
      else it.copy(rechargeStatus = RechargeStatus.IDLE)
    }
  }

  private fun observe() {
    observing?.cancel()
    _uiState.update { it.copy(isLoading = true, errorMsg = null) }
    val uid = authRepository.getCurrentUser()?.uid
    if (uid == null) {
      _uiState.update { it.copy(isLoading = false, errorMsg = "") }
      return
    }
    val wallet = walletRepository.observeWallet(uid)
    val history = walletRepository.observeHistory(uid)
    observing = viewModelScope.launch {
      combine(wallet, history) { w, h -> w to h.sortedWith(NEWEST_FIRST) }
          .catch { _uiState.update { it.copy(isLoading = false, errorMsg = "") } }
          .collect { (wallet, history) ->
            _uiState.update {
              it.copy(wallet = wallet, history = history, isLoading = false, errorMsg = null)
            }
          }
    }
  }

  /** Builds the ViewModel with its dependencies (manual constructor injection). */
  class Factory(
      private val walletRepository: WalletRepository,
      private val authRepository: AuthRepository,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(WalletViewModel::class.java)) {
        "Unknown ViewModel class: ${modelClass.name}"
      }
      @Suppress("UNCHECKED_CAST")
      return WalletViewModel(walletRepository, authRepository) as T
    }
  }

  private companion object {
    /** Newest first; for the same date, the highest id first, so the order is deterministic. */
    val NEWEST_FIRST = compareByDescending<WalletTransaction> { it.date }.thenByDescending { it.id }
  }
}
