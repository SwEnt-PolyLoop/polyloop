// Made with Claude.

import { getFirestore } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { toCallableError } from "./callable";
import { proposeSplit as proposeSplitInTransaction } from "./dispute";
import { parseSplitInput } from "./disputeRules";
import { requireVerifiedEpfl } from "./rentalRules";

/**
 * Callable `proposeSplit` (see docs/ARCHITECTURE.md): the caller adds a split to a dispute in
 * direct settlement. Input: `{rentalId, lenderPercent}`, a whole percentage from 0 to 100.
 */
export const proposeSplit = onCall(async (request) => {
  try {
    const uid = requireVerifiedEpfl(request.auth);
    await proposeSplitInTransaction(getFirestore(), uid, parseSplitInput(request.data));
    return {};
  } catch (error) {
    throw toCallableError(error);
  }
});
