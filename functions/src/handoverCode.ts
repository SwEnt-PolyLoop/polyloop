// Made with Claude.

import { createHash, randomBytes, timingSafeEqual } from "node:crypto";
import type { DocumentReference, Transaction } from "firebase-admin/firestore";

/** The subcollection of a rental holding its handover codes, one document per phase. */
export const HANDOVER_CODES = "handoverCodes";

/** The two handovers, each with its own code: the pickup and the return. */
export type HandoverPhase = "PICKUP" | "RETURN";

/** The field of the rental holding the fingerprint of each phase's code. */
export const CODE_HASH_FIELDS = { PICKUP: "pickupCodeHash", RETURN: "returnCodeHash" } as const;

/**
 * Makes a new code for [phase] of [rentalRef] in [tx]: the code in `handoverCodes/{phase}`, which
 * only the person who shows it reads, and its fingerprint, returned as `{<field>: fingerprint}` to
 * be written on the rental, so the scanning phone can check a scan. It writes to [tx], and
 * Firestore refuses a read after a write: call it only after every `tx.get` of the transaction,
 * as its own statement, not inside the update it feeds.
 */
export function writeNewHandoverCode(
  tx: Transaction,
  rentalRef: DocumentReference,
  phase: HandoverPhase,
): Record<string, string> {
  const code = newHandoverCode();
  tx.set(rentalRef.collection(HANDOVER_CODES).doc(phase), { code });
  return { [CODE_HASH_FIELDS[phase]]: codeFingerprint(code) };
}

/** How many random bytes a handover code holds: 256 bits, far too many to guess. */
const CODE_BYTES = 32;

/**
 * A new secret handover code: [CODE_BYTES] random bytes as base64url, so 43 characters made only
 * of letters, digits, `-` and `_`, which a QR code holds easily.
 */
export function newHandoverCode(): string {
  return randomBytes(CODE_BYTES).toString("base64url");
}

/**
 * The fingerprint of [code] that the scanning phone checks a scan against: the SHA-256 of its UTF-8
 * bytes, as 64 lowercase hex characters. It must give the same result as the app's
 * `HandoverCode.fingerprint`. The code cannot be worked out from it.
 */
export function codeFingerprint(code: string): string {
  return createHash("sha256").update(code, "utf8").digest("hex");
}

/**
 * True when [scanned] is exactly [expected], a code that is not blank. The comparison takes the
 * same time wherever the two differ, so the time it takes says nothing about the code.
 */
export function isSameCode(scanned: unknown, expected: unknown): boolean {
  if (typeof scanned !== "string" || typeof expected !== "string") return false;
  if (expected.trim() === "") return false;
  const a = Buffer.from(scanned, "utf8");
  const b = Buffer.from(expected, "utf8");
  return a.length === b.length && timingSafeEqual(a, b);
}
