// Made with Claude.

import { getFirestore } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { toCallableError } from "./callable";
import { declineRequest as declineInTransaction } from "./negotiation";
import { parseRentalId, requireVerifiedEpfl } from "./rentalRules";

/** Callable `declineRequest`: the lender declines a request with no offer. Input: `{rentalId}`. */
export const declineRequest = onCall(async (request) => {
  try {
    const uid = requireVerifiedEpfl(request.auth);
    await declineInTransaction(getFirestore(), uid, parseRentalId(request.data));
    return {};
  } catch (error) {
    throw toCallableError(error);
  }
});
