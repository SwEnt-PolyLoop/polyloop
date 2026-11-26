// Made with Claude.

import { Firestore } from "firebase-admin/firestore";
import { requireOpenDispute, settle } from "./dispute";
import {
  COURT,
  FAILED,
  LLM_PROPOSAL,
  LLM_PROPOSAL_CHANGED,
  LlmAcceptInput,
  MAX_LLM_ATTEMPTS,
  PENDING,
  READY,
  isWholePercent,
} from "./disputeRules";
import { writeWithSystemMessage } from "./negotiation";
import { RequestError } from "./rentalRules";

/** What the chat says when a party rejects the LLM proposal. */
export const REJECTED_MESSAGE = "LLM proposal rejected · the dispute goes to court";

/** The LLM proposal's fields the answers read, as stored in `disputes/{rentalId}.llmProposal`. */
interface StoredLlmProposal {
  status?: unknown;
  attempt?: unknown;
  lenderPercent?: unknown;
  acceptedBy?: unknown;
}

/**
 * Accepts the LLM proposal of the dispute of [input.rentalId] for the caller, in one transaction.
 * The caller is added to `llmProposal.acceptedBy`, with no chat message. Once both participants
 * are in it, the dispute is settled on the proposal's split as `acceptSplit` settles it:
 * `agreedAt`, the rental CLOSED, the shares in the chat and the held deposit split between the two
 * wallets (see `settle` in dispute.ts). Accepting again before the dispute is settled changes
 * nothing and succeeds; once it is settled, it is refused with `STAGE_CHANGED`, as `acceptSplit`
 * is.
 *
 * @param db the Firestore database.
 * @param callerId the participant accepting.
 * @param input the rental and the attempt the caller saw, already checked by
 *   `parseLlmAcceptInput`.
 * @param now the current time, stored as `agreedAt`; passed so that tests can set it.
 * @throws RequestError `not-found`, `permission-denied` (not a participant) or
 *   `failed-precondition` (settled or no longer in `LLM_PROPOSAL`: `STAGE_CHANGED`; another
 *   attempt than [input.attempt]: `LLM_PROPOSAL_CHANGED`, so nobody accepts a proposal they never
 *   saw; no proposal, or one not `READY`; the rental not DISPUTED), or the refusals of `settle`.
 *   Nothing is written then.
 */
export async function acceptLlmProposal(
  db: Firestore,
  callerId: string,
  input: LlmAcceptInput,
  now: Date = new Date(),
): Promise<void> {
  await db.runTransaction(async (tx) => {
    const open = await requireOpenDispute(tx, db, input.rentalId, callerId, LLM_PROPOSAL);
    if (open.llmProposal == null) {
      throw new RequestError("failed-precondition", "This dispute has no LLM proposal.");
    }
    const proposal = open.llmProposal as StoredLlmProposal;
    if (proposal.attempt !== input.attempt) throw llmProposalChanged();
    const lenderPercent = proposal.lenderPercent;
    if (proposal.status !== READY || !isWholePercent(lenderPercent)) {
      throw new RequestError("failed-precondition", "The LLM proposal is not ready.");
    }
    const acceptedBy = acceptedByOf(proposal);
    if (acceptedBy.includes(callerId)) return;
    const nowAccepted = [...acceptedBy, callerId];
    const { ownerId, borrowerId } = open.rental;
    if (nowAccepted.includes(ownerId) && nowAccepted.includes(borrowerId)) {
      await settle(tx, db, open, lenderPercent, now, callerId, {
        "llmProposal.acceptedBy": nowAccepted,
      });
    } else {
      tx.update(open.disputeRef, { "llmProposal.acceptedBy": nowAccepted });
    }
  });
}

/**
 * Rejects the LLM proposal of the dispute of [rentalId], in one transaction: the dispute moves to
 * `COURT` at once, with the caller as `escalatedBy`, and a SYSTEM message in the chat. A `READY`
 * proposal can be rejected even after the other party, or the caller, accepted it; a `FAILED` one
 * too, which is the way out once no attempt is left. The deposit stays held.
 *
 * @param db the Firestore database.
 * @param callerId the participant rejecting, stored as `escalatedBy`.
 * @param rentalId the rental of the dispute.
 * @throws RequestError `not-found`, `permission-denied` (not a participant) or
 *   `failed-precondition` (settled or no longer in `LLM_PROPOSAL`: `STAGE_CHANGED`, so two
 *   rejections sent at once move the dispute once; the proposal `PENDING`, a new attempt asked
 *   meanwhile: `LLM_PROPOSAL_CHANGED`; no proposal; the rental not DISPUTED). Nothing is written
 *   then.
 */
export async function rejectLlmProposal(
  db: Firestore,
  callerId: string,
  rentalId: string,
): Promise<void> {
  await db.runTransaction(async (tx) => {
    const open = await requireOpenDispute(tx, db, rentalId, callerId, LLM_PROPOSAL);
    const status = ((open.llmProposal ?? {}) as StoredLlmProposal).status;
    if (status === PENDING) {
      // The other party asked for a new attempt meanwhile: the app follows the dispute.
      throw llmProposalChanged();
    }
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
 *   `PENDING` or `READY`, an attempt already started, so two retries sent at once, or a retry sent
 *   again after its answer was lost, start one attempt: `LLM_PROPOSAL_CHANGED`; no proposal, or
 *   no attempt left; the rental not DISPUTED). Nothing is written then.
 */
export async function retryLlmProposal(
  db: Firestore,
  callerId: string,
  rentalId: string,
): Promise<void> {
  await db.runTransaction(async (tx) => {
    const open = await requireOpenDispute(tx, db, rentalId, callerId, LLM_PROPOSAL);
    const { status, attempt } = (open.llmProposal ?? {}) as StoredLlmProposal;
    if (status === PENDING || status === READY) {
      // Another retry, maybe this caller's own whose answer was lost, already started an attempt.
      throw llmProposalChanged();
    }
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

/** The participants who accepted [proposal]; missing or invalid reads as nobody. */
function acceptedByOf(proposal: StoredLlmProposal): string[] {
  const acceptedBy = proposal.acceptedBy;
  return Array.isArray(acceptedBy)
    ? acceptedBy.filter((uid): uid is string => typeof uid === "string")
    : [];
}

/** The refusal sent when the LLM proposal changed since the caller saw it. */
function llmProposalChanged(): RequestError {
  return new RequestError("failed-precondition", "The LLM proposal changed.", {
    reason: LLM_PROPOSAL_CHANGED,
  });
}
