// Made with Claude.

import { FieldValue, Firestore } from "firebase-admin/firestore";
import { HANDOVER_CODES, isSameCode, writeNewHandoverCode } from "./handoverCode";
import { writeWithSystemMessage } from "./negotiation";
import {
  PAID_STATUSES,
  isLenderPaid,
  readLenderPayment,
  requireParties,
  writeLenderPayment,
} from "./payLender";
import { RequestError, asFields, parseText } from "./rentalRules";
import { zurichDay } from "./serverToday";

/** The reasons of a refusal the app reads (`details.reason`), see docs/ARCHITECTURE.md. */
export const WRONG_CODE = "WRONG_CODE";
export const RENTAL_CHANGED = "RENTAL_CHANGED";

/** What the borrower's phone sends once it has scanned the lender's QR code. */
export interface PickupScan {
  rentalId: string;
  code: string;
}

/** The longest code accepted: real codes have 43 characters (see handoverCode.ts). */
const MAX_CODE_LENGTH = 200;

/**
 * The input of `confirmHandover`: `{rentalId, phase, code}`. Only the PICKUP phase exists for now
 * (the return scan comes with the return). The code is kept exactly as scanned, not trimmed. Throws
 * an `invalid-argument` error if a field is wrong.
 */
export function parseHandoverScan(data: unknown): PickupScan {
  const fields = asFields(data);
  const rentalId = parseText(fields.rentalId, "rentalId");
  if (fields.phase === "RETURN") {
    throw new RequestError("invalid-argument", "The return scan does not exist yet.");
  }
  if (fields.phase !== "PICKUP") {
    throw new RequestError("invalid-argument", "The phase must be PICKUP or RETURN.");
  }
  const code = fields.code;
  if (typeof code !== "string" || code === "" || code.length > MAX_CODE_LENGTH) {
    throw new RequestError("invalid-argument", "code is required.");
  }
  return { rentalId, code };
}

/**
 * Confirms the pickup of [scan.rentalId] scanned by [callerId], in one transaction: the rental
 * becomes ACTIVE with `pickedUpAt`, the lender is paid the rental price held at request (see
 * payLender.ts; not again if they already were), a SYSTEM message says it started, and the pickup
 * code and its fingerprint are deleted, as they are used up. The checks run in this order: the
 * rental exists, the caller is its borrower (the person receiving the item), then, if the rental
 * was already picked up, the call succeeds and changes nothing (a second scan, or one sent twice);
 * otherwise the rental is ACCEPTED, today in Zurich at [now] is its first day, the code is the one
 * the lender's phone shows, and the price is held. The server trusts the app's order: the photos
 * are not checked. A rental accepted before the return code existed gets it here, in the same
 * transaction, so it can still be given back.
 *
 * @throws RequestError `not-found`; `permission-denied` (not the borrower); `failed-precondition`
 *   with the reason `RENTAL_CHANGED` (no longer ACCEPTED, e.g. cancelled) or `WRONG_CODE`, or with
 *   no reason outside the pickup day or when the rental price is not held; `internal` for a rental
 *   with no valid borrower, lender or price. Nothing is written then.
 */
export async function confirmPickup(
  db: Firestore,
  callerId: string,
  scan: PickupScan,
  now: Date,
): Promise<void> {
  const rentalRef = db.collection("rentals").doc(scan.rentalId);
  const codeRef = rentalRef.collection(HANDOVER_CODES).doc("PICKUP");
  const returnCodeRef = rentalRef.collection(HANDOVER_CODES).doc("RETURN");
  await db.runTransaction(async (tx) => {
    const rental = (await tx.get(rentalRef)).data();
    if (!rental) {
      throw new RequestError("not-found", "This rental does not exist.");
    }
    if (callerId !== rental.borrowerId) {
      throw new RequestError("permission-denied", "Only the borrower confirms the pickup.");
    }
    if (PAID_STATUSES.includes(rental.status)) return;
    if (rental.status !== "ACCEPTED") {
      throw new RequestError("failed-precondition", "This rental can no longer be picked up.", {
        reason: RENTAL_CHANGED,
      });
    }
    if (zurichDay(now) !== rental.startDate) {
      throw new RequestError("failed-precondition", "The pickup is on the rental's first day.");
    }
    const code = (await tx.get(codeRef)).get("code");
    if (!isSameCode(scan.code, code)) {
      throw new RequestError("failed-precondition", "This is not the code of this pickup.", {
        reason: WRONG_CODE,
      });
    }
    // The payment's reads, before any write of the transaction.
    const parties = requireParties(rental);
    const payment = (await isLenderPaid(tx, db, parties, scan.rentalId))
      ? null
      : await readLenderPayment(tx, db, rental, parties);
    const hasReturnCode = (await tx.get(returnCodeRef)).exists;
    tx.delete(codeRef);
    writeWithSystemMessage(
      tx,
      rentalRef,
      "Picked up · rental started",
      {
        status: "ACTIVE",
        pickedUpAt: FieldValue.serverTimestamp(),
        pickupCodeHash: FieldValue.delete(),
        ...(hasReturnCode ? {} : writeNewHandoverCode(tx, rentalRef, "RETURN")),
      },
      callerId,
    );
    if (payment) writeLenderPayment(tx, db, scan.rentalId, payment);
  });
}
