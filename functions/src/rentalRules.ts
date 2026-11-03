// Made with Claude.

/**
 * Pure rules for answering a rental request: the buffer-day overlap, the input checks and the
 * caller checks. No Firestore and no Firebase SDK here, so they are tested without the emulator.
 * A refusal is a RequestError; the callable wrappers turn it into the HttpsError the client gets
 * (see callable.ts).
 */

/** The error codes a refusal can carry; the same names as Firebase's HttpsError codes. */
export type RequestErrorCode =
  | "invalid-argument"
  | "unauthenticated"
  | "permission-denied"
  | "not-found"
  | "failed-precondition"
  | "internal";

/** A refused request: a code and a message for the user. */
export class RequestError extends Error {
  constructor(
    readonly code: RequestErrorCode,
    message: string,
  ) {
    super(message);
    this.name = "RequestError";
  }
}

/** The 1-day buffer before and after every booking (ARCHITECTURE.md, "Core rules"). */
export const BUFFER_DAYS = 1;

const MILLISECONDS_PER_DAY = 86_400_000;

/** A stretch of days, both included, as YYYY-MM-DD strings (the format of the rental documents). */
export interface DateRange {
  start: string;
  end: string;
}

/** The fields the lender sends with an acceptance. */
export interface AcceptInput {
  rentalId: string;
  deposit: number;
  pickupAddress: string;
  returnAddress: string;
}

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
 * The input of an acceptance: a rental id, a whole deposit above 0, and two non-blank addresses
 * (trimmed). Throws an `invalid-argument` error naming the first field that is wrong.
 */
export function parseAcceptInput(data: unknown): AcceptInput {
  const fields = (typeof data === "object" && data !== null ? data : {}) as Record<string, unknown>;
  const rentalId = parseText(fields.rentalId, "rentalId");
  const deposit = fields.deposit;
  if (typeof deposit !== "number" || !Number.isInteger(deposit) || deposit <= 0) {
    throw new RequestError("invalid-argument", "The deposit must be a whole number above 0.");
  }
  return {
    rentalId,
    deposit,
    pickupAddress: parseText(fields.pickupAddress, "pickupAddress"),
    returnAddress: parseText(fields.returnAddress, "returnAddress"),
  };
}

/** The input of a decline: only the rental id. */
export function parseRentalId(data: unknown): string {
  const fields = (typeof data === "object" && data !== null ? data : {}) as Record<string, unknown>;
  return parseText(fields.rentalId, "rentalId");
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
export function requireVerifiedEpfl(auth: { uid: string; token: Record<string, unknown> } | undefined): string {
  if (!auth) {
    throw new RequestError("unauthenticated", "Sign in to answer a rental request.");
  }
  const email = typeof auth.token.email === "string" ? auth.token.email.toLowerCase() : "";
  if (auth.token.email_verified !== true || !/^[a-z0-9._-]+@epfl\.ch$/.test(email)) {
    throw new RequestError(
      "permission-denied",
      "Verify your @epfl.ch email to answer a rental request.",
    );
  }
  return auth.uid;
}
