// Made with Claude.

package com.swent.polyloop.model.wallet

import kotlinx.coroutines.flow.Flow

/**
 * The PolyPoints wallet of the signed-in user.
 *
 * Only Cloud Functions change balances: the app never writes them. The app only sends a request
 * (e.g. to recharge) and shows the result, and the Firestore Security Rules block any client write.
 */
interface WalletRepository {

  /**
   * Follows the wallet of [uid].
   *
   * @param uid the user whose wallet is observed.
   * @return a flow that emits the current [Wallet] when collected, then again every time it
   *   changes. A read error is thrown to the collector, which handles it with `catch`.
   */
  fun observeWallet(uid: String): Flow<Wallet>

  /**
   * Follows the transaction history of [uid].
   *
   * @param uid the user whose history is observed.
   * @return a flow that emits the current list of [WalletTransaction]s when collected, then again
   *   every time it changes. The order is not guaranteed: the caller sorts it. A read error is
   *   thrown to the collector, which handles it with `catch`.
   */
  fun observeHistory(uid: String): Flow<List<WalletTransaction>>

  /**
   * Asks to add [chf] Swiss francs to the wallet of [uid], as the same number of PP. The payment is
   * simulated: no real money moves.
   *
   * @param uid the user who recharges.
   * @param chf the amount in whole CHF. The caller checks it first; the server checks it again.
   * @return a success with the [Wallet] as it is after the recharge, or a failure with the cause if
   *   the request was refused or could not be sent (e.g. no connection).
   */
  suspend fun recharge(uid: String, chf: Int): Result<Wallet>
}
