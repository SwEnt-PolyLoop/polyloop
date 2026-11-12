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
import { SweepCounts, runSweep } from "./sweep";

/** Firestore names, as in "Firestore schema" in docs/ARCHITECTURE.md. */
const DISPUTES = "disputes";
const RENTALS = "rentals";
const CLOSED = "CLOSED";
const DISPUTED = "DISPUTED";

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
  /** As read: a Timestamp, or missing or null on a dispute that never escalates by itself. */
  escalatesAt: unknown;
}

/**
 * Adds the caller's split to the dispute of [input.rentalId], in one transaction. It becomes the
 * latest proposal, the one the other party can accept. Nothing is added to the chat. Once
 * `escalatesAt` has passed, the dispute escalates instead (see [inDirectSettlement]).
 *
 * @param db the Firestore database.
 * @param callerId the participant proposing.
 * @param input the rental and the lender's share, already checked by `parseSplitInput`.
 * @param now the current time, stored as the proposal's `createdAt` and compared with
 *   `escalatesAt`; passed so tests can set it.
 * @throws RequestError `not-found`, `permission-denied` (not a participant) or
 *   `failed-precondition` (settled, no longer in direct settlement or past `escalatesAt`:
 *   `STAGE_CHANGED`; the rental not DISPUTED; the same split as the latest proposal, or
 *   [MAX_PROPOSALS] reached). Nothing is written then, except the escalation once past
 *   `escalatesAt`.
 */
export async function proposeSplit(
  db: Firestore,
  callerId: string,
  input: SplitInput,
  now: Date = new Date(),
): Promise<void> {
  await inDirectSettlement(db, callerId, input.rentalId, now, (tx, open) => {
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
 * the caller saw, so a proposal changed meanwhile is never accepted. No money moves yet: nothing
 * calls `reserveDeposit` when an offer is accepted, so no deposit is held to release or split.
 * Once `escalatesAt` has passed, the dispute escalates instead (see [inDirectSettlement]).
 *
 * @param db the Firestore database.
 * @param callerId the participant accepting, who must not be the latest proposal's author.
 * @param input the rental and the split the caller saw, already checked by `parseSplitInput`.
 * @param now the current time, stored as `agreedAt` and compared with `escalatesAt`; passed so
 *   that tests can set it.
 * @throws RequestError `not-found`, `permission-denied` (not a participant, or the caller's own
 *   proposal) or `failed-precondition` (settled, no longer in direct settlement or past
 *   `escalatesAt`: `STAGE_CHANGED`; the rental not DISPUTED; no proposal; the latest proposal is
 *   not the split the caller saw: `PROPOSAL_CHANGED`, checked before the author so that a
 *   percentage changed meanwhile, even by the caller's own new proposal, reads as changed).
 *   Nothing is written then, except the escalation once past `escalatesAt`.
 */
export async function acceptSplit(
  db: Firestore,
  callerId: string,
  input: SplitInput,
  now: Date = new Date(),
): Promise<void> {
  await inDirectSettlement(db, callerId, input.rentalId, now, (tx, open) => {
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
    settle(tx, open, latest.lenderPercent, now, callerId);
  });
}

/**
 * Moves the dispute of [rentalId] from direct settlement to the LLM proposal at the caller's
 * request, in one transaction. `escalatesAt` is left as it was: it only applies to direct
 * settlement. Once `escalatesAt` has passed, the dispute escalates by itself instead, with no
 * `escalatedBy` (see [inDirectSettlement]).
 *
 * @param db the Firestore database.
 * @param callerId the participant escalating, stored as `escalatedBy`.
 * @param rentalId the rental of the dispute.
 * @param now the current time, compared with `escalatesAt`; passed so that tests can set it.
 * @throws RequestError `not-found`, `permission-denied` (not a participant) or
 *   `failed-precondition` (settled, no longer in direct settlement or past `escalatesAt`:
 *   `STAGE_CHANGED`, so two escalations sent at once never skip a stage; the rental not DISPUTED).
 *   Nothing is written then, except the escalation once past `escalatesAt`.
 */
export async function escalateDispute(
  db: Firestore,
  callerId: string,
  rentalId: string,
  now: Date = new Date(),
): Promise<void> {
  await inDirectSettlement(db, callerId, rentalId, now, (tx, open) => {
    moveToLlmProposal(tx, open.disputeRef, open.rentalRef, callerId);
  });
}

/**
 * Runs [act] on the dispute of [rentalId] in one transaction, if it is still in direct
 * settlement. If its `escalatesAt` has passed at [now] but the hourly sweep has not run yet, the
 * dispute escalates as the sweep would, [act] is not run, and the caller gets `STAGE_CHANGED` once
 * the escalation is written: throwing inside the transaction would undo it.
 *
 * @param db the Firestore database.
 * @param callerId the participant calling, who must be the rental's owner or borrower.
 * @param rentalId the rental of the dispute.
 * @param now the current time, compared with `escalatesAt`.
 * @param act the change to make while the dispute is in direct settlement; it may throw a refusal
 *   before writing anything.
 * @throws RequestError the refusals of [requireSettlingDispute] and of [act], or `STAGE_CHANGED`
 *   after the escalation.
 */
async function inDirectSettlement(
  db: Firestore,
  callerId: string,
  rentalId: string,
  now: Date,
  act: (tx: Transaction, open: OpenDispute) => void,
): Promise<void> {
  const escalated = await db.runTransaction(async (tx) => {
    const open = await requireSettlingDispute(tx, db, rentalId, callerId);
    if (isDue(open.escalatesAt, now)) {
      moveToLlmProposal(tx, open.disputeRef, open.rentalRef, null);
      return true;
    }
    act(tx, open);
    return false;
  });
  if (escalated) throw stageChanged();
}

/**
 * Moves on to the LLM proposal every dispute still in direct settlement whose `escalatesAt` has
 * passed at [now]. One failure is logged and does not stop the others. The query is on one field,
 * so it needs no composite index. `escalatesAt` is never cleared, by decision: disputes already
 * escalated or settled are left out before any transaction, but each run still reads every
 * dispute ever past its date (only `stage` and `agreedAt`), a cost that grows with the number of
 * disputes. Each transaction then checks the stage and the agreement again.
 *
 * @param db the Firestore database.
 * @param now the current time, passed so that tests can set it.
 * @param escalate handles one dispute; tests pass one that fails.
 * @returns how many disputes were escalated (`done`) and how many could not be (`failed`), retried
 *   by the next run.
 */
export async function escalateOverdueDisputes(
  db: Firestore,
  now: Date,
  escalate: typeof escalateIfOverdue = escalateIfOverdue,
): Promise<SweepCounts> {
  const overdue = await db
    .collection(DISPUTES)
    .where("escalatesAt", "<=", Timestamp.fromDate(now))
    .select("stage", "agreedAt")
    .get();
  const open = overdue.docs.filter((doc) => doc.get("stage") === SETTLE && !doc.get("agreedAt"));
  return runSweep(
    open.map((doc) => doc.ref),
    (ref) => escalate(ref, now),
    (id) => `Could not escalate the dispute of rental ${id}`,
  );
}

/**
 * Moves one dispute on to the LLM proposal if, at [now], it is still in direct settlement, not
 * settled and past its `escalatesAt`, in one transaction. The check is made again because a party
 * may have agreed or escalated since the query. As in the callables, its rental must be DISPUTED,
 * checked after the dispute so that one settled meanwhile (its rental CLOSED) is nothing to do.
 *
 * @param disputeRef the dispute to check.
 * @param now the current time.
 * @returns true if the dispute was escalated, false if there was nothing to do.
 * @throws Error if the dispute is due but its rental is missing or not DISPUTED: corrupt data,
 *   left as it is and logged by the sweep at each run.
 */
export async function escalateIfOverdue(
  disputeRef: DocumentReference,
  now: Date,
): Promise<boolean> {
  const rentalRef = disputeRef.firestore.collection(RENTALS).doc(disputeRef.id);
  return disputeRef.firestore.runTransaction(async (tx) => {
    const [disputeSnap, rentalSnap] = await Promise.all([tx.get(disputeRef), tx.get(rentalRef)]);
    const dispute = disputeSnap.data();
    if (
      dispute?.stage !== SETTLE ||
      dispute.agreedAt ||
      !isDue(dispute.escalatesAt, now)
    ) {
      return false;
    }
    if (rentalSnap.get("status") !== DISPUTED) {
      throw new Error(`The rental of the dispute ${disputeRef.id} is missing or not DISPUTED.`);
    }
    moveToLlmProposal(tx, disputeRef, rentalRef, null);
    return true;
  });
}

/**
 * Moves a dispute on to the LLM proposal, with [escalatedBy] (null when it moved on by itself) and
 * a SYSTEM message in the rental's chat. Runs inside the caller's transaction, after its reads.
 */
function moveToLlmProposal(
  tx: Transaction,
  disputeRef: DocumentReference,
  rentalRef: DocumentReference,
  escalatedBy: string | null,
): void {
  tx.update(disputeRef, { stage: LLM_PROPOSAL, escalatedBy });
  const text =
    escalatedBy === null
      ? "No agreement after 30 days · waiting for the LLM proposal"
      : "Dispute escalated · waiting for the LLM proposal";
  writeWithSystemMessage(tx, rentalRef, text, {}, escalatedBy);
}

/**
 * Ends a dispute on the split [lenderPercent], accepted by [acceptedBy]: `agreedAt` on the dispute,
 * the rental CLOSED and the shares in the chat. Moving the deposit will be added here once it is
 * really reserved.
 */
function settle(
  tx: Transaction,
  open: OpenDispute,
  lenderPercent: number,
  now: Date,
  acceptedBy: string,
): void {
  const { lender, borrower } = splitDeposit(depositOf(open.rental), lenderPercent);
  tx.update(open.disputeRef, { agreedAt: Timestamp.fromDate(now) });
  writeWithSystemMessage(
    tx,
    open.rentalRef,
    `Dispute settled · ${lender} PP to the lender, ${borrower} PP back to the borrower`,
    { status: CLOSED },
    acceptedBy,
  );
}

/**
 * The dispute of [rentalId] and its rental, or a refusal: the dispute or the rental is missing, the
 * caller takes no part in the rental, the dispute is settled, it left direct settlement, or the
 * rental is not DISPUTED. The settled and left cases carry `STAGE_CHANGED`: a request that lost
 * the race to an agreement or an escalation tells the app the dispute moved on, so it follows the
 * dispute instead of showing an error. They are checked before the status, because an agreement
 * also closes the rental. Only a Function opens a dispute, setting its rental to DISPUTED, so any
 * other status means corrupt data: it gets a plain refusal, and an agreement never overwrites it.
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
    throw stageChanged("This dispute is already settled.");
  }
  if (dispute.stage !== SETTLE) {
    throw stageChanged();
  }
  if (rental.status !== DISPUTED) {
    throw new RequestError("failed-precondition", "This rental is not in dispute.");
  }
  const proposals = Array.isArray(dispute.proposals) ? (dispute.proposals as StoredProposal[]) : [];
  return { disputeRef, rentalRef, rental, proposals, escalatesAt: dispute.escalatesAt };
}

/** The refusal sent when the dispute was settled or left direct settlement, with [message]. */
function stageChanged(
  message = "This dispute is no longer in direct settlement.",
): RequestError {
  return new RequestError("failed-precondition", message, { reason: STAGE_CHANGED });
}

/** Whether direct settlement should have escalated by [now]; a dispute without a date never does. */
function isDue(escalatesAt: unknown, now: Date): boolean {
  return escalatesAt instanceof Timestamp && escalatesAt.toMillis() <= now.getTime();
}

/** The proposal on the table: the last one sent, or undefined when nobody has proposed yet. */
function latestOf(proposals: StoredProposal[]): StoredProposal | undefined {
  return proposals[proposals.length - 1];
}

/** The rental's accepted deposit, in PolyPoints; a missing one reads as 0, as in the app. */
function depositOf(rental: DocumentData): number {
  return typeof rental.deposit === "number" ? rental.deposit : 0;
}
