// Made with Claude.

package com.swent.polyloop.model.wallet

import com.swent.polyloop.model.firestore.enumOrNull
import com.swent.polyloop.model.firestore.instantOrNull
import com.swent.polyloop.model.firestore.intOrNull

/** Firestore names for wallets (see "Firestore schema" in docs/ARCHITECTURE.md). */
internal object WalletFields {
  const val COLLECTION = "wallets"
  const val TRANSACTIONS = "transactions"
  const val AVAILABLE = "available"
  const val HELD_AS_DEPOSITS = "heldAsDeposits"
  const val HELD_FOR_RENTALS = "heldForRentals"
  const val TYPE = "type"
  const val AMOUNT = "amount"
  const val ITEM_TITLE = "itemTitle"
  const val DATE = "date"
}

/**
 * Builds a [Wallet] from the document `wallets/{uid}`. [data] is null when the document does not
 * exist. A missing field, a field that is not a whole number, and a negative value all read as 0: a
 * wallet is never negative, so a corrupted value must not show as a debt.
 *
 * @param data the fields of the document, or null if it does not exist.
 * @return the wallet; all zeros when [data] is null.
 */
internal fun walletFromDocument(data: Map<String, Any?>?): Wallet =
    Wallet(
        available = data.amountOrZero(WalletFields.AVAILABLE),
        heldAsDeposits = data.amountOrZero(WalletFields.HELD_AS_DEPOSITS),
        heldForRentals = data.amountOrZero(WalletFields.HELD_FOR_RENTALS),
    )

/**
 * Builds a [WalletTransaction] from a document of `wallets/{uid}/transactions`, or returns null if
 * it breaks the invariants of [WalletTransaction]: unknown `type`, missing or invalid `date`, and
 * missing, non-integer or zero `amount`. A missing `itemTitle` reads as "". The caller must read
 * the document with `ServerTimestampBehavior.ESTIMATE`, so `date` is never null for a pending
 * server timestamp.
 *
 * @param id the id of the transaction document.
 * @param data the fields of the transaction document.
 * @return the transaction, or null if the document is invalid.
 */
internal fun walletTransactionFromDocument(
    id: String,
    data: Map<String, Any?>,
): WalletTransaction? {
  val amount = intOrNull(data[WalletFields.AMOUNT])?.takeIf { it != 0 } ?: return null
  return WalletTransaction(
      id = id,
      type = enumOrNull<WalletTransactionType>(data[WalletFields.TYPE]) ?: return null,
      amount = amount,
      itemTitle = data[WalletFields.ITEM_TITLE] as? String ?: "",
      date = instantOrNull(data[WalletFields.DATE]) ?: return null,
  )
}

private fun Map<String, Any?>?.amountOrZero(field: String): Int =
    intOrNull(this?.get(field))?.coerceAtLeast(0) ?: 0
