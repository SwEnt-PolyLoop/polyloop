// Made with Claude.

import { FieldValue, Firestore } from "firebase-admin/firestore";
import { HANDOVER_CODES, isSameCode } from "./handoverCode";
import { writeWithSystemMessage } from "./negotiation";
import { PAID_STATUSES } from "./payLender";
import { RequestError } from "./rentalRules";
import { zurichDay } from "./serverToday";

/** The reasons of a refusal the app reads (`details.reason`), see docs/ARCHITECTURE.md. */
export const WRONG_CODE = "WRONG_CODE";
export const RENTAL_CHANGED = "RENTAL_CHANGED";

/** What the borrower's phone sends once it has scanned the lender's QR code. */
export interface PickupScan {
  rentalId: string;
  code: string;
}

/**
 * Confirms the pickup of [scan.rentalId] scanned by [callerId], in one transaction: the rental
 * becomes ACTIVE with `pickedUpAt`, a SYSTEM message says it started, and the pickup code and its
 * fingerprint are deleted, as they are used up. The checks run in this order: the rental exists,
 * the caller is its borrower (the person receiving the item), then, if the rental was already
 * picked up, the call succeeds and changes nothing (a second scan, or one sent twice); otherwise
 * the rental is ACCEPTED, today in Zurich at [now] is its first day, and the code is the one the
 * lender's phone shows. The server trusts the app's order: the photos are not checked.
 *
 * @throws RequestError `not-found`; `permission-denied` (not the borrower); `failed-precondition`
 *   with the reason `RENTAL_CHANGED` (no longer ACCEPTED, e.g. cancelled) or `WRONG_CODE`, or with
 *   no reason outside the pickup day. Nothing is written then.
 */
export async function confirmPickup(
  db: Firestore,
  callerId: string,
  scan: PickupScan,
  now: Date,
): Promise<void> {
  const rentalRef = db.collection("rentals").doc(scan.rentalId);
  const codeRef = rentalRef.collection(HANDOVER_CODES).doc("PICKUP");
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
    tx.delete(codeRef);
    writeWithSystemMessage(
      tx,
      rentalRef,
      "Picked up · rental started",
      {
        status: "ACTIVE",
        pickedUpAt: FieldValue.serverTimestamp(),
        pickupCodeHash: FieldValue.delete(),
      },
      callerId,
    );
  });
}
