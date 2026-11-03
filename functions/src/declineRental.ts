// Made with Claude.

import { getFirestore } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { toCallableError } from "./callable";
import { declineRental as declineInTransaction } from "./rentalAnswers";
import { parseRentalId, requireVerifiedEpfl } from "./rentalRules";

/**
 * Callable `declineRental`: the lender declines a requested rental. Input: `{rentalId}`.
 */
export const declineRental = onCall(async (request) => {
  try {
    const uid = requireVerifiedEpfl(request.auth);
    const rentalId = parseRentalId(request.data);
    await declineInTransaction(getFirestore(), uid, rentalId);
    return {};
  } catch (error) {
    throw toCallableError(error);
  }
});
