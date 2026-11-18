// Made with Claude.

import { DocumentData, FieldValue, Firestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { FAILED, LLM_PROPOSAL, PENDING } from "./disputeRules";
import { writeWithSystemMessage } from "./negotiation";

/** Firestore names, as in "Firestore schema" in docs/ARCHITECTURE.md. */
const DISPUTES = "disputes";
const RENTALS = "rentals";
const DISPUTED = "DISPUTED";

/** What the chat says when the LLM proposal fails. */
export const FAILED_MESSAGE = "The LLM proposal failed · try again or reject";

/**
 * Writes the LLM proposal of the dispute of [rentalId], given the dispute [before] and [after] an
 * update (see "Disputes" in docs/ARCHITECTURE.md). It acts only when the update started a new
 * attempt: the dispute is in `LLM_PROPOSAL` and its `llmProposal` is `PENDING` with an `attempt`
 * that changed. Its own writes keep the attempt, so they never start it again.
 *
 * No model is asked yet (#455): every new attempt is written `FAILED` at once, with a SYSTEM message
 * in the chat from nobody, so the parties can already try again or reject. A dispute whose rental
 * is missing or not DISPUTED is left as it is.
 *
 * The result is written only if the dispute still waits for this attempt, so when two runs
 * overlap, the first one is kept and the second is dropped.
 *
 * @param db the Firestore database.
 * @param rentalId the rental of the dispute, also the dispute's id.
 * @param before the dispute before the update, or undefined if it did not exist.
 * @param after the dispute after the update, or undefined if it was deleted.
 * @throws Error when Firestore fails, so that the event is delivered again.
 */
export async function draftLlmProposal(
  db: Firestore,
  rentalId: string,
  before: DocumentData | undefined,
  after: DocumentData | undefined,
): Promise<void> {
  const attempt = startedAttempt(before, after);
  if (attempt === null) return;
  logger.error(`No model is configured for the LLM proposal of rental ${rentalId}`);
  await writeResult(db, rentalId, attempt, failedProposal(attempt), FAILED_MESSAGE);
}

/**
 * The attempt the update from [before] to [after] started, or null when it started none: the
 * dispute must be in `LLM_PROPOSAL`, its proposal `PENDING`, and its `attempt` a number that
 * changed.
 */
function startedAttempt(
  before: DocumentData | undefined,
  after: DocumentData | undefined,
): number | null {
  const proposal = after?.llmProposal;
  if (after?.stage !== LLM_PROPOSAL || proposal?.status !== PENDING) return null;
  const attempt = proposal.attempt;
  if (typeof attempt !== "number" || attempt === before?.llmProposal?.attempt) return null;
  return attempt;
}

/** A failed proposal for [attempt], which "Try again" can follow. */
function failedProposal(attempt: number): DocumentData {
  return { status: FAILED, attempt, createdAt: FieldValue.serverTimestamp() };
}

/**
 * Stores [proposal] as the dispute's LLM proposal, with [message] in the chat from nobody, in one
 * transaction, if the dispute still waits for [attempt]: in `LLM_PROPOSAL`, not settled, its
 * proposal `PENDING` on that attempt, and its rental DISPUTED. Otherwise nothing is written: the
 * dispute moved on, or another run already answered.
 */
async function writeResult(
  db: Firestore,
  rentalId: string,
  attempt: number,
  proposal: DocumentData,
  message: string,
): Promise<void> {
  const disputeRef = db.collection(DISPUTES).doc(rentalId);
  const rentalRef = db.collection(RENTALS).doc(rentalId);
  await db.runTransaction(async (tx) => {
    const [disputeSnap, rentalSnap] = await Promise.all([tx.get(disputeRef), tx.get(rentalRef)]);
    const dispute = disputeSnap.data();
    const waiting =
      dispute?.stage === LLM_PROPOSAL &&
      !dispute.agreedAt &&
      dispute.llmProposal?.status === PENDING &&
      dispute.llmProposal?.attempt === attempt &&
      rentalSnap.get("status") === DISPUTED;
    if (!waiting) return;
    tx.update(disputeRef, { llmProposal: proposal });
    writeWithSystemMessage(tx, rentalRef, message, {}, null);
  });
}
