// Made with Claude.

import { DocumentData, FieldValue, Firestore, Transaction } from "firebase-admin/firestore";
import { RENTAL_CHANGED } from "./handover";
import { CODE_HASH_FIELDS, HANDOVER_CODES } from "./handoverCode";
import { writeWithSystemMessage } from "./negotiation";
import {
  DateRange,
  RequestError,
  firstDayStart,
  Parties,
  isValidDeposit,
  readRange,
  requireParties,
  requirePerson,
} from "./rentalRules";
import { DepositSettlement, readDepositSettlement, writeDepositSettlement } from "./wallet";

/** The status of a cancelled rental. */
export const CANCELLED = "CANCELLED";

/** The reason the app reads (`details.reason`) when an accepted rental's first day has come. */
export const TOO_LATE = "TOO_LATE";

const REQUESTED = "REQUESTED";
const ACCEPTED = "ACCEPTED";

/** What cancelling an accepted rental undoes, read before any write of the transaction. */
interface AcceptedReads {
  dates: DateRange;
  /** False when the listing was deleted: its booked days are gone with it. */
  listingExists: boolean;
  listingId: string;
  parties: Parties;
  /** The wallets, and whether the deposit was held and already given back. */
  settlement: DepositSettlement;
}

/**
 * Cancels the rental [rentalId] for [callerId], one of its two participants, in one transaction:
 * the rental becomes CANCELLED, with no waiting offer and no offer addresses left, and a SYSTEM
 * message "Cancelled", seen by the caller. An accepted rental also gets back what its acceptance set
 * up: its days leave the listing's `bookedRanges`, both handover codes and their fingerprints are
 * deleted, and the deposit goes back to the borrower through `writeDepositSettlement`
 * (`DEPOSIT_RELEASED`), only if it was held (`deposit_held_<rentalId>` exists). The checks run in
 * this order: the rental exists, the caller is a participant, then, if it is already cancelled, the
 * call succeeds and changes nothing; otherwise it is REQUESTED (at any time) or ACCEPTED until the
 * day before its first day, in Zurich at [now].
 *
 * @param db the Firestore database.
 * @param callerId the participant cancelling.
 * @param rentalId the rental to cancel.
 * @param now the current time, passed so that tests can set it.
 * @throws RequestError `not-found`; `permission-denied` (not a participant); `failed-precondition`
 *   with the reason `RENTAL_CHANGED` (neither REQUESTED nor ACCEPTED) or `TOO_LATE` (an accepted
 *   rental on or after its first day); `internal` for an accepted rental with no valid dates,
 *   listing, parties or deposit, or, from `writeDepositSettlement`, a held deposit that the
 *   borrower's `heldAsDeposits` does not cover. Nothing is written then.
 */
export async function cancelInTransaction(
  db: Firestore,
  callerId: string,
  rentalId: string,
  now: Date = new Date(),
): Promise<void> {
  const rentalRef = db.collection("rentals").doc(rentalId);
  await db.runTransaction(async (tx) => {
    const rental = (await tx.get(rentalRef)).data();
    if (!rental) {
      throw new RequestError("not-found", "This rental does not exist.");
    }
    if (callerId !== rental.ownerId && callerId !== rental.borrowerId) {
      throw new RequestError("permission-denied", "Only the two participants can cancel a rental.");
    }
    // Before the status check: a cancel sent twice (a retry after a lost answer) finds the rental
    // already cancelled, and must succeed rather than fail as if the rental had changed.
    if (rental.status === CANCELLED) return;
    if (rental.status !== REQUESTED && rental.status !== ACCEPTED) {
      throw new RequestError("failed-precondition", "This rental can no longer be cancelled.", {
        reason: RENTAL_CHANGED,
      });
    }
    // A request holds no money and blocks no day, so it can be withdrawn at any time, even on or
    // after its first day. An accepted rental is locked from its first day: the 1-day buffer before
    // it is what gives the lender time to cancel it if the previous rental went wrong.
    const accepted =
      rental.status === ACCEPTED ? await readAccepted(tx, db, rentalId, rental, now) : null;

    tx.delete(rentalRef.collection("private").doc("addresses"));
    writeWithSystemMessage(
      tx,
      rentalRef,
      "Cancelled",
      {
        status: CANCELLED,
        offer: FieldValue.delete(),
        ...(accepted
          ? {
              [CODE_HASH_FIELDS.PICKUP]: FieldValue.delete(),
              [CODE_HASH_FIELDS.RETURN]: FieldValue.delete(),
            }
          : {}),
      },
      callerId,
    );
    if (!accepted) return;
    // Both handover codes are made at acceptance, so a cancel removes both.
    tx.delete(rentalRef.collection(HANDOVER_CODES).doc("PICKUP"));
    tx.delete(rentalRef.collection(HANDOVER_CODES).doc("RETURN"));
    if (accepted.listingExists) {
      // The same object `accept` added, so arrayRemove finds it.
      // arrayRemove keeps the order the Security Rules rely on (#341).
      tx.update(db.collection("listings").doc(accepted.listingId), {
        bookedRanges: FieldValue.arrayRemove({
          start: accepted.dates.start,
          end: accepted.dates.end,
        }),
      });
    }
    // Before pickup no late fee exists yet, so the whole deposit goes back.
    writeDepositSettlement(tx, db, accepted.parties, rentalId, rental, accepted.settlement, {
      kind: "release",
    });
  });
}

/**
 * The reads of an accepted rental's cancellation, after refusing it from its first day on. The
 * deposit's settlement state tells whether it was held and whether it is already released or split.
 */
async function readAccepted(
  tx: Transaction,
  db: Firestore,
  rentalId: string,
  rental: DocumentData,
  now: Date,
): Promise<AcceptedReads> {
  const dates = readRange({ start: rental.startDate, end: rental.endDate });
  if (!dates) {
    throw new RequestError("internal", "The rental has no valid dates.");
  }
  if (now.getTime() >= firstDayStart(dates.start)) {
    throw new RequestError(
      "failed-precondition",
      "An accepted rental can only be cancelled until the day before pickup.",
      { reason: TOO_LATE },
    );
  }
  // requirePerson checks any id that must be a non-empty string, not only a person's.
  const listingId = requirePerson(rental.listingId, "listing");
  const listingExists = (await tx.get(db.collection("listings").doc(listingId))).exists;
  const parties = requireParties(rental);
  if (!isValidDeposit(rental.deposit)) {
    throw new RequestError("internal", "The rental has no valid deposit.");
  }
  const settlement = await readDepositSettlement(tx, db, parties, rentalId);
  return { dates, listingExists, listingId, parties, settlement };
}
