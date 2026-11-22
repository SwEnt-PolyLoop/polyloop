// Made with Claude.

import { getFirestore } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { toCallableError } from "./callable";
import { confirmPickup, confirmReturn, parseHandoverScan } from "./handover";
import { requireVerifiedEpfl } from "./rentalRules";

/**
 * Callable `confirmHandover` (see docs/ARCHITECTURE.md): the phone of the person receiving the item
 * sends the code it scanned on the other person's phone. Input: `{rentalId, phase, code}`, with
 * `phase` `PICKUP` (the borrower scans) or `RETURN` (the lender scans). A confirmed pickup makes
 * the rental ACTIVE and pays the lender; a confirmed return makes it RETURNED. A refusal the app
 * tells apart carries `details.reason`: `WRONG_CODE` or `RENTAL_CHANGED`. Returns nothing.
 */
export const confirmHandover = onCall(async (request) => {
  try {
    const uid = requireVerifiedEpfl(request.auth);
    const scan = parseHandoverScan(request.data);
    const confirm = scan.phase === "RETURN" ? confirmReturn : confirmPickup;
    await confirm(getFirestore(), uid, scan, new Date());
    return {};
  } catch (error) {
    throw toCallableError(error);
  }
});
