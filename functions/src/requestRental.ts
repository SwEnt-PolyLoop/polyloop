// Made with Claude.

import { DocumentData, FieldValue, Firestore, getFirestore } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { toCallableError } from "./callable";
import {
  DateRange,
  RentalRequestInput,
  RequestError,
  conflicts,
  daysIn,
  dayNumber,
  isDateString,
  parseRentalRequest,
  readRange,
  requireVerifiedEpfl,
} from "./rentalRules";
import { zurichDay } from "./serverToday";
import { readWallet, writeRentalHold } from "./wallet";

/** The reason the app reads in `details` to tell a balance too low from any other refusal. */
export const INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";

const PUBLISHED = "PUBLISHED";
const REQUESTED = "REQUESTED";

/**
 * Creates the rental request [input.rentalId] for [borrowerId] and reserves its price, in one
 * transaction: `available -= totalPrice`, `heldForRentals += totalPrice`, and one `RENTAL_HELD` row
 * `rental_held_<rentalId>` with the amount `-totalPrice`. The lender, the title, the first photo,
 * the price per day and the areas are copied from the listing, the names from both profiles (""
 * when a name is missing or blank), and `totalPrice` is the price per day × the number of days.
 * Nothing but the ids and the dates comes from the client.
 *
 * It is idempotent: if the rental already exists with the same borrower, listing and dates, it
 * returns its id and changes nothing, so a retry never holds the price twice.
 *
 * @param db the Firestore database.
 * @param borrowerId the caller.
 * @param input the ids and the dates, already checked by `parseRentalRequest`.
 * @param now the current time, passed so that tests can set it.
 * @returns the id of the rental.
 * @throws RequestError `already-exists` (the id belongs to another request), `not-found` (no such
 *   listing), `permission-denied` (the caller's own listing), `failed-precondition` (the listing is
 *   not published, the dates are outside its availability window or no longer free, or the
 *   available balance is below the price, with the reason `INSUFFICIENT_FUNDS`), `invalid-argument`
 *   (the first day is in the past) or `internal` (the listing is broken). Nothing is written then.
 */
export async function createRentalRequest(
  db: Firestore,
  borrowerId: string,
  input: RentalRequestInput,
  now: Date = new Date(),
): Promise<string> {
  const rentalRef = db.collection("rentals").doc(input.rentalId);
  const listingRef = db.collection("listings").doc(input.listingId);
  await db.runTransaction(async (tx) => {
    const existing = (await tx.get(rentalRef)).data();
    if (existing) {
      if (isSameRequest(existing, borrowerId, input)) return;
      throw new RequestError("already-exists", "This request id is already used.");
    }

    const listing = (await tx.get(listingRef)).data();
    if (!listing) {
      throw new RequestError("not-found", "This listing does not exist.");
    }
    const ownerId = requireOwner(listing);
    // All reads come before the first write.
    const [borrowerProfile, ownerProfile, wallet] = await Promise.all([
      tx.get(db.collection("users").doc(borrowerId)),
      tx.get(db.collection("users").doc(ownerId)),
      readWallet(tx, db, borrowerId),
    ]);

    if (listing.status !== PUBLISHED) {
      throw new RequestError("failed-precondition", "This listing is not available.");
    }
    if (ownerId === borrowerId) {
      throw new RequestError("permission-denied", "You cannot rent your own listing.");
    }
    const dates: DateRange = { start: input.startDate, end: input.endDate };
    requireFreeDates(listing, dates, zurichDay(now));
    const pricePerDay = requirePricePerDay(listing);
    const totalPrice = pricePerDay * daysIn(dates);
    if (wallet.available < totalPrice) {
      throw new RequestError(
        "failed-precondition",
        "Insufficient funds to reserve the rental price.",
        { reason: INSUFFICIENT_FUNDS },
      );
    }

    const title = text(listing.title);
    tx.create(rentalRef, {
      listingId: input.listingId,
      listingTitle: title,
      listingPhotoUrl: firstPhoto(listing),
      ownerId,
      ownerName: text(ownerProfile.get("name")).trim(),
      borrowerId,
      borrowerName: text(borrowerProfile.get("name")).trim(),
      participants: [ownerId, borrowerId],
      startDate: input.startDate,
      endDate: input.endDate,
      status: REQUESTED,
      pricePerDay,
      totalPrice,
      pickupArea: text(listing.pickupArea),
      dropOffArea: text(listing.dropOffArea),
      pickupAddress: "",
      returnAddress: "",
      createdAt: FieldValue.serverTimestamp(),
      lastMessage: "",
      lastMessageAt: FieldValue.serverTimestamp(),
    });
    writeRentalHold(tx, db, borrowerId, wallet, totalPrice, input.rentalId, title);
  });
  return input.rentalId;
}

/**
 * Callable `requestRental` (see docs/ARCHITECTURE.md): the borrower sends a rental request and its
 * price is reserved. Input: `{rentalId, listingId, startDate, endDate}` only, `rentalId` being a new
 * id generated by the app. Returns `{rentalId}`.
 */
export const requestRental = onCall(async (request) => {
  try {
    const uid = requireVerifiedEpfl(request.auth);
    const rentalId = await createRentalRequest(
      getFirestore(),
      uid,
      parseRentalRequest(request.data),
    );
    return { rentalId };
  } catch (error) {
    throw toCallableError(error);
  }
});

/** True if the stored rental is the very request [input] by [borrowerId]: a retry. */
function isSameRequest(
  rental: DocumentData,
  borrowerId: string,
  input: RentalRequestInput,
): boolean {
  return (
    rental.borrowerId === borrowerId &&
    rental.listingId === input.listingId &&
    rental.startDate === input.startDate &&
    rental.endDate === input.endDate
  );
}

function requireOwner(listing: DocumentData): string {
  if (typeof listing.ownerId !== "string" || listing.ownerId === "") {
    throw new RequestError("internal", "The listing has no owner.");
  }
  return listing.ownerId;
}

/** The price per day of the listing, a whole number from 1, or an `internal` error. */
function requirePricePerDay(listing: DocumentData): number {
  const price = listing.pricePerDay;
  if (typeof price !== "number" || !Number.isInteger(price) || price < 1) {
    throw new RequestError("internal", "The listing has no valid price.");
  }
  return price;
}

/**
 * Refuses [dates] that start before [today], fall outside the listing's availability window or
 * clash with a booking (the buffer days included).
 */
function requireFreeDates(listing: DocumentData, dates: DateRange, today: string): void {
  if (dayNumber(dates.start) < dayNumber(today)) {
    throw new RequestError("invalid-argument", "The first day is in the past.");
  }
  const { availableFrom, availableTo } = listing;
  if (!isDateString(availableFrom) || !isDateString(availableTo)) {
    throw new RequestError("internal", "The listing has no availability window.");
  }
  if (
    dayNumber(dates.start) < dayNumber(availableFrom) ||
    dayNumber(dates.end) > dayNumber(availableTo)
  ) {
    throw new RequestError("failed-precondition", "These dates are outside the availability.");
  }
  const booked = Array.isArray(listing.bookedRanges) ? listing.bookedRanges : [];
  const clash = booked
    .map(readRange)
    .some((range): boolean => range !== null && conflicts(range, dates));
  if (clash) {
    throw new RequestError("failed-precondition", "These dates are no longer free.");
  }
}

/** The first photo of the listing, or "" when it has none. */
function firstPhoto(listing: DocumentData): string {
  const photos = listing.photoUrls;
  return Array.isArray(photos) ? text(photos[0]) : "";
}

/** [value] if it is a string, otherwise "". */
function text(value: unknown): string {
  return typeof value === "string" ? value : "";
}
