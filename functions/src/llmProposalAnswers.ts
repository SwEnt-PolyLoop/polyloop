// Made with Claude.

import { Firestore } from "firebase-admin/firestore";
import { requireOpenDispute } from "./dispute";
import { COURT, FAILED, LLM_PROPOSAL, MAX_LLM_ATTEMPTS, PENDING, READY } from "./disputeRules";
import { writeWithSystemMessage } from "./negotiation";
import { RequestError } from "./rentalRules";

/** What the chat says when a party rejects the LLM proposal. */
export const REJECTED_MESSAGE = "LLM proposal rejected · the dispute goes to court";

/** The LLM proposal's fields the answers read, as stored in `disputes/{rentalId}.llmProposal`. */
interface StoredLlmProposal {
  status?: unknown;
  attempt?: unknown;
}

/**
 * Rejects the LLM proposal of the dispute of [rentalId], in one transaction: the dispute moves to
 * `COURT` at once, with the caller as `escalatedBy`, and a SYSTEM message in the chat. A `READY`
 * proposal can be rejected, and so can a `FAILED` one, which is the way out once no attempt is
 * left. The deposit stays held.
 *
 * @param db the Firestore database.
 * @param callerId the participant rejecting, stored as `escalatedBy`.
 * @param rentalId the rental of the dispute.
 * @throws RequestError `not-found`, `permission-denied` (not a participant) or
 *   `failed-precondition` (settled or no longer in `LLM_PROPOSAL`: `STAGE_CHANGED`, so two
 *   rejections sent at once move the dispute once; the proposal still `PENDING`; the rental not
 *   DISPUTED). Nothing is written then.
 */
export async function rejectLlmProposal(
  db: Firestore,
  callerId: string,
  rentalId: string,
): Promise<void> {
  await db.runTransaction(async (tx) => {
    const open = await requireOpenDispute(tx, db, rentalId, callerId, LLM_PROPOSAL);
    const status = ((open.llmProposal ?? {}) as StoredLlmProposal).status;
    if (status !== READY && status !== FAILED) {
      throw new RequestError("failed-precondition", "The LLM proposal is not ready yet.");
    }
    tx.update(open.disputeRef, { stage: COURT, escalatedBy: callerId });
    writeWithSystemMessage(tx, open.rentalRef, REJECTED_MESSAGE, {}, callerId);
  });
}

/**
 * Asks the LLM for a new proposal on the dispute of [rentalId], in one transaction, when the last
 * one failed and fewer than [MAX_LLM_ATTEMPTS] were made: the proposal becomes `PENDING` on the
 * next attempt, which starts `draftLlmProposal` again. Nothing is added to the chat; the result
 * is.
 *
 * @param db the Firestore database.
 * @param callerId the participant asking.
 * @param rentalId the rental of the dispute.
 * @throws RequestError `not-found`, `permission-denied` (not a participant) or
 *   `failed-precondition` (settled or no longer in `LLM_PROPOSAL`: `STAGE_CHANGED`; the proposal
 *   not `FAILED`, so two retries sent at once start one attempt; no attempt left; the rental not
 *   DISPUTED). Nothing is written then.
 */
export async function retryLlmProposal(
  db: Firestore,
  callerId: string,
  rentalId: string,
): Promise<void> {
  await db.runTransaction(async (tx) => {
    const open = await requireOpenDispute(tx, db, rentalId, callerId, LLM_PROPOSAL);
    const { status, attempt } = (open.llmProposal ?? {}) as StoredLlmProposal;
    if (status !== FAILED || typeof attempt !== "number" || !Number.isInteger(attempt)) {
      throw new RequestError("failed-precondition", "Only a failed LLM proposal can be retried.");
    }
    if (attempt >= MAX_LLM_ATTEMPTS) {
      throw new RequestError(
        "failed-precondition",
        "No attempt left: reject the LLM proposal to go to court.",
      );
    }
    tx.update(open.disputeRef, { llmProposal: { status: PENDING, attempt: attempt + 1 } });
  });
}

