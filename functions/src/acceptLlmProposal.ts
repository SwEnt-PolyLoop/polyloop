// Made with Claude.

import { getFirestore } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { toCallableError } from "./callable";
import { parseLlmAcceptInput } from "./disputeRules";
import { acceptLlmProposal as acceptInTransaction } from "./llmProposalAnswers";
import { requireVerifiedEpfl } from "./rentalRules";

/**
 * Callable `acceptLlmProposal` (see docs/ARCHITECTURE.md): the caller accepts the LLM proposal;
 * once both parties have, the dispute is settled. Input: `{rentalId, attempt}`, the attempt the
 * caller saw.
 */
export const acceptLlmProposal = onCall(async (request) => {
  try {
    const uid = requireVerifiedEpfl(request.auth);
    await acceptInTransaction(getFirestore(), uid, parseLlmAcceptInput(request.data));
    return {};
  } catch (error) {
    throw toCallableError(error);
  }
});
