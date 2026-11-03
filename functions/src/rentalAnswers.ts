// Made with Claude.

import { FieldValue, Firestore, QueryDocumentSnapshot } from "firebase-admin/firestore";
import { AcceptInput, DateRange, RequestError, conflicts, readRange } from "./rentalRules";

/** Firestore names, as in "Firestore schema" in docs/ARCHITECTURE.md. */
export const RENTALS = "rentals";
export const LISTINGS = "listings";

/** What an acceptance did: the other requests it declined because their dates overlapped. */
export interface AcceptResult {
  declinedRentalIds: string[];
}

/**
 * Accepts the request [input.rentalId] for the lender [lenderId], in one transaction: the rental
 * becomes ACCEPTED with the deposit and the exact addresses, its dates join the listing's
 * `bookedRanges`, and every other REQUESTED rental of the same listing whose dates overlap (buffer
 * day included) becomes DECLINED.
 *
 * Refused with `not-found`, `permission-denied` (not the lender), `failed-precondition` (not
 * requested, or the dates were booked meanwhile). Nothing is written then.
 *
 * Not done yet: reserving the deposit from the borrower's balance. There is no wallet yet, so the
 * deposit is only recorded.
 */
export async function acceptRental(
  db: Firestore,
  lenderId: string,
  input: AcceptInput,
): Promise<AcceptResult> {
  const rentalRef = db.collection(RENTALS).doc(input.rentalId);
  return db.runTransaction(async (tx) => {
    const rentalSnap = await tx.get(rentalRef);
    const rental = rentalSnap.data();
    if (!rental) {
      throw new RequestError("not-found", "This rental does not exist.");
    }
    if (rental.ownerId !== lenderId) {
      throw new RequestError("permission-denied", "Only the lender can answer this request.");
    }
    if (rental.status !== "REQUESTED") {
      throw new RequestError("failed-precondition", "Only a requested rental can be accepted.");
    }
    const dates = readRange({ start: rental.startDate, end: rental.endDate });
    if (!dates) {
      throw new RequestError("internal", "The rental has no valid dates.");
    }

    const listingRef = db.collection(LISTINGS).doc(rental.listingId);
    const listingSnap = await tx.get(listingRef);
    if (!listingSnap.exists) {
      throw new RequestError("not-found", "The listing of this rental does not exist.");
    }
    const booked = listingBookedRanges(listingSnap.get("bookedRanges"));
    if (booked.some((range) => conflicts(range, dates))) {
      throw new RequestError("failed-precondition", "These dates are no longer free.");
    }

    // Equality filters only, so no composite index is needed; the date check is done here.
    const pendingSnap = await tx.get(
      db
        .collection(RENTALS)
        .where("listingId", "==", rental.listingId)
        .where("status", "==", "REQUESTED"),
    );
    const overlapping = pendingSnap.docs.filter(
      (doc) => doc.id !== input.rentalId && overlapsRequest(doc, dates),
    );

    tx.update(rentalRef, {
      status: "ACCEPTED",
      deposit: input.deposit,
      pickupAddress: input.pickupAddress,
      returnAddress: input.returnAddress,
    });
    tx.update(listingRef, {
      bookedRanges: FieldValue.arrayUnion({ start: dates.start, end: dates.end }),
    });
    for (const doc of overlapping) {
      tx.update(doc.ref, { status: "DECLINED" });
    }
    return { declinedRentalIds: overlapping.map((doc) => doc.id) };
  });
}

/**
 * Declines the request [rentalId] for the lender [lenderId]. Refused with `not-found`,
 * `permission-denied` or `failed-precondition` (not requested).
 */
export async function declineRental(db: Firestore, lenderId: string, rentalId: string): Promise<void> {
  const rentalRef = db.collection(RENTALS).doc(rentalId);
  await db.runTransaction(async (tx) => {
    const rental = (await tx.get(rentalRef)).data();
    if (!rental) {
      throw new RequestError("not-found", "This rental does not exist.");
    }
    if (rental.ownerId !== lenderId) {
      throw new RequestError("permission-denied", "Only the lender can answer this request.");
    }
    if (rental.status !== "REQUESTED") {
      throw new RequestError("failed-precondition", "Only a requested rental can be declined.");
    }
    tx.update(rentalRef, { status: "DECLINED" });
  });
}

/** The valid ranges of a listing's `bookedRanges`; malformed entries are ignored. */
function listingBookedRanges(value: unknown): DateRange[] {
  if (!Array.isArray(value)) return [];
  return value.map(readRange).filter((range): range is DateRange => range !== null);
}

/** True if a stored request's dates conflict with [dates], buffer days included. */
function overlapsRequest(doc: QueryDocumentSnapshot, dates: DateRange): boolean {
  const range = readRange({ start: doc.get("startDate"), end: doc.get("endDate") });
  return range !== null && conflicts(range, dates);
}
