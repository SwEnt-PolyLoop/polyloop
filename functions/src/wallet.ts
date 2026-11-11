// Made with Claude.

import { FieldValue, Firestore, DocumentReference, Transaction } from "firebase-admin/firestore";

/**
 * Reading and writing a user's wallet inside a transaction, shared by the functions that move
 * PolyPoints (see "Firestore schema" in docs/ARCHITECTURE.md): `wallets/{uid}` holds the balances,
 * and `wallets/{uid}/transactions/{id}` the history. Only Cloud Functions write them. As in every
 * Firestore transaction, read first (`readWallet`, `hasTransaction`), then write (`writeWallet`).
 */

/** The balances of a wallet, in whole PolyPoints. */
export interface Wallet {
  available: number;
  heldAsDeposits: number;
  heldForRentals: number;
}

/** One row of a wallet's history. */
export interface WalletRow {
  /** The document id; a fixed one (e.g. `deposit_held_<rentalId>`) makes a payment idempotent. */
  id: string;
  /** A `WalletTransactionType` name, e.g. `DEPOSIT_HELD`. */
  type: string;
  /** Signed, from the point of view of the wallet's owner; never 0. */
  amount: number;
  /** The title of the item, or "" when there is none. */
  itemTitle: string;
}

/**
 * Reads the wallet of [uid] inside the transaction. A missing document, and a missing, non-integer
 * or negative field, read as 0, like the app does.
 *
 * @param tx the transaction to read in.
 * @param db the Firestore database.
 * @param uid the owner of the wallet.
 * @returns the balances, all 0 if the wallet does not exist yet.
 */
export async function readWallet(tx: Transaction, db: Firestore, uid: string): Promise<Wallet> {
  const snapshot = await tx.get(walletRef(db, uid));
  return {
    available: amountOrZero(snapshot.get("available")),
    heldAsDeposits: amountOrZero(snapshot.get("heldAsDeposits")),
    heldForRentals: amountOrZero(snapshot.get("heldForRentals")),
  };
}

/**
 * Tells whether the history of [uid] already has the row [id], read inside the transaction.
 *
 * @param tx the transaction to read in.
 * @param db the Firestore database.
 * @param uid the owner of the wallet.
 * @param id the id of the history row.
 * @returns true if the row exists.
 */
export async function hasTransaction(
  tx: Transaction,
  db: Firestore,
  uid: string,
  id: string,
): Promise<boolean> {
  return (await tx.get(transactionRef(db, uid, id))).exists;
}

/**
 * Sets the balances of [uid], creating the wallet if it does not exist, and adds [row] to the
 * history, dated with the server's time. Runs inside the caller's transaction, after its reads.
 *
 * @param tx the transaction to write in.
 * @param db the Firestore database.
 * @param uid the owner of the wallet.
 * @param wallet the new balances.
 * @param row the history row that explains the change.
 */
export function writeWallet(
  tx: Transaction,
  db: Firestore,
  uid: string,
  wallet: Wallet,
  row: WalletRow,
): void {
  tx.set(walletRef(db, uid), wallet, { merge: true });
  tx.set(transactionRef(db, uid, row.id), {
    type: row.type,
    amount: row.amount,
    itemTitle: row.itemTitle,
    date: FieldValue.serverTimestamp(),
  });
}

function walletRef(db: Firestore, uid: string): DocumentReference {
  return db.collection("wallets").doc(uid);
}

function transactionRef(db: Firestore, uid: string, id: string): DocumentReference {
  return walletRef(db, uid).collection("transactions").doc(id);
}

/** [value] if it is a whole number from 0 up, otherwise 0. */
function amountOrZero(value: unknown): number {
  return typeof value === "number" && Number.isInteger(value) && value > 0 ? value : 0;
}
