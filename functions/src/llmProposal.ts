// Made with Claude.

import { DocumentData, FieldValue, Firestore, Timestamp } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { FAILED, LLM_PROPOSAL, PENDING, READY } from "./disputeRules";
import {
  ANSWER_SCHEMA,
  INSTRUCTIONS,
  RentalFacts,
  StoredPhoto,
  checkAnswer,
  choosePhotos,
  evidenceParts,
} from "./llmProposalRules";
import { writeWithSystemMessage } from "./negotiation";
import { listingTitleOf } from "./rentalRules";
import { AdvisorRequest, PermanentAdvisorError, SplitAdvisor } from "./splitAdvisor";

/** Firestore names, as in "Firestore schema" in docs/ARCHITECTURE.md. */
const DISPUTES = "disputes";
const RENTALS = "rentals";
const LISTINGS = "listings";
const PHOTOS = "photos";
const DISPUTED = "DISPUTED";

/** What the chat says when the LLM proposal fails. */
export const FAILED_MESSAGE = "The LLM proposal failed · try again or reject";

/** What the chat says when the LLM proposal is ready, with the lender's share. */
export function readyMessage(lenderPercent: number): string {
  return `LLM proposal ready · ${lenderPercent}% to the lender`;
}

/** What a run of [draftLlmProposal] works with, passed so that tests can replace it. */
export interface DraftOptions {
  /** The model to ask, or null while none is configured: the proposal then fails at once. */
  advisor: SplitAdvisor | null;
  /** True once the event has been retried for too long: the proposal fails without asking. */
  expired: boolean;
}

/** What is about to be asked, kept to store the photos sent with the proposal. */
interface Evidence {
  request: AdvisorRequest;
  photoIds: string[];
}

/**
 * Writes the LLM proposal of the dispute of [rentalId], given the dispute [before] and [after] an
 * update (see "Disputes" in docs/ARCHITECTURE.md). It acts only when the update started a new
 * attempt: the dispute is in `LLM_PROPOSAL` and its `llmProposal` is `PENDING` with an `attempt`
 * that changed. Its own writes keep the attempt, so they never start it again.
 *
 * It asks [options.advisor] for a split from the rental's facts and condition photos, then writes
 * `READY` with the split, its reasoning, the model and the photos sent, or `FAILED`: when no
 * advisor is configured, the event is too old, the advisor fails for good or its answer breaks the
 * rules. Either result adds a SYSTEM message to the chat, from nobody. A failure that may pass
 * (the model is down or slow) rejects instead, writing nothing, so that the event is delivered
 * again. A dispute whose rental is missing or not DISPUTED is left as it is.
 *
 * The result is written only if the dispute still waits for this attempt, so when two runs
 * overlap, the first answer is kept and the second is dropped.
 *
 * @param db the Firestore database.
 * @param rentalId the rental of the dispute, also the dispute's id.
 * @param before the dispute before the update, or undefined if it did not exist.
 * @param after the dispute after the update, or undefined if it was deleted.
 * @param options the advisor, and whether the event was retried for too long.
 * @throws Error when the advisor fails in a way that may pass, or Firestore fails.
 */
export async function draftLlmProposal(
  db: Firestore,
  rentalId: string,
  before: DocumentData | undefined,
  after: DocumentData | undefined,
  options: DraftOptions,
): Promise<void> {
  const attempt = startedAttempt(before, after);
  if (attempt === null) return;
  const { advisor, expired } = options;
  if (expired || advisor === null) {
    logger.error(
      expired
        ? `Gave up asking for the LLM proposal of rental ${rentalId}`
        : `No model is configured for the LLM proposal of rental ${rentalId}`,
    );
    await writeResult(db, rentalId, attempt, failedProposal(attempt), FAILED_MESSAGE);
    return;
  }
  const evidence = await gatherEvidence(db, rentalId);
  if (evidence === null) return;
  let answer: unknown;
  try {
    answer = await advisor.advise(evidence.request);
  } catch (error) {
    if (!(error instanceof PermanentAdvisorError)) {
      logger.error(`Could not get the LLM proposal of rental ${rentalId}, will retry`, error);
      throw error;
    }
    logger.error(`The model refused the LLM proposal of rental ${rentalId}`, error);
    await writeResult(db, rentalId, attempt, failedProposal(attempt), FAILED_MESSAGE);
    return;
  }
  const checked = checkAnswer(answer);
  if (checked === null) {
    // Not the answer itself: it may quote the parties' photos or item.
    logger.error(`The model's answer for the LLM proposal of rental ${rentalId} broke the rules`);
    await writeResult(db, rentalId, attempt, failedProposal(attempt), FAILED_MESSAGE);
    return;
  }
  const ready = {
    status: READY,
    attempt,
    lenderPercent: checked.lenderPercent,
    reasoning: checked.reasoning,
    model: advisor.model,
    photoIds: evidence.photoIds,
    createdAt: FieldValue.serverTimestamp(),
  };
  await writeResult(db, rentalId, attempt, ready, readyMessage(checked.lenderPercent));
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

/**
 * The request for the dispute of [rentalId], from its rental, its listing and its condition
 * photos, or null when the rental is missing or not DISPUTED. A deleted listing leaves the item's
 * description and value unknown; its title is copied on the rental.
 */
async function gatherEvidence(db: Firestore, rentalId: string): Promise<Evidence | null> {
  const rentalRef = db.collection(RENTALS).doc(rentalId);
  const [rentalSnap, photosSnap] = await Promise.all([
    rentalRef.get(),
    rentalRef.collection(PHOTOS).get(),
  ]);
  const rental = rentalSnap.data();
  if (rental?.status !== DISPUTED) {
    logger.warn(`The rental of the dispute ${rentalId} is missing or not DISPUTED`);
    return null;
  }
  const listingId = rental.listingId;
  const listing =
    typeof listingId === "string" && listingId !== ""
      ? (await db.collection(LISTINGS).doc(listingId).get()).data()
      : undefined;
  const facts: RentalFacts = {
    itemTitle: listingTitleOf(rental),
    itemDescription: typeof listing?.description === "string" ? listing.description : "",
    itemValue: typeof listing?.itemValue === "number" ? listing.itemValue : null,
    deposit: typeof rental.deposit === "number" ? rental.deposit : 0,
    startDate: typeof rental.startDate === "string" ? rental.startDate : "",
    endDate: typeof rental.endDate === "string" ? rental.endDate : "",
  };
  const stored: StoredPhoto[] = photosSnap.docs.map((doc) => {
    const takenAt = doc.get("takenAt");
    return {
      id: doc.id,
      phase: doc.get("phase"),
      takenBy: doc.get("takenBy"),
      takenAt: takenAt instanceof Timestamp ? takenAt.toDate() : null,
    };
  });
  const photos = choosePhotos(stored, rental.ownerId, rental.borrowerId);
  return {
    request: {
      instructions: INSTRUCTIONS,
      parts: evidenceParts(rentalId, facts, photos),
      schema: ANSWER_SCHEMA,
    },
    photoIds: photos.map((photo) => photo.id),
  };
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
