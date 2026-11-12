// Made with Claude.

import { DocumentData, Firestore, getFirestore } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { toCallableError } from "./callable";
import {
  RequestError,
  isValidDeposit,
  listingTitleOf,
  parseRentalId,
  requirePerson,
  requireVerifiedEpfl,
} from "./rentalRules";
import {
  depositRowId,
  hasTransaction,
  readWallet,
  requireFunds,
  writeDepositHold,
} from "./wallet";

const ACCEPTED = "ACCEPTED";

/**
 * Holds the deposit of the accepted rental [rentalId] in the borrower's wallet, in one transaction:
 * `available -= deposit`, `heldAsDeposits += deposit`, and one `DEPOSIT_HELD` row with the id
 * `deposit_held_<rentalId>`, the amount `-deposit` and the item's title. The amount, the people and
 * the title all come from the rental. The checks run in this order: the rental exists, the caller is
 * a participant, the borrower is valid, then, if the row already exists, the call succeeds and
 * changes nothing (whatever the rental's status is by now; the row is looked for in the same
 * transaction), then the status is ACCEPTED, the deposit is valid and the balance covers it.
 *
 * @param db the Firestore database.
 * @param callerId the caller, who must be the rental's borrower or its lender.
 * @param rentalId the rental whose deposit is held.
 * @throws RequestError `not-found`, `permission-denied` (not the borrower or the lender),
 *   `failed-precondition` (no row yet and the rental is not ACCEPTED, or the borrower's available
 *   balance is below the deposit) or `internal` (the rental has no valid borrower or, with no row
 *   yet, no valid deposit). Nothing is written then.
 */
export async function reserveRentalDeposit(
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
    if (await hasTransaction(tx, db, borrowerId, depositRowId(rentalId))) return;
    if (rental.status !== ACCEPTED) {
      throw new RequestError(
        "failed-precondition",
        "Only an accepted rental has a deposit to hold.",
      );
    }
    const deposit = requireDeposit(rental);
    const wallet = await readWallet(tx, db, borrowerId);
    requireFunds(wallet, deposit);
    writeDepositHold(tx, db, borrowerId, wallet, deposit, rentalId, listingTitleOf(rental));
  });
}

/**
 * Callable `reserveDeposit` (see docs/ARCHITECTURE.md): holds the deposit of an accepted rental in
 * the borrower's wallet. Input: `{rentalId}` only. Returns nothing, so the lender learns only
 * whether the borrower's balance covers the deposit (the insufficient funds error), never the
 * balance itself.
 */
export const reserveDeposit = onCall(async (request) => {
  try {
    const uid = requireVerifiedEpfl(request.auth);
    await reserveRentalDeposit(getFirestore(), uid, parseRentalId(request.data));
    return {};
  } catch (error) {
    throw toCallableError(error);
  }
});

/** The rental's deposit, a whole number from 1 to [MAX_DEPOSIT], or a refusal. */
function requireDeposit(rental: DocumentData): number {
  const deposit = rental.deposit;
  if (!isValidDeposit(deposit)) {
    throw new RequestError("internal", "The rental has no valid deposit.");
  }
  return deposit;
}
