// Made with Claude.

import {
  DocumentData,
  DocumentReference,
  Firestore,
  Timestamp,
  Transaction,
} from "firebase-admin/firestore";
import {
  LLM_PROPOSAL,
  PROPOSAL_CHANGED,
  SETTLE,
  STAGE_CHANGED,
  SplitInput,
  splitDeposit,
} from "./disputeRules";
import { writeWithSystemMessage } from "./negotiation";
import { RequestError } from "./rentalRules";

/** Firestore names, as in "Firestore schema" in docs/ARCHITECTURE.md. */
const DISPUTES = "disputes";
const RENTALS = "rentals";
const CLOSED = "CLOSED";

/**
 * The most proposals a dispute holds. A real negotiation stays far below it; it only stops a
 * script or an app bug from growing the document towards Firestore's 1 MiB limit, which would
 * block every write to the dispute, and from making each change heavy for both phones listening.
 */
export const MAX_PROPOSALS = 500;

/** A split proposed by one party, as stored in `disputes/{rentalId}.proposals`. */
interface StoredProposal {
  authorId: string;
  lenderPercent: number;
  createdAt: Timestamp;
}

/** A dispute and its rental, read in a transaction. */
interface OpenDispute {
  disputeRef: DocumentReference;
  rentalRef: DocumentReference;
  rental: DocumentData;
  proposals: StoredProposal[];
}

/**
 * Adds the caller's split to the dispute of [input.rentalId], in one transaction. It becomes the
 * latest proposal, the one the other party can accept. Nothing is added to the chat.
 *
 * @param db the Firestore database.
 * @param callerId the participant proposing.
 * @param input the rental and the lender's share, already checked by `parseSplitInput`.
 * @param now the current time, stored as the proposal's `createdAt`; passed so tests can set it.
 * @throws RequestError `not-found`, `permission-denied` (not a participant) or
 *   `failed-precondition` (settled or no longer in direct settlement: `STAGE_CHANGED`; the same
 *   split as the latest proposal, or [MAX_PROPOSALS] reached). Nothing is written then.
 */
export async function proposeSplit(
  db: Firestore,
  callerId: string,
  input: SplitInput,
  now: Date = new Date(),
): Promise<void> {
  await db.runTransaction(async (tx) => {
    const open = await requireSettlingDispute(tx, db, input.rentalId, callerId);
    if (latestOf(open.proposals)?.lenderPercent === input.lenderPercent) {
      throw new RequestError("failed-precondition", "This split is already on the table.");
    }
    if (open.proposals.length >= MAX_PROPOSALS) {
      throw new RequestError(
        "failed-precondition",
        "Too many proposals: accept the latest one or escalate.",
      );
    }
    const proposal: StoredProposal = {
      authorId: callerId,
      lenderPercent: input.lenderPercent,
      // A server timestamp cannot go inside an array.
      createdAt: Timestamp.fromDate(now),
    };
    tx.update(open.disputeRef, { proposals: [...open.proposals, proposal] });
  });
}

/**
 * Accepts the latest proposal of the dispute of [input.rentalId], in one transaction: the dispute
 * is settled and the rental CLOSED, with the split in the chat. [input.lenderPercent] is the split
 * the caller saw, so a proposal changed meanwhile is never accepted. No money moves yet: the
 * deposit is not reserved at acceptance until the wallet has a Firestore implementation.
 *
 * @param db the Firestore database.
 * @param callerId the participant accepting, who must not be the latest proposal's author.
 * @param input the rental and the split the caller saw, already checked by `parseSplitInput`.
 * @param now the current time, stored as `agreedAt`; passed so that tests can set it.
 * @throws RequestError `not-found`, `permission-denied` (not a participant, or the caller's own
 *   proposal) or `failed-precondition` (settled or no longer in direct settlement:
 *   `STAGE_CHANGED`; no proposal; the latest proposal is not the split the caller saw:
 *   `PROPOSAL_CHANGED`, checked before the author so that a percentage changed meanwhile, even
 *   by the caller's own new proposal, reads as changed). Nothing is written then.
 */
export async function acceptSplit(
  db: Firestore,
  callerId: string,
  input: SplitInput,
  now: Date = new Date(),
): Promise<void> {
  await db.runTransaction(async (tx) => {
    const open = await requireSettlingDispute(tx, db, input.rentalId, callerId);
    const latest = latestOf(open.proposals);
    if (!latest) {
      throw new RequestError("failed-precondition", "No proposal is waiting for an answer.");
    }
    if (latest.lenderPercent !== input.lenderPercent) {
      throw new RequestError("failed-precondition", "The proposal changed.", {
        reason: PROPOSAL_CHANGED,
      });
    }
    if (latest.authorId === callerId) {
      throw new RequestError("permission-denied", "You cannot accept your own proposal.");
    }
    settle(tx, open, latest.lenderPercent, now);
  });
}

/**
 * Moves the dispute of [rentalId] from direct settlement to the LLM proposal at the caller's
 * request, in one transaction. `escalatesAt` is left as it was: it only applies to direct
 * settlement.
 *
 * @param db the Firestore database.
 * @param callerId the participant escalating, stored as `escalatedBy`.
 * @param rentalId the rental of the dispute.
 * @throws RequestError `not-found`, `permission-denied` (not a participant) or
 *   `failed-precondition` (settled or no longer in direct settlement: `STAGE_CHANGED`, so two
 *   escalations sent at once never skip a stage). Nothing is written then.
 */
export async function escalateDispute(
  db: Firestore,
  callerId: string,
  rentalId: string,
): Promise<void> {
  await db.runTransaction(async (tx) => {
    const open = await requireSettlingDispute(tx, db, rentalId, callerId);
    moveToLlmProposal(tx, open.disputeRef, open.rentalRef, callerId);
  });
}

/**
 * Moves a dispute on to the LLM proposal, with [escalatedBy] and a SYSTEM message in the rental's
 * chat. Runs inside the caller's transaction, after its reads.
 */
function moveToLlmProposal(
  tx: Transaction,
  disputeRef: DocumentReference,
  rentalRef: DocumentReference,
  escalatedBy: string,
): void {
  tx.update(disputeRef, { stage: LLM_PROPOSAL, escalatedBy });
  writeWithSystemMessage(tx, rentalRef, "Dispute escalated · waiting for the LLM proposal", {});
}

/**
 * Ends a dispute on the split [lenderPercent]: `agreedAt` on the dispute, the rental CLOSED and the
 * shares in the chat. Moving the deposit will be added here once it is really reserved.
 */
function settle(tx: Transaction, open: OpenDispute, lenderPercent: number, now: Date): void {
  const { lender, borrower } = splitDeposit(depositOf(open.rental), lenderPercent);
  tx.update(open.disputeRef, { agreedAt: Timestamp.fromDate(now) });
  writeWithSystemMessage(
    tx,
    open.rentalRef,
    `Dispute settled · ${lender} PP to the lender, ${borrower} PP back to the borrower`,
    { status: CLOSED },
  );
}

/**
 * The dispute of [rentalId] and its rental, or a refusal: the dispute or the rental is missing, the
 * caller takes no part in the rental, the dispute is settled, or it left direct settlement. The
 * last two carry `STAGE_CHANGED`: a request that lost the race to an agreement or an escalation
 * tells the app the dispute moved on, so it follows the dispute instead of showing an error.
 */
async function requireSettlingDispute(
  tx: Transaction,
  db: Firestore,
  rentalId: string,
  callerId: string,
): Promise<OpenDispute> {
  const disputeRef = db.collection(DISPUTES).doc(rentalId);
  const rentalRef = db.collection(RENTALS).doc(rentalId);
  const [disputeSnap, rentalSnap] = await Promise.all([tx.get(disputeRef), tx.get(rentalRef)]);
  const dispute = disputeSnap.data();
  const rental = rentalSnap.data();
  if (!dispute || !rental) {
    throw new RequestError("not-found", "This dispute does not exist.");
  }
  if (callerId !== rental.ownerId && callerId !== rental.borrowerId) {
    throw new RequestError("permission-denied", "Only the two participants can take part.");
  }
  if (dispute.agreedAt) {
    throw new RequestError("failed-precondition", "This dispute is already settled.", {
      reason: STAGE_CHANGED,
    });
  }
  if (dispute.stage !== SETTLE) {
    throw new RequestError(
      "failed-precondition",
      "This dispute is no longer in direct settlement.",
      { reason: STAGE_CHANGED },
    );
  }
  const proposals = Array.isArray(dispute.proposals) ? (dispute.proposals as StoredProposal[]) : [];
  return { disputeRef, rentalRef, rental, proposals };
}

/** The proposal on the table: the last one sent, or undefined when nobody has proposed yet. */
function latestOf(proposals: StoredProposal[]): StoredProposal | undefined {
  return proposals[proposals.length - 1];
}

/** The rental's accepted deposit, in PolyPoints; a missing one reads as 0, as in the app. */
function depositOf(rental: DocumentData): number {
  return typeof rental.deposit === "number" ? rental.deposit : 0;
}
