// Made with Claude.

import { getFirestore } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { toCallableError } from "./callable";
import { escalateDispute as escalateInTransaction } from "./dispute";
import { parseRentalId, requireVerifiedEpfl } from "./rentalRules";

/**
 * Callable `escalateDispute` (see docs/ARCHITECTURE.md): the caller moves a dispute from direct
 * settlement to the LLM proposal right away. Input: `{rentalId}`.
 */
export const escalateDispute = onCall(async (request) => {
  try {
    const uid = requireVerifiedEpfl(request.auth);
    await escalateInTransaction(getFirestore(), uid, parseRentalId(request.data));
    return {};
  } catch (error) {
    throw toCallableError(error);
  }
});
