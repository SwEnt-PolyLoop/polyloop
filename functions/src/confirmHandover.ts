// Made with Claude.

import { getFirestore } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { toCallableError } from "./callable";
import { confirmPickup, parseHandoverScan } from "./handover";
import { requireVerifiedEpfl } from "./rentalRules";

/**
 * Callable `confirmHandover` (see docs/ARCHITECTURE.md): the borrower's phone sends the code it
 * scanned on the lender's phone. Input: `{rentalId, phase, code}`, with `phase` `PICKUP` for now.
 * A confirmed pickup makes the rental ACTIVE and pays the lender. A refusal the app tells apart
 * carries `details.reason`: `WRONG_CODE` or `RENTAL_CHANGED`. Returns nothing.
 */
export const confirmHandover = onCall(async (request) => {
  try {
    const uid = requireVerifiedEpfl(request.auth);
    await confirmPickup(getFirestore(), uid, parseHandoverScan(request.data), new Date());
    return {};
  } catch (error) {
    throw toCallableError(error);
  }
});
