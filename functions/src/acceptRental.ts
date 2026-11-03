// Made with Claude.

import { getFirestore } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { toCallableError } from "./callable";
import { acceptRental as acceptInTransaction } from "./rentalAnswers";
import { parseAcceptInput, requireVerifiedEpfl } from "./rentalRules";

/**
 * Callable `acceptRental` (see docs/ARCHITECTURE.md): the lender accepts a requested rental with
 * the agreed deposit and both addresses. Input: `{rentalId, deposit, pickupAddress, returnAddress}`.
 * Returns `{declinedRentalIds}`, the other requests declined because their dates overlapped.
 */
export const acceptRental = onCall(async (request) => {
  try {
    const uid = requireVerifiedEpfl(request.auth);
    const input = parseAcceptInput(request.data);
    return await acceptInTransaction(getFirestore(), uid, input);
  } catch (error) {
    throw toCallableError(error);
  }
});
