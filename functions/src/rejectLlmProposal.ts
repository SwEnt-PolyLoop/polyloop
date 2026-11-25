// Made with Claude.

import { getFirestore } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { toCallableError } from "./callable";
import { rejectLlmProposal as rejectInTransaction } from "./llmProposalAnswers";
import { parseRentalId, requireVerifiedEpfl } from "./rentalRules";

/**
 * Callable `rejectLlmProposal` (see docs/ARCHITECTURE.md): the caller rejects the LLM proposal,
 * ready or failed, which sends the dispute to court. Input: `{rentalId}`.
 */
export const rejectLlmProposal = onCall(async (request) => {
  try {
    const uid = requireVerifiedEpfl(request.auth);
    await rejectInTransaction(getFirestore(), uid, parseRentalId(request.data));
    return {};
  } catch (error) {
    throw toCallableError(error);
  }
});
