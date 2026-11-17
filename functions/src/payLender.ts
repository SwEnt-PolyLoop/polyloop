// Made with Claude.

import { DocumentData, Firestore, Transaction, getFirestore } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { toCallableError } from "./callable";
import {
  RequestError,
  listingTitleOf,
  parseRentalId,
  requirePerson,
  requireVerifiedEpfl,
} from "./rentalRules";
import { Wallet, hasTransaction, readWallet, writeWallet } from "./wallet";

/**
 * The statuses from which the lender can be paid: the item has been picked up. A payment missed
 * while the rental was ACTIVE (app killed, offline scans synced together) can still be made up
 * after the rental has moved on.
 */
export const PAID_STATUSES = [
  "ACTIVE",
  "RETURNED",
  "OVERDUE",
  "DISPUTED",
  "FROZEN",
  "LOST",
  "CLOSED",
];
const RENTAL_PAYMENT = "RENTAL_PAYMENT";

/**
 * Pays the lender of the picked-up rental [rentalId] the rental's `totalPrice`, in one transaction,
 * from the price held when the request was sent: borrower `heldForRentals -= price`, lender
 * `available += price` (the lender's wallet is created if it does not exist), and one
 * `RENTAL_PAYMENT` row `+price` with the id `rental_payment_<rentalId>` and the item's title in the
 * lender's history. The borrower's history gets no row: their `available` does not change, it went
 * down when the price was held. The price, the people and the title all come from the rental. The
 * checks run in this order: the rental exists, the caller is a participant, the borrower and the
 * lender are valid and different, then, if the lender's row already exists, the call succeeds and
 * changes nothing (whatever the rental's status is by now; the row is looked for in the same
 * transaction), then the status is one of [PAID_STATUSES], the price is valid and held.
 *
 * @param db the Firestore database.
 * @param callerId the caller, who must be the rental's borrower or its lender.
 * @param rentalId the rental to pay.
 * @throws RequestError `not-found`, `permission-denied` (not the borrower or the lender),
 *   `failed-precondition` (no row yet and the rental is not picked up, or the borrower's
 *   `heldForRentals` is below the price) or `internal` (the rental has no valid borrower or lender, the same
 *   one for both, or, with no row yet, no valid price). Nothing is written then.
 */
export async function payRentalLender(
  db: Firestore,
  callerId: string,
  rentalId: string,
): Promise<void> {
  const rentalRef = db.collection("rentals").doc(rentalId);
  await db.runTransaction(async (tx) => {
    const snapshot = await tx.get(rentalRef);
    const rental = snapshot.data();
    if (!rental) {
      throw new RequestError("not-found", "This rental does not exist.");
    }
    if (callerId !== rental.ownerId && callerId !== rental.borrowerId) {
      throw new RequestError("permission-denied", "Only the two participants can do this.");
    }
    const parties = requireParties(rental);
    if (await isLenderPaid(tx, db, parties, rentalId)) return;
    if (!PAID_STATUSES.includes(rental.status)) {
      throw new RequestError("failed-precondition", "Only a picked-up rental can be paid.");
    }
    const payment = await readLenderPayment(tx, db, rental, parties);
    writeLenderPayment(tx, db, rentalId, payment);
  });
}

/** The borrower and the lender of a rental. */
export interface Parties {
  borrowerId: string;
  lenderId: string;
}

/** What a payment moves, read inside the caller's transaction (see [readLenderPayment]). */
export interface LenderPayment extends Parties {
  price: number;
  itemTitle: string;
  borrower: Wallet;
  lender: Wallet;
}

/**
 * The rental's borrower and lender, two different valid users, or an `internal` refusal: the
 * rental's data is broken then.
 */
export function requireParties(rental: DocumentData): Parties {
  const borrowerId = requirePerson(rental.borrowerId, "borrower");
  const lenderId = requirePerson(rental.ownerId, "lender");
  if (borrowerId === lenderId) {
    throw new RequestError("internal", "The rental has the same borrower and lender.");
  }
  return { borrowerId, lenderId };
}

/**
 * True when the lender of [rentalId] has already been paid: the lender's row exists. Read inside the
 * caller's transaction, so two payments sent together never both pay.
 */
export function isLenderPaid(
  tx: Transaction,
  db: Firestore,
  parties: Parties,
  rentalId: string,
): Promise<boolean> {
  return hasTransaction(tx, db, parties.lenderId, paymentRowId(rentalId));
}

/**
 * Read step of a payment, inside the caller's transaction before its writes: the price, the title
 * and both wallets. Fails with `internal` for a missing or invalid price, and with
 * `failed-precondition` when the borrower's `heldForRentals` is below the price: the price was not
 * held when the request was sent. It is never taken from `available` instead.
 */
export async function readLenderPayment(
  tx: Transaction,
  db: Firestore,
  rental: DocumentData,
  parties: Parties,
): Promise<LenderPayment> {
  const price = requirePrice(rental);
  const borrower = await readWallet(tx, db, parties.borrowerId);
  if (borrower.heldForRentals < price) {
    throw new RequestError("failed-precondition", "The rental price is not held.");
  }
  const lender = await readWallet(tx, db, parties.lenderId);
  return { ...parties, price, itemTitle: listingTitleOf(rental), borrower, lender };
}

/** Write step of a payment, inside the caller's transaction after its reads. */
export function writeLenderPayment(
  tx: Transaction,
  db: Firestore,
  rentalId: string,
  payment: LenderPayment,
): void {
  const { price, itemTitle, borrower, lender } = payment;
  const id = paymentRowId(rentalId);
  // No row for the borrower: their available balance went down when the price was held.
  writeWallet(tx, db, payment.borrowerId, {
    ...borrower,
    heldForRentals: borrower.heldForRentals - price,
  });
  writeWallet(
    tx,
    db,
    payment.lenderId,
    { ...lender, available: lender.available + price },
    { id, type: RENTAL_PAYMENT, amount: price, itemTitle },
  );
}

function paymentRowId(rentalId: string): string {
  return `rental_payment_${rentalId}`;
}

/**
 * Callable `payLender` (see docs/ARCHITECTURE.md): moves a picked-up rental's price from what the
 * borrower holds for rentals to the lender's available balance. Input: `{rentalId}` only. Returns
 * nothing, so the lender learns only whether the price is held, never the borrower's balances.
 */
export const payLender = onCall(async (request) => {
  try {
    const uid = requireVerifiedEpfl(request.auth);
    await payRentalLender(getFirestore(), uid, parseRentalId(request.data));
    return {};
  } catch (error) {
    throw toCallableError(error);
  }
});

/** The rental's total price, a whole number of PolyPoints from 1 up, or a refusal. */
function requirePrice(rental: DocumentData): number {
  const price = rental.totalPrice;
  if (typeof price !== "number" || !Number.isSafeInteger(price) || price < 1) {
    throw new RequestError("internal", "The rental has no valid price.");
  }
  return price;
}
