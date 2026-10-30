// Made with Claude.

package com.swent.polyloop.model.wallet

import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.updateAndGet

/**
 * In-memory [WalletRepository] for tests and previews; the app itself never uses it. It lives in
 * `main` so that unit tests, Compose tests and `@Preview`s can all share it.
 *
 * Holds one wallet and its history in memory, whatever the `uid` passed to its functions, but it
 * refuses a blank `uid` and records the ones it receives ([observedUids], [recharges]), so tests
 * can check that a ViewModel passes the signed-in user's. The wallet and the history are stored
 * together and change in one step, so every change is emitted to the observers at once, and [emit]
 * replaces the data without a recharge. When [failure] is set, every call fails with it; an
 * observer throws it when it subscribes or at the next change, not as soon as it is set. When
 * [gate] is set, [recharge] waits for it to complete, so tests can see a loading state. [clock]
 * gives the date of new transactions.
 *
 * @param wallet the wallet at the start.
 * @param history the transaction history at the start, kept in the order given (not sorted).
 * @property failure the error every call fails with, or null for no error.
 * @property gate when set, [recharge] suspends until it completes.
 */
class FakeWalletRepository(
    wallet: Wallet = Wallet(),
    history: List<WalletTransaction> = emptyList(),
    var failure: Throwable? = null,
    var gate: CompletableDeferred<Unit>? = null,
    private val clock: () -> Instant = { Instant.now() },
) : WalletRepository {

  /** The wallet and its history, kept together so that they are always updated in one step. */
  private data class State(val wallet: Wallet, val history: List<WalletTransaction>)

  private val state = MutableStateFlow(State(wallet, history))
  private val sent = mutableListOf<Pair<String, Int>>()
  private val observed = mutableListOf<String>()

  /** The wallet currently stored, e.g. to check the balance after a recharge. */
  val wallet: Wallet
    get() = state.value.wallet

  /** The history currently stored, in the order the transactions were added. */
  val history: List<WalletTransaction>
    get() = state.value.history

  /**
   * Every [recharge] call received, in order, as (uid, CHF amount). It includes the calls that
   * failed and the ones still waiting for [gate]. Use it to check what a ViewModel sent, and for
   * whom.
   */
  val recharges: List<Pair<String, Int>>
    get() = sent.toList()

  /**
   * The `uid` of every [observeWallet] and [observeHistory] call received, in order, including a
   * blank one. It is recorded when the function is called, not when the flow is collected. Use it
   * to check that a ViewModel observes the signed-in user's wallet.
   */
  val observedUids: List<String>
    get() = observed.toList()

  /**
   * Replaces the stored wallet and history together, and emits them to the observers, as if the
   * server had changed them. Unlike [recharge], it ignores [failure] and [gate], and records
   * nothing.
   *
   * @param wallet the new wallet.
   * @param history the new history, kept in the order given (not sorted).
   */
  fun emit(wallet: Wallet, history: List<WalletTransaction>) {
    state.value = State(wallet, history)
  }

  /**
   * @param uid the user whose wallet is observed; the fake holds a single wallet, so only a blank
   *   value matters. Recorded in [observedUids].
   * @return a flow that emits the stored wallet, then again whenever it changes. Throws [failure]
   *   if it is set, or an [IllegalArgumentException] if [uid] is blank.
   */
  override fun observeWallet(uid: String): Flow<Wallet> {
    observed += uid
    return state
        .map {
          failure?.let { throw it }
          requireUid(uid)
          it.wallet
        }
        .distinctUntilChanged()
  }

  /**
   * @param uid the user whose history is observed; the fake holds a single history, so only a blank
   *   value matters. Recorded in [observedUids].
   * @return a flow that emits the stored history, oldest first unless [emit] gave another order,
   *   then again whenever it changes. Throws [failure] if it is set, or an
   *   [IllegalArgumentException] if [uid] is blank.
   */
  override fun observeHistory(uid: String): Flow<List<WalletTransaction>> {
    observed += uid
    return state
        .map {
          failure?.let { throw it }
          requireUid(uid)
          it.history
        }
        .distinctUntilChanged()
  }

  /**
   * Records ([uid], [chf]) in [recharges], waits for [gate], then adds [chf] to the available
   * balance and appends a [WalletTransactionType.RECHARGE] transaction (positive amount, no item
   * title), both in one step.
   *
   * @param uid the user who recharges; the fake holds a single wallet, so only a blank value
   *   matters.
   * @param chf the amount in whole CHF.
   * @return a success with the wallet after the recharge; a failure with [failure] if it is set, or
   *   with an [IllegalArgumentException] if [uid] is blank or [chf] is less than 1. Nothing changes
   *   on a failure.
   */
  override suspend fun recharge(uid: String, chf: Int): Result<Wallet> {
    sent += uid to chf
    gate?.await()
    failure?.let {
      return Result.failure(it)
    }
    if (uid.isBlank()) {
      return Result.failure(IllegalArgumentException("uid is blank"))
    }
    if (chf < 1) {
      return Result.failure(IllegalArgumentException("chf $chf is less than 1"))
    }
    val recharged = state.updateAndGet {
      State(
          wallet = it.wallet.copy(available = it.wallet.available + chf),
          history =
              it.history +
                  WalletTransaction(
                      id = newId(it.history),
                      type = WalletTransactionType.RECHARGE,
                      amount = chf,
                      itemTitle = "",
                      date = clock(),
                  ),
      )
    }
    return Result.success(recharged.wallet)
  }

  private fun requireUid(uid: String) = require(uid.isNotBlank()) { "uid is blank" }

  /** "tx-N" with the smallest N not used in [history] yet, so it never clashes with it. */
  private fun newId(history: List<WalletTransaction>): String {
    val used = history.map { it.id }.toSet()
    return generateSequence(1) { it + 1 }.map { "tx-$it" }.first { it !in used }
  }
}
