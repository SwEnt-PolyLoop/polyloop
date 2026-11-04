// Made with Claude.

import { getFirestore } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { toCallableError } from "./callable";
import { respondToOffer as respondInTransaction } from "./negotiation";
import { parseOfferAnswer, requireVerifiedEpfl } from "./rentalRules";

/**
 * Callable `respondToOffer` (see docs/ARCHITECTURE.md): a participant answers the other's offer.
 * Input: `{rentalId, action, deposit}`, where `action` is `ACCEPT`, `DECLINE` or `COUNTER` and
 * `deposit` is needed for `COUNTER` only. Returns `{declinedRentalIds}`, the other requests declined
 * because their dates overlapped.
 */
export const respondToOffer = onCall(async (request) => {
  try {
    const uid = requireVerifiedEpfl(request.auth);
    return await respondInTransaction(getFirestore(), uid, parseOfferAnswer(request.data));
  } catch (error) {
    throw toCallableError(error);
  }
});
