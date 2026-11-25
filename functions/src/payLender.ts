// Made with Claude.

import { DocumentData, Firestore, Transaction, getFirestore } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { toCallableError } from "./callable";
import {
  Parties,
  RequestError,
  listingTitleOf,
  parseRentalId,
  requireParties,
  requireVerifiedEpfl,
} from "./rentalRules";
import {
  RENTAL_PAYMENT,
  Wallet,
  hasTransaction,
  readWallet,
  rentalHeldRowId,
  writeWallet,
} from "./wallet";

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

/**
 * Pays the lender of the picked-up rental [rentalId] the rental's `totalPrice`, in one transaction,
 * from the price held when the request was sent: borrower `heldForRentals -= price`, lender
 * `available += price` (the lender's wallet is created if it does not exist), and one
 * `RENTAL_PAYMENT` row `+price` with the id `rental_payment_<rentalId>` and the item's title in the
 * lender's history. The borrower's history gets no row: their `available` does not change, it went
 * down when the price was held. A rental sent before the price was held at request (it has no
 * `rental_price_held_<rentalId>` row) is paid from the borrower's `available` instead, with a
 * `-price` row in their history.
 * The price, the people and the title all come from the rental. The checks run in this order:
 * the rental exists, the caller is a participant, the borrower and the lender are valid and
 * different, then, if the lender's row already exists, the call succeeds and changes nothing
 * (whatever the rental's status is by now; the row is looked for in the same transaction), then
 * the status is one of [PAID_STATUSES], the price is valid and covered.
 *
 * @param db the Firestore database.
 * @param callerId the caller, who must be the rental's borrower or its lender.
 * @param rentalId the rental to pay.
 * @throws RequestError `not-found`, `permission-denied` (not the borrower or the lender),
 *   `failed-precondition` (no row yet and the rental is not picked up, or neither the borrower's
 *   `heldForRentals` nor their `available` covers the price) or `internal` (the rental has no
 *   valid borrower or lender, the same one for both, or, with no row yet, no valid price). Nothing
 *   is written then.
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
    const payment = await readLenderPayment(tx, db, rental, parties, rentalId);
    writeLenderPayment(tx, db, rentalId, payment);
  });
}

/** What a payment moves, read inside the caller's transaction (see [readLenderPayment]). */
export interface LenderPayment extends Parties {
  price: number;
  itemTitle: string;
  borrower: Wallet;
  lender: Wallet;
  // False for a rental sent before the price was held at request: it is taken from `available`.
  fromHeld: boolean;
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
 * and both wallets. The price comes from the borrower's `heldForRentals` when this rental's price
 * was held at request, which its `rental_price_held_<rentalId>` row tells. A rental sent before
 * that has no row, and its price is taken from `available`, as `payLender` did before; looking at
 * the row, not at `heldForRentals`, keeps such a rental from taking the price another request
 * holds.
 *
 * @param tx the transaction to read in.
 * @param db the Firestore database.
 * @param rental the rental's data, read in the same transaction.
 * @param parties the rental's borrower and lender (see [requireParties]).
 * @param rentalId the rental's id, which names the row of its held price.
 * @returns what the payment moves, for [writeLenderPayment].
 * @throws RequestError `internal` for a missing or invalid price, or a held price that
 *   `heldForRentals` no longer covers (the stored data is broken); `failed-precondition` when an
 *   unheld price is above `available`.
 */
export async function readLenderPayment(
  tx: Transaction,
  db: Firestore,
  rental: DocumentData,
  parties: Parties,
  rentalId: string,
): Promise<LenderPayment> {
  const price = requirePrice(rental);
  const borrower = await readWallet(tx, db, parties.borrowerId);
  const fromHeld = await hasTransaction(tx, db, parties.borrowerId, rentalHeldRowId(rentalId));
  if (fromHeld && borrower.heldForRentals < price) {
    // Paying anyway would make heldForRentals negative, or create PolyPoints.
    throw new RequestError("internal", "The held rental price does not cover the payment.");
  }
  if (!fromHeld && borrower.available < price) {
    throw new RequestError("failed-precondition", "Insufficient funds to pay the rental price.");
  }
  const lender = await readWallet(tx, db, parties.lenderId);
  const itemTitle = listingTitleOf(rental);
  return { ...parties, price, itemTitle, borrower, lender, fromHeld };
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
  if (payment.fromHeld) {
    // No row for the borrower: their available balance went down when the price was held.
    writeWallet(tx, db, payment.borrowerId, {
      ...borrower,
      heldForRentals: borrower.heldForRentals - price,
    });
  } else {
    writeWallet(
      tx,
      db,
      payment.borrowerId,
      { ...borrower, available: borrower.available - price },
      { id, type: RENTAL_PAYMENT, amount: -price, itemTitle },
    );
  }
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
 * borrower holds for rentals (or, for a rental sent before the price was held, their available
 * balance) to the lender's available balance. Input: `{rentalId}` only. Returns nothing, so the lender learns only whether the price is covered, never the borrower's balances.
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
