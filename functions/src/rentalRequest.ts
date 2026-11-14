// Made with Claude.

import { DocumentData, FieldValue, Firestore } from "firebase-admin/firestore";
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

/** The `details.reason` of a refusal because the days are booked, which the app reads. */
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
 * @param db the Firestore database.
 * @param borrowerId the caller.
 * @param input the listing and the days, already checked by `parseRentalRequestInput`.
 * @param now the current time, passed so that tests can set it.
 * @throws RequestError `not-found` (no such listing), `permission-denied` (the caller's own
 *   listing), `invalid-argument` (the days are in the past or outside the listing's availability)
 *   or `failed-precondition` (the listing is not published, or the days conflict with a booking,
 *   buffer day included, with `details.reason` [DATES_UNAVAILABLE]). Nothing is written then.
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

  await db.runTransaction(async (tx) => {
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
    if (listing.status !== PUBLISHED) {
      throw new RequestError("failed-precondition", "This listing is not published.");
    }
    requireAvailableDays(listing, dates, now);
    if (bookedRanges(listing.bookedRanges).some((range) => conflicts(range, dates))) {
      throw new RequestError("failed-precondition", "These dates are no longer available.", {
        reason: DATES_UNAVAILABLE,
      });
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
  });
  return rentalRef.id;
}

/**
 * Refuses [dates] if the first day has passed in Zurich at [now], or if they are not inside the
 * listing's availability window.
 */
function requireAvailableDays(listing: DocumentData, dates: DateRange, now: Date): void {
  if (dayNumber(dates.start) < dayNumber(zurichDay(now))) {
    throw new RequestError("invalid-argument", "The rental cannot start in the past.");
  }
  const window = readRange({ start: listing.availableFrom, end: listing.availableTo });
  if (
    !window ||
    dayNumber(dates.start) < dayNumber(window.start) ||
    dayNumber(dates.end) > dayNumber(window.end)
  ) {
    throw new RequestError(
      "invalid-argument",
      "These dates are outside the listing's availability.",
    );
  }
}

/** The listing's first photo URL, or "" when it has none (as the app did). */
function firstPhoto(value: unknown): string {
  return Array.isArray(value) && typeof value[0] === "string" ? value[0] : "";
}

/** [value] trimmed if it is a string, or "". */
function textOf(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}
