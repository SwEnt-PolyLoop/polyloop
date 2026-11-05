// Made with Claude.

import { getFirestore } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { toCallableError } from "./callable";
import { makeOffer as makeOfferInTransaction } from "./negotiation";
import { parseOfferInput, requireVerifiedEpfl } from "./rentalRules";

/**
 * Callable `makeOffer` (see docs/ARCHITECTURE.md): the lender answers a request with a deposit and
 * both addresses. Input: `{rentalId, deposit, pickupAddress, returnAddress}`.
 */
export const makeOffer = onCall(async (request) => {
  try {
    const uid = requireVerifiedEpfl(request.auth);
    await makeOfferInTransaction(getFirestore(), uid, parseOfferInput(request.data));
    return {};
  } catch (error) {
    throw toCallableError(error);
  }
});
