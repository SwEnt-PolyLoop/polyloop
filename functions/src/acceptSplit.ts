// Made with Claude.

import { getFirestore } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { toCallableError } from "./callable";
import { acceptSplit as acceptSplitInTransaction } from "./dispute";
import { parseSplitInput } from "./disputeRules";
import { requireVerifiedEpfl } from "./rentalRules";

/**
 * Callable `acceptSplit` (see docs/ARCHITECTURE.md): the caller accepts the other party's latest
 * split, which settles the dispute. Input: `{rentalId, lenderPercent}`, the split the caller saw.
 */
export const acceptSplit = onCall(async (request) => {
  try {
    const uid = requireVerifiedEpfl(request.auth);
    await acceptSplitInTransaction(getFirestore(), uid, parseSplitInput(request.data));
    return {};
  } catch (error) {
    throw toCallableError(error);
  }
});
