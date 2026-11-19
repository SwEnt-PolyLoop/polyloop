// Made with Claude.

import { DocumentData, DocumentSnapshot, FieldValue, Firestore } from "firebase-admin/firestore";
import {
  DateRange,
  RequestError,
  asFields,
  bookedRanges,
  conflicts,
  dayNumber,
  isDateString,
  readRange,
} from "./rentalRules";
import { zurichDay } from "./serverToday";

/** Firestore names, as in "Firestore schema" in docs/ARCHITECTURE.md. */
const RENTALS = "rentals";
const LISTINGS = "listings";
const USERS = "users";
const PUBLISHED = "PUBLISHED";

/** What the borrower sends: the listing and the days, both included. */
export interface RentalRequestInput {
  listingId: string;
  startDate: string;
  endDate: string;
}

/**
 * The `details.reason` of a refusal because the days cannot be requested (booked, already
 * started, or outside the availability window), which the app reads.
 */
export const DATES_UNAVAILABLE = "DATES_UNAVAILABLE";

/**
 * The input of `requestRental`: a listing id and two real dates, the start not after the end.
 * Throws an `invalid-argument` error naming the first field that is wrong.
 */
export function parseRentalRequestInput(data: unknown): RentalRequestInput {
  const fields = asFields(data);
  const listingId = typeof fields.listingId === "string" ? fields.listingId.trim() : "";
  if (listingId === "" || listingId.includes("/")) {
    throw new RequestError("invalid-argument", "listingId is required.");
  }
  for (const field of ["startDate", "endDate"]) {
    if (!isDateString(fields[field])) {
      throw new RequestError("invalid-argument", `${field} must be a YYYY-MM-DD date.`);
    }
  }
  const dates = readRange({ start: fields.startDate, end: fields.endDate });
  if (!dates) {
    throw new RequestError("invalid-argument", "The start date is after the end date.");
  }
  return { listingId, startDate: dates.start, endDate: dates.end };
}

/**
 * Creates the request of [borrowerId] for [input], in one transaction, and returns its id. Only
 * the days come from the borrower: every other field is copied from the listing and the two
 * profiles. The listing is read in the same transaction as the rental is written, so two requests
 * sent at once are both checked against the same `bookedRanges`. No money is reserved yet.
 *
 * Sending the same request again is safe: if the caller already has a `REQUESTED` rental of this
 * listing for exactly these days, its id is returned and nothing is written. So when the answer is
 * lost after the server committed, or the app cannot read the new rental, a retry gets the same
 * rental instead of a second one.
 *
 * @param db the Firestore database.
 * @param borrowerId the caller.
 * @param input the listing and the days, already checked by `parseRentalRequestInput`.
 * @param now the current time, passed so that tests can set it.
 * @throws RequestError `not-found` (no such listing), `permission-denied` (the caller's own
 *   listing), `already-exists` (the caller already has a request for this listing with other
 *   days in common) or `failed-precondition` (the listing is not published, or, with `details.reason`
 *   [DATES_UNAVAILABLE], the first day is not after today, the days are outside the listing's
 *   availability or they conflict with a booking, buffer day included). Nothing is written then.
 */
export async function createRentalRequest(
  db: Firestore,
  borrowerId: string,
  input: RentalRequestInput,
  now: Date = new Date(),
): Promise<string> {
  const listingRef = db.collection(LISTINGS).doc(input.listingId);
  const rentalRef = db.collection(RENTALS).doc();
  const dates = { start: input.startDate, end: input.endDate };

  return db.runTransaction(async (tx) => {
    const listing = (await tx.get(listingRef)).data();
    if (!listing) {
      throw new RequestError("not-found", "This listing does not exist.");
    }
    const ownerId = listing.ownerId;
    if (typeof ownerId !== "string" || ownerId === "") {
      throw new RequestError("internal", "The listing has no owner.");
    }
    if (ownerId === borrowerId) {
      throw new RequestError("permission-denied", "You cannot rent your own listing.");
    }
    const ownRequests = await tx.get(
      db
        .collection(RENTALS)
        .where("listingId", "==", listingRef.id)
        .where("borrowerId", "==", borrowerId)
        .where("status", "==", "REQUESTED"),
    );
    // The same request sent again (e.g. a retry after a lost answer): answer with the first one.
    const sameRequest = ownRequests.docs.find(
      (doc) => doc.get("startDate") === dates.start && doc.get("endDate") === dates.end,
    );
    if (sameRequest) return sameRequest.id;
    if (listing.status !== PUBLISHED) {
      throw new RequestError("failed-precondition", "This listing is not published.");
    }
    requireAvailableDays(listing, dates, now);
    if (bookedRanges(listing.bookedRanges).some((range) => conflicts(range, dates))) {
      throw datesUnavailable("These dates are no longer available.");
    }
    if (ownRequests.docs.some((doc) => sharesADay(doc, dates))) {
      throw new RequestError(
        "already-exists",
        "You already sent a request for these dates.",
      );
    }
    const [owner, borrower] = await tx.getAll(
      db.collection(USERS).doc(ownerId),
      db.collection(USERS).doc(borrowerId),
    );

    const pricePerDay = listing.pricePerDay;
    if (typeof pricePerDay !== "number" || !Number.isInteger(pricePerDay)) {
      throw new RequestError("internal", "The listing has no valid price.");
    }
    const created = FieldValue.serverTimestamp();
    tx.create(rentalRef, {
      listingId: listingRef.id,
      listingTitle: textOf(listing.title),
      listingPhotoUrl: firstPhoto(listing.photoUrls),
      ownerId,
      ownerName: textOf(owner.get("name")),
      borrowerId,
      borrowerName: textOf(borrower.get("name")),
      participants: [ownerId, borrowerId],
      startDate: dates.start,
      endDate: dates.end,
      status: "REQUESTED",
      pricePerDay,
      totalPrice: pricePerDay * (dayNumber(dates.end) - dayNumber(dates.start) + 1),
      pickupArea: textOf(listing.pickupArea),
      dropOffArea: textOf(listing.dropOffArea),
      pickupAddress: "",
      returnAddress: "",
      createdAt: created,
      lastMessage: "",
      lastMessageAt: created,
    });
    return rentalRef.id;
  });
}

/**
 * Refuses [dates] if the first day is not after today in Zurich at [now], or if they are not inside
 * the listing's availability window. A request starting today could get no offer: `makeOffer`
 * refuses once the first day has started, and `expireOffers` declines it.
 */
function requireAvailableDays(listing: DocumentData, dates: DateRange, now: Date): void {
  if (dayNumber(dates.start) <= dayNumber(zurichDay(now))) {
    throw datesUnavailable("The rental must start tomorrow at the earliest.");
  }
  const window = readRange({ start: listing.availableFrom, end: listing.availableTo });
  if (
    !window ||
    dayNumber(dates.start) < dayNumber(window.start) ||
    dayNumber(dates.end) > dayNumber(window.end)
  ) {
    throw datesUnavailable("These dates are outside the listing's availability.");
  }
}

/** A refusal of the days, with `details.reason` [DATES_UNAVAILABLE] for the app. */
function datesUnavailable(message: string): RequestError {
  return new RequestError("failed-precondition", message, { reason: DATES_UNAVAILABLE });
}

/** True if a stored request's days share at least one day with [dates] (no buffer). */
function sharesADay(doc: DocumentSnapshot, dates: DateRange): boolean {
  const range = readRange({ start: doc.get("startDate"), end: doc.get("endDate") });
  return (
    range !== null &&
    dayNumber(range.start) <= dayNumber(dates.end) &&
    dayNumber(dates.start) <= dayNumber(range.end)
  );
}

/** The listing's first photo URL, or "" when it has none (as the app did). */
function firstPhoto(value: unknown): string {
  return Array.isArray(value) && typeof value[0] === "string" ? value[0] : "";
}

/** [value] trimmed if it is a string, or "". */
function textOf(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}
