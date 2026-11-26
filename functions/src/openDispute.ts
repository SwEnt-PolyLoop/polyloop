// Made with Claude.

import { getFirestore } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { toCallableError } from "./callable";
import { openDispute as openInTransaction } from "./dispute";
import { parseRentalId, requireVerifiedEpfl } from "./rentalRules";

/**
 * Callable `openDispute` (see docs/ARCHITECTURE.md): the lender flags damage on a returned item,
 * which opens its dispute in direct settlement. Input: `{rentalId}`. Returns `{}`, also when the
 * dispute was already open.
 */
export const openDispute = onCall(async (request) => {
  try {
    const uid = requireVerifiedEpfl(request.auth);
    await openInTransaction(getFirestore(), uid, parseRentalId(request.data));
    return {};
  } catch (error) {
    throw toCallableError(error);
  }
});
