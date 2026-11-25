// Made with Claude.

import { getFirestore } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { toCallableError } from "./callable";
import { confirmReturnOk as closeInTransaction } from "./handover";
import { parseRentalId, requireVerifiedEpfl } from "./rentalRules";

/**
 * Callable `confirmReturnOk` (see docs/ARCHITECTURE.md): the lender answers "Item is fine" on a
 * returned item, which gives the deposit back to the borrower and closes the rental. Input:
 * `{rentalId}`. Returns `{}`, also when the rental was already closed by this answer.
 */
export const confirmReturnOk = onCall(async (request) => {
  try {
    const uid = requireVerifiedEpfl(request.auth);
    await closeInTransaction(getFirestore(), uid, parseRentalId(request.data));
    return {};
  } catch (error) {
    throw toCallableError(error);
  }
});
