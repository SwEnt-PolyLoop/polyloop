// Made with Claude.

import { getFirestore } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { toCallableError } from "./callable";
import { respondToOffer as respondInTransaction } from "./negotiation";
import { parseOfferAnswer, requireVerifiedEpfl } from "./rentalRules";

/**
 * Callable `respondToOffer` (see docs/ARCHITECTURE.md): a participant answers the other's offer.
 * Input: `{rentalId, action, deposit}`, where `action` is `ACCEPT`, `DECLINE` or `COUNTER` and
 * `deposit` is needed for `COUNTER` only. `ACCEPT` holds the deposit in the borrower's wallet and
 * fails with `failed-precondition` if their available balance is too low. Returns nothing: the requests an acceptance declines
 * belong to other people, whom the caller cannot read.
 */
export const respondToOffer = onCall(async (request) => {
  try {
    const uid = requireVerifiedEpfl(request.auth);
    await respondInTransaction(getFirestore(), uid, parseOfferAnswer(request.data));
    return {};
  } catch (error) {
    throw toCallableError(error);
  }
});
