// Made with Claude.

import { getFirestore } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { toCallableError } from "./callable";
import { retryLlmProposal as retryInTransaction } from "./llmProposalAnswers";
import { parseRentalId, requireVerifiedEpfl } from "./rentalRules";

/**
 * Callable `retryLlmProposal` (see docs/ARCHITECTURE.md): the caller asks the LLM again after a
 * failed proposal, up to 3 attempts in all. Input: `{rentalId}`.
 */
export const retryLlmProposal = onCall(async (request) => {
  try {
    const uid = requireVerifiedEpfl(request.auth);
    await retryInTransaction(getFirestore(), uid, parseRentalId(request.data));
    return {};
  } catch (error) {
    throw toCallableError(error);
  }
});
