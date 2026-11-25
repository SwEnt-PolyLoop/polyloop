// Made with Claude.

import {
  DocumentData,
  DocumentReference,
  FieldValue,
  Firestore,
  Transaction,
} from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { splitDeposit } from "./disputeRules";
import { Parties, RequestError, listingTitleOf } from "./rentalRules";

/**
 * Reading and writing a user's wallet inside a transaction, shared by the functions that move
 * PolyPoints (see "Firestore schema" in docs/ARCHITECTURE.md): `wallets/{uid}` holds the balances,
 * and `wallets/{uid}/transactions/{id}` the history. Only Cloud Functions write them. As in every
 * Firestore transaction, read first (`readWallet`, `hasTransaction`), check (`requireFunds`), then
 * write (`writeWallet`, or `writeDepositHold` for a deposit, whose row id is `depositRowId`).
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
 * history, dated with the server's time; with no [row], the history is left as it is (a move
 * between held amounts that does not change `available`). Runs inside the caller's transaction,
 * after its reads.
 *
 * @param tx the transaction to write in.
 * @param db the Firestore database.
 * @param uid the owner of the wallet.
 * @param wallet the new balances.
 * @param row the history row that explains the change, if any.
 */
export function writeWallet(
  tx: Transaction,
  db: Firestore,
  uid: string,
  wallet: Wallet,
  row?: WalletRow,
): void {
  tx.set(walletRef(db, uid), wallet, { merge: true });
  if (!row) return;
  tx.set(transactionRef(db, uid, row.id), {
    type: row.type,
    amount: row.amount,
    itemTitle: row.itemTitle,
    date: FieldValue.serverTimestamp(),
  });
}

/** The `type` of the history row of a held deposit. */
const DEPOSIT_HELD = "DEPOSIT_HELD";

/** The id of the history row of a rental's deposit hold, which makes the hold idempotent. */
export function depositRowId(rentalId: string): string {
  return `deposit_held_${rentalId}`;
}

/**
 * Refuses a hold of [amount] if [wallet] cannot cover it. Called after the caller's other checks,
 * so the order of refusals stays the caller's.
 *
 * @param wallet the borrower's wallet, as read in the same transaction.
 * @param amount the deposit to hold.
 * @throws RequestError `failed-precondition` when `available` is below [amount].
 */
export function requireFunds(wallet: Wallet, amount: number): void {
  if (wallet.available < amount) {
    throw new RequestError("failed-precondition", "Insufficient funds to hold the deposit.");
  }
}

/**
 * Write step of a deposit hold, inside the caller's transaction after its reads: `available -=
 * deposit`, `heldAsDeposits += deposit`, and the `DEPOSIT_HELD` row `deposit_held_<rentalId>` with
 * the amount `-deposit`. The funds are checked before, with `requireFunds`.
 *
 * @param tx the transaction to write in.
 * @param db the Firestore database.
 * @param borrowerId the owner of the wallet.
 * @param wallet the borrower's wallet, as read in the same transaction.
 * @param deposit the amount to hold.
 * @param rentalId the rental the deposit belongs to.
 * @param itemTitle the title of the item, or "".
 */
export function writeDepositHold(
  tx: Transaction,
  db: Firestore,
  borrowerId: string,
  wallet: Wallet,
  deposit: number,
  rentalId: string,
  itemTitle: string,
): void {
  writeWallet(
    tx,
    db,
    borrowerId,
    {
      ...wallet,
      available: wallet.available - deposit,
      heldAsDeposits: wallet.heldAsDeposits + deposit,
    },
    { id: depositRowId(rentalId), type: DEPOSIT_HELD, amount: -deposit, itemTitle },
  );
}

/** The `type` of the history row of a deposit given back whole after a clean return. */
const DEPOSIT_RELEASED = "DEPOSIT_RELEASED";
/** The `type` of the history row of a deposit shared by a split. */
const DEPOSIT_SPLIT = "DEPOSIT_SPLIT";

/** The borrower and the lender of a rental, whose wallets a settlement moves. */
export type SettlementParties = Parties;

/** What a settlement reads, inside the caller's transaction (see [readDepositSettlement]). */
export interface DepositSettlement {
  borrower: Wallet;
  lender: Wallet;
  /** Whether the borrower's `deposit_held_<rentalId>` row exists: a deposit was really held. */
  held: boolean;
  /** Whether the deposit was already released or split. */
  settled: boolean;
}

/** How a settled deposit is shared, in whole PolyPoints. */
export interface DepositShares {
  lender: number;
  borrower: number;
}

/**
 * The amount of a rental's deposit left to settle: `deposit - lateFees`, each read as 0 when
 * missing or not a whole number from 0 up, and never below 0. The late-fee job takes the fees from
 * the held deposit and pays the lender itself, so they are not paid again at settlement.
 *
 * @param rental the rental document's data.
 * @returns the whole PolyPoints left to settle, 0 or more.
 */
export function depositToSettle(rental: DocumentData): number {
  return Math.max(0, amountOrZero(rental.deposit) - amountOrZero(rental.lateFees));
}

/**
 * Read step of a deposit settlement, inside the caller's transaction before its writes: both
 * wallets, whether the deposit was held, and whether it was already settled. Settled means the
 * borrower's released or split row exists, or the lender's split row does, so no new field is
 * needed and a second settlement changes nothing.
 *
 * @param tx the transaction to read in.
 * @param db the Firestore database.
 * @param parties the borrower and the lender, whose wallets and rows are read.
 * @param rentalId the rental the deposit belongs to.
 * @returns both wallets, and whether the deposit was held and already settled.
 */
export async function readDepositSettlement(
  tx: Transaction,
  db: Firestore,
  parties: SettlementParties,
  rentalId: string,
): Promise<DepositSettlement> {
  const { borrowerId, lenderId } = parties;
  const [borrower, lender, held, released, borrowerSplit, lenderSplit] = await Promise.all([
    readWallet(tx, db, borrowerId),
    readWallet(tx, db, lenderId),
    hasTransaction(tx, db, borrowerId, depositRowId(rentalId)),
    hasTransaction(tx, db, borrowerId, releasedRowId(rentalId)),
    hasTransaction(tx, db, borrowerId, splitRowId(rentalId)),
    hasTransaction(tx, db, lenderId, splitRowId(rentalId)),
  ]);
  return { borrower, lender, held, settled: released || borrowerSplit || lenderSplit };
}

/** How a deposit is settled: given back whole after a clean return, or split by a percentage. */
export type DepositOutcome = { kind: "release" } | { kind: "split"; lenderPercent: number };

/**
 * Write step of a deposit settlement, inside the caller's transaction after its reads. It works
 * out the amount (`depositToSettle`, the late fees already taken) and the shares itself, so a
 * caller cannot pass shares that leave part of the deposit held for good. The borrower's
 * `heldAsDeposits` goes down by the whole amount, and each share goes to its owner's `available`.
 * A clean return (`release`) gives everything to the borrower, with a `DEPOSIT_RELEASED` row
 * `deposit_released_<rentalId>`; a split (`splitDeposit`, the borrower's share rounded down) gives
 * each side that receives PolyPoints a `DEPOSIT_SPLIT` row `deposit_split_<rentalId>`, and none for
 * a share of 0. Nothing moves when no deposit was held (a rental from before deposits were held),
 * when it was already settled, or when there is nothing to settle.
 *
 * @param tx the transaction to write in.
 * @param db the Firestore database.
 * @param parties the borrower and the lender.
 * @param rentalId the rental the deposit belongs to.
 * @param rental the rental document's data: the deposit, the late fees and the item title.
 * @param state what [readDepositSettlement] read in the same transaction.
 * @param outcome a clean return, or a split with the lender's percentage.
 * @returns the shares settled, which add up to the amount; both 0 when nothing moved.
 * @throws RequestError `internal` when the borrower's `heldAsDeposits` is below the amount: the
 *   data is broken, and nothing is written.
 */
export function writeDepositSettlement(
  tx: Transaction,
  db: Firestore,
  parties: SettlementParties,
  rentalId: string,
  rental: DocumentData,
  state: DepositSettlement,
  outcome: DepositOutcome,
): DepositShares {
  const amount = depositToSettle(rental);
  if (state.settled || amount <= 0) return { lender: 0, borrower: 0 };
  if (!state.held) {
    logger.warn("Settling a deposit that was never held: nothing moves", { rentalId });
    return { lender: 0, borrower: 0 };
  }
  const release = outcome.kind === "release";
  const shares: DepositShares = release
    ? { lender: 0, borrower: amount }
    : splitDeposit(amount, outcome.lenderPercent);
  const itemTitle = listingTitleOf(rental);
  if (state.borrower.heldAsDeposits < amount) {
    logger.error("Held deposits do not cover the settlement", {
      rentalId,
      amount,
      heldAsDeposits: state.borrower.heldAsDeposits,
    });
    throw new RequestError("internal", "The borrower's held deposits do not cover the deposit.");
  }
  const borrowerRow =
    shares.borrower > 0
      ? {
          id: release ? releasedRowId(rentalId) : splitRowId(rentalId),
          type: release ? DEPOSIT_RELEASED : DEPOSIT_SPLIT,
          amount: shares.borrower,
          itemTitle,
        }
      : undefined;
  writeWallet(
    tx,
    db,
    parties.borrowerId,
    {
      ...state.borrower,
      available: state.borrower.available + shares.borrower,
      heldAsDeposits: state.borrower.heldAsDeposits - amount,
    },
    borrowerRow,
  );
  if (shares.lender > 0) {
    writeWallet(
      tx,
      db,
      parties.lenderId,
      { ...state.lender, available: state.lender.available + shares.lender },
      { id: splitRowId(rentalId), type: DEPOSIT_SPLIT, amount: shares.lender, itemTitle },
    );
  }
  return shares;
}

/**
 * The id of the borrower's history row of a deposit released after a clean return, which makes the
 * release idempotent.
 *
 * @param rentalId the rental the deposit belongs to.
 * @returns `deposit_released_<rentalId>`.
 */
function releasedRowId(rentalId: string): string {
  return `deposit_released_${rentalId}`;
}

/**
 * The id of the history row of a split deposit, the same on the borrower's and the lender's side,
 * which makes the split idempotent.
 *
 * @param rentalId the rental the deposit belongs to.
 * @returns `deposit_split_<rentalId>`.
 */
function splitRowId(rentalId: string): string {
  return `deposit_split_${rentalId}`;
}

function walletRef(db: Firestore, uid: string): DocumentReference {
  return db.collection("wallets").doc(uid);
}

function transactionRef(db: Firestore, uid: string, id: string): DocumentReference {
  return walletRef(db, uid).collection("transactions").doc(id);
}

/** [value] if it is a whole number from 0 up, otherwise 0. */
export function amountOrZero(value: unknown): number {
  return typeof value === "number" && Number.isInteger(value) && value > 0 ? value : 0;
}
