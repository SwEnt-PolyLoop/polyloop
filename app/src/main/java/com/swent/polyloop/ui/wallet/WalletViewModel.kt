// Made with Claude.

package com.swent.polyloop.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swent.polyloop.model.auth.AuthRepository
import com.swent.polyloop.model.connectivity.ConnectivityRepository
import com.swent.polyloop.model.wallet.Wallet
import com.swent.polyloop.model.wallet.WalletRepository
import com.swent.polyloop.model.wallet.WalletTransaction
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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
 * @property wallet the balance and the amounts held, as last observed from the repository; null
 *   while unknown: nothing has been confirmed yet, e.g. offline with an empty cache. Unknown is not
 *   0, so the screen never shows it as 0 PP. Once known it is kept, also when the phone goes
 *   offline or a later read fails.
 * @property history the transaction history, newest first, or null while unknown (see [wallet]):
 *   unknown is not "no transactions". Transactions with the same date are ordered by id, highest
 *   first, so the order is always the same.
 * @property isLoading true until the wallet and the history are both known, or a read failed. It
 *   stays true offline while one is still unknown, so the screen shows a spinner only online.
 * @property errorMsg null without an error, blank for the generic text.
 * @property isOffline true while the phone is offline: the wallet and the history stay readable
 *   from cache, but a recharge is refused.
 * @property isUnreachable true when the wallet or the history is still unknown
 *   [UNREACHABLE_AFTER_MS] after the screen began to read them, with no error: the server is not
 *   answering, whatever the phone's network status says (captive Wi-Fi, a router without internet).
 *   The screen then shows the same unavailable state as offline, without the offline banner. A
 *   confirmed snapshot that arrives later makes both known and resets it; it never replaces a value
 *   that is already known, and [isLoading] stays true until both are known. Starting to read again
 *   (retry) resets it and restarts the wait.
 * @property rechargeStatus where the last recharge stands.
 * @property rechargeAmount the amount of the last recharge in whole PP, sent or refused; null
 *   before the first one.
 * @property rechargeNewBalance the available balance returned by the last successful recharge; null
 *   until one succeeds, and reset when the next recharge starts.
 */
data class WalletUiState(
    val wallet: Wallet? = null,
    val history: List<WalletTransaction>? = null,
    val isLoading: Boolean = true,
    val errorMsg: String? = null,
    val isOffline: Boolean = false,
    val isUnreachable: Boolean = false,
    val rechargeStatus: RechargeStatus = RechargeStatus.IDLE,
    val rechargeAmount: Int? = null,
    val rechargeNewBalance: Int? = null,
)

/**
 * Shows the wallet and the history of the signed-in user and sends recharges. It never changes a
 * balance itself: only the repository does, and the wallet shown is the one it observes. Reading
 * keeps working offline; a recharge needs a connection and is refused while offline.
 *
 * @param walletRepository the wallet, the history and recharges.
 * @param authRepository gives the signed-in user.
 * @param connectivityRepository tells whether the phone is online.
 */
class WalletViewModel(
    private val walletRepository: WalletRepository,
    private val authRepository: AuthRepository,
    private val connectivityRepository: ConnectivityRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(WalletUiState())

  /** The state of the Wallet screen. */
  val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

  private var observing: Job? = null

  init {
    // Apart from the wallet and history job, so that retry() neither stops nor duplicates it.
    viewModelScope.launch {
      connectivityRepository.isOnline.collect { online ->
        _uiState.update { it.copy(isOffline = !online) }
      }
    }
    observe()
  }

  /** Starts following the wallet and the history again, e.g. after an error. */
  fun retry() = observe()

  /**
   * Sends a recharge of [amount] CHF (the same number of PP). An amount outside
   * [WalletRepository.MIN_RECHARGE]..[WalletRepository.MAX_RECHARGE], or no signed-in user, sets
   * [RechargeStatus.ERROR] without calling the repository, and so does being offline
   * ([WalletUiState.isOffline]) or unreachable ([WalletUiState.isUnreachable]). Ignored while a
   * recharge is sending, even if the phone has gone offline since: a recharge already sent is not
   * cancelled.
   *
   * @param amount the amount in whole CHF.
   */
  fun recharge(amount: Int) {
    if (_uiState.value.rechargeStatus == RechargeStatus.SENDING) return
    val uid = authRepository.getCurrentUser()?.uid
    if (
        _uiState.value.isOffline ||
            _uiState.value.isUnreachable ||
            uid == null ||
            amount !in WalletRepository.MIN_RECHARGE..WalletRepository.MAX_RECHARGE
    ) {
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
    _uiState.update { it.copy(isLoading = true, errorMsg = null, isUnreachable = false) }
    val uid = authRepository.getCurrentUser()?.uid
    if (uid == null) {
      _uiState.update { it.copy(isLoading = false, errorMsg = "") }
      return
    }
    val walletFlow = walletRepository.observeWallet(uid)
    val historyFlow = walletRepository.observeHistory(uid)
    observing = viewModelScope.launch {
      // A child of this job, so a restart (retry) cancels it and starts a new wait.
      launch {
        delay(UNREACHABLE_AFTER_MS)
        _uiState.update {
          // Ignored once both are known or an error is shown; known values are never touched.
          if (it.errorMsg == null && (it.wallet == null || it.history == null)) {
            it.copy(isUnreachable = true)
          } else it
        }
      }
      // Each repository flow stays silent while its data is unconfirmed (e.g. offline with an empty
      // cache), so a null first lets the other one through instead of waiting for both.
      combine(
              walletFlow.map<Wallet, Wallet?> { it }.onStart { emit(null) },
              historyFlow
                  .map<List<WalletTransaction>, List<WalletTransaction>?> { it }
                  .onStart { emit(null) },
          ) { wallet, history ->
            wallet to history?.sortedWith(NEWEST_FIRST)
          }
          .catch {
            _uiState.update { it.copy(isLoading = false, errorMsg = "", isUnreachable = false) }
          }
          .collect { (wallet, history) ->
            _uiState.update {
              // A restart (retry) begins with nulls: keep what was known instead of dropping it.
              val knownWallet = wallet ?: it.wallet
              val knownHistory = history ?: it.history
              it.copy(
                  wallet = knownWallet,
                  history = knownHistory,
                  isLoading = knownWallet == null || knownHistory == null,
                  isUnreachable = it.isUnreachable && (knownWallet == null || knownHistory == null),
                  errorMsg = null,
              )
            }
          }
    }
  }

  /** Builds the ViewModel with its dependencies (manual constructor injection). */
  class Factory(
      private val walletRepository: WalletRepository,
      private val authRepository: AuthRepository,
      private val connectivityRepository: ConnectivityRepository,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(WalletViewModel::class.java)) {
        "Unknown ViewModel class: ${modelClass.name}"
      }
      @Suppress("UNCHECKED_CAST")
      return WalletViewModel(walletRepository, authRepository, connectivityRepository) as T
    }
  }

  private companion object {
    /**
     * How long the wallet or the history may stay unknown before the screen calls the server
     * unreachable. A first answer on a working connection takes a few seconds at most, even on a
     * bad mobile one, while a captive-portal Wi-Fi or a router without internet never answers, so a
     * longer wait only keeps the spinner on dead Wi-Fi. Being wrong is cheap: a confirmed snapshot
     * that arrives later shows the real values at once.
     */
    const val UNREACHABLE_AFTER_MS = 10_000L

    /** Newest first; for the same date, the highest id first, so the order is deterministic. */
    val NEWEST_FIRST = compareByDescending<WalletTransaction> { it.date }.thenByDescending { it.id }
  }
}
