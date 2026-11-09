// Made with Claude.

package com.swent.polyloop.model.wallet

import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.swent.polyloop.model.firestore.isValidDocumentId
import com.swent.polyloop.model.firestore.snapshotsAsFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/**
 * [WalletRepository] backed by Firestore: the balance at `wallets/{uid}` and the history at
 * `wallets/{uid}/transactions/{id}` (see "Firestore schema" in docs/ARCHITECTURE.md). The app only
 * reads them: Cloud Functions write them and the Security Rules refuse every client write.
 *
 * The wallet and the history are followed with plain snapshot listeners, which serve the local
 * cache first and while offline, so both stay readable offline (see "Offline mode" in AGENTS.md).
 * That is deliberate: do not force a server read here.
 *
 * @param db the Firestore instance; tests pass a mock.
 */
class WalletRepositoryFirestore(private val db: FirebaseFirestore = Firebase.firestore) :
    WalletRepository {

  /**
   * An invalid [uid] (blank, or containing `/`) has no wallet, so it reads as an empty one without
   * touching Firestore, as for the other repositories. A user without a wallet document, a missing
   * or non-integer field, or a negative value also reads as 0 (see [walletFromDocument]).
   */
  override fun observeWallet(uid: String): Flow<Wallet> {
    if (!isValidDocumentId(uid)) return flowOf(Wallet())
    return walletDocument(uid)
        .snapshotsAsFlow { snapshot ->
          walletFromDocument(snapshot.getData(ServerTimestampBehavior.ESTIMATE))
        }
        .distinctUntilChanged()
  }

  /**
   * An invalid [uid] gives an empty history without touching Firestore. A malformed row is skipped
   * instead of failing the list (see [walletTransactionFromDocument]). The rows are not sorted: the
   * caller does it.
   */
  override fun observeHistory(uid: String): Flow<List<WalletTransaction>> {
    if (!isValidDocumentId(uid)) return flowOf(emptyList())
    return walletDocument(uid)
        .collection(WalletFields.TRANSACTIONS)
        .snapshotsAsFlow { snapshot ->
          snapshot.documents.mapNotNull { document ->
            document.getData(ServerTimestampBehavior.ESTIMATE)?.let {
              walletTransactionFromDocument(document.id, it)
            }
          }
        }
        .distinctUntilChanged()
  }

  /**
   * Always fails with an [UnsupportedOperationException]: recharging needs a Cloud Function that
   * does not exist yet, and the app must not write the balance itself. The interface's
   * [IllegalArgumentException] and [ArithmeticException] cases do not apply, as nothing is sent.
   * `WalletViewModel` turns any failure into `RechargeStatus.ERROR`.
   */
  override suspend fun recharge(uid: String, chf: Int): Result<Wallet> =
      Result.failure(UnsupportedOperationException("Recharging is not available yet"))

  private fun walletDocument(uid: String): DocumentReference =
      db.collection(WalletFields.COLLECTION).document(uid)
}
