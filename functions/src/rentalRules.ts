// Made with Claude.

/**
 * Pure rules for the deposit negotiation: the buffer-day overlap, the offer expiry, the input checks
 * and the caller checks. No Firestore and no Firebase SDK here, so they are tested without the
 * emulator. A refusal is a RequestError; the callable wrappers turn it into the HttpsError the
 * client gets (see callable.ts).
 */

/** The error codes a refusal can carry; the same names as Firebase's HttpsError codes. */
export type RequestErrorCode =
  | "invalid-argument"
  | "unauthenticated"
  | "permission-denied"
  | "not-found"
  | "failed-precondition"
  | "internal";

/**
 * A refused request: a code, a message for the user, and, when the request lost a race, the reason
 * the app reads in `details` (e.g. `{reason: "STAGE_CHANGED"}`, see disputeRules.ts).
 */
export class RequestError extends Error {
  constructor(
    readonly code: RequestErrorCode,
    message: string,
    readonly details?: { reason: string },
  ) {
    super(message);
    this.name = "RequestError";
  }
}

/** The 1-day buffer before and after every booking (ARCHITECTURE.md, "Product rules"). */
export const BUFFER_DAYS = 1;

/** The highest deposit, in PolyPoints: the same cap as a listing price. */
export const MAX_DEPOSIT = 99_999;

/** How long an offer waits for an answer, unless the rental starts sooner. */
export const OFFER_LIFETIME_DAYS = 7;

const MILLISECONDS_PER_DAY = 86_400_000;

/** A stretch of days, both included, as YYYY-MM-DD strings (the format of the rental documents). */
export interface DateRange {
  start: string;
  end: string;
}

/** The fields the lender sends with an offer. */
export interface OfferInput {
  rentalId: string;
  deposit: number;
  pickupAddress: string;
  returnAddress: string;
}

/** An answer to an offer. */
export type OfferAnswer =
  | { rentalId: string; action: "ACCEPT" | "DECLINE" }
  | { rentalId: string; action: "COUNTER"; deposit: number };

/** True for a YYYY-MM-DD string that is a real calendar date. */
export function isDateString(value: unknown): value is string {
  if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const day = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(day.getTime()) && day.toISOString().startsWith(value);
}

/** The day number of a YYYY-MM-DD string, counted from 1970-01-01 (UTC). */
export function dayNumber(date: string): number {
  return Date.parse(`${date}T00:00:00Z`) / MILLISECONDS_PER_DAY;
}

/**
 * True if [a] and [b] cannot both be booked: their days, each widened by the buffer day on both
 * sides, share at least one day. Two rentals that touch only through a buffer day conflict; two
 * rentals with one free day between them do not.
 */
export function conflicts(a: DateRange, b: DateRange): boolean {
  return (
    dayNumber(a.start) - BUFFER_DAYS <= dayNumber(b.end) &&
    dayNumber(b.start) <= dayNumber(a.end) + BUFFER_DAYS
  );
}

/**
 * Reads a stored date range (`{start, end}`, as in `bookedRanges`), or returns null if it is not
 * one. A range whose end is before its start is refused too.
 */
export function readRange(value: unknown): DateRange | null {
  if (typeof value !== "object" || value === null) return null;
  const { start, end } = value as Record<string, unknown>;
  if (!isDateString(start) || !isDateString(end)) return null;
  if (dayNumber(end) < dayNumber(start)) return null;
  return { start, end };
}

/**
 * When an offer made at [now] expires: after [OFFER_LIFETIME_DAYS], or at the start of the rental's
 * first day ([startDate], in UTC like every date here) if that comes sooner.
 */
export function offerExpiry(now: Date, startDate: string): Date {
  const afterLifetime = now.getTime() + OFFER_LIFETIME_DAYS * MILLISECONDS_PER_DAY;
  return new Date(Math.min(afterLifetime, Date.parse(`${startDate}T00:00:00Z`)));
}

/** True for a whole deposit from 1 to [MAX_DEPOSIT]. */
export function isValidDeposit(value: unknown): value is number {
  return typeof value === "number" && Number.isInteger(value) && value >= 1 && value <= MAX_DEPOSIT;
}

/** A whole deposit from 1 to [MAX_DEPOSIT], or an `invalid-argument` error. */
export function parseDeposit(value: unknown): number {
  if (!isValidDeposit(value)) {
    throw new RequestError(
      "invalid-argument",
      `The deposit must be a whole number from 1 to ${MAX_DEPOSIT}.`,
    );
  }
  return value;
}

/**
 * The input of an offer: a rental id, a valid deposit and two non-blank addresses (trimmed). Throws
 * an `invalid-argument` error naming the first field that is wrong.
 */
export function parseOfferInput(data: unknown): OfferInput {
  const fields = asFields(data);
  return {
    rentalId: parseText(fields.rentalId, "rentalId"),
    deposit: parseDeposit(fields.deposit),
    pickupAddress: parseText(fields.pickupAddress, "pickupAddress"),
    returnAddress: parseText(fields.returnAddress, "returnAddress"),
  };
}

/**
 * The input of an answer to an offer: a rental id, an action, and a valid deposit for `COUNTER`
 * only. Throws an `invalid-argument` error if one is wrong.
 */
export function parseOfferAnswer(data: unknown): OfferAnswer {
  const fields = asFields(data);
  const rentalId = parseText(fields.rentalId, "rentalId");
  switch (fields.action) {
    case "ACCEPT":
    case "DECLINE":
      return { rentalId, action: fields.action };
    case "COUNTER":
      return { rentalId, action: "COUNTER", deposit: parseDeposit(fields.deposit) };
    default:
      throw new RequestError("invalid-argument", "The action must be ACCEPT, DECLINE or COUNTER.");
  }
}

/** The input of a decline: only the rental id. */
export function parseRentalId(data: unknown): string {
  return parseText(asFields(data).rentalId, "rentalId");
}

/** A non-blank string, trimmed, or an `invalid-argument` error naming [field]. */
export function parseText(value: unknown, field: string): string {
  const text = typeof value === "string" ? value.trim() : "";
  if (text === "") {
    throw new RequestError("invalid-argument", `${field} is required.`);
  }
  return text;
}

/**
 * Same rule as the Security Rules' `isVerifiedEpfl()`: a signed-in user whose email is verified and
 * ends in @epfl.ch. Returns the user id, or throws `unauthenticated` / `permission-denied`.
 */
export function requireVerifiedEpfl(
  auth: { uid: string; token: Record<string, unknown> } | undefined,
): string {
  if (!auth) {
    throw new RequestError("unauthenticated", "Sign in to negotiate a rental.");
  }
  const email = typeof auth.token.email === "string" ? auth.token.email.toLowerCase() : "";
  if (auth.token.email_verified !== true || !/^[a-z0-9._-]+@epfl\.ch$/.test(email)) {
    throw new RequestError("permission-denied", "Verify your @epfl.ch email to negotiate a rental.");
  }
  return auth.uid;
}

/** The id of the rental's [role] (e.g. "borrower"), or an `internal` error if the rental has none. */
export function requirePerson(value: unknown, role: string): string {
  if (typeof value !== "string" || value === "") {
    throw new RequestError("internal", `The rental has no ${role}.`);
  }
  return value;
}

/** The fields of a callable's input, or none if it is not an object. */
export function asFields(data: unknown): Record<string, unknown> {
  return (typeof data === "object" && data !== null ? data : {}) as Record<string, unknown>;
}
