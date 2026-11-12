// Made with Claude.

import { DocumentData, Firestore, getFirestore } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { toCallableError } from "./callable";
import {
  RequestError,
  listingTitleOf,
  parseRentalId,
  requirePerson,
  requireVerifiedEpfl,
} from "./rentalRules";
import { hasTransaction, readWallet, writeWallet } from "./wallet";

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
 * Pays the lender of the picked-up rental [rentalId] the rental's `totalPrice`, in one transaction:
 * borrower `available -= price`, lender `available += price` (the lender's wallet is created if it
 * does not exist), and one `RENTAL_PAYMENT` row with the id `rental_payment_<rentalId>` in each
 * history, `-price` for the borrower and `+price` for the lender, both with the item's title. The
 * price, the people and the title all come from the rental. The checks run in this order: the rental
 * exists, the caller is a participant, the borrower and the lender are valid and different, then, if
 * the borrower's row already exists, the call succeeds and changes nothing (whatever the rental's
 * status is by now; the row is looked for in the same transaction, and the lender's row is written
 * with it), then the status is one of [PAID_STATUSES], the price is valid and the borrower's balance covers it.
 *
 * @param db the Firestore database.
 * @param callerId the caller, who must be the rental's borrower or its lender.
 * @param rentalId the rental to pay.
 * @throws RequestError `not-found`, `permission-denied` (not the borrower or the lender),
 *   `failed-precondition` (no row yet and the rental is not picked up, or the borrower's available
 *   balance is below the price) or `internal` (the rental has no valid borrower or lender, the same
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
    const borrowerId = requirePerson(rental.borrowerId, "borrower");
    const lenderId = requirePerson(rental.ownerId, "lender");
    if (borrowerId === lenderId) {
      throw new RequestError("internal", "The rental has the same borrower and lender.");
    }
    const rowId = `rental_payment_${rentalId}`;
    if (await hasTransaction(tx, db, borrowerId, rowId)) return;
    if (!PAID_STATUSES.includes(rental.status)) {
      throw new RequestError("failed-precondition", "Only a picked-up rental can be paid.");
    }
    const price = requirePrice(rental);
    const borrower = await readWallet(tx, db, borrowerId);
    if (borrower.available < price) {
      throw new RequestError("failed-precondition", "Insufficient funds to pay the rental.");
    }
    const lender = await readWallet(tx, db, lenderId);
    const itemTitle = listingTitleOf(rental);
    writeWallet(
      tx,
      db,
      borrowerId,
      { ...borrower, available: borrower.available - price },
      { id: rowId, type: RENTAL_PAYMENT, amount: -price, itemTitle },
    );
    writeWallet(
      tx,
      db,
      lenderId,
      { ...lender, available: lender.available + price },
      { id: rowId, type: RENTAL_PAYMENT, amount: price, itemTitle },
    );
  });
}

/**
 * Callable `payLender` (see docs/ARCHITECTURE.md): moves a picked-up rental's price from the borrower's
 * available balance to the lender's. Input: `{rentalId}` only. Returns nothing, so the lender learns
 * only whether the borrower's balance covers the price (the insufficient funds error), never the
 * balance itself.
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
