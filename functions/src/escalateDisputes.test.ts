// Made with Claude.

import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { DocumentReference, Firestore, Timestamp, getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { acceptSplit, escalateIfOverdue, escalateOverdueDisputes } from "./dispute";
import { escalateDisputes } from "./index";

jest.mock("firebase-functions/logger");
// Firebase loads this ES module to verify tokens, which Jest cannot load; index.ts needs it.
jest.mock("jose", () => ({}));

let db: Firestore;

beforeAll(() => {
  // Without the emulator, the Admin SDK would write to the real project.
  if (!process.env.FIRESTORE_EMULATOR_HOST) {
    throw new Error(
      "These tests need the Firestore emulator. In functions/, run: " +
        'firebase emulators:exec --only firestore "npm test"',
    );
  }
  if (getApps().length === 0) initializeApp({ projectId: process.env.GCLOUD_PROJECT });
  db = getFirestore();
});

// Long before every date of the other test files, which share the emulator and leave their disputes
// there: a sweep at NOW never reaches theirs. Disputes of this file are deleted after each test, so
// that a sweep finds only those of its own test and its counts are exact.
const OVERDUE = new Date("2001-06-15T09:00:00Z");
const NOW = new Date("2001-06-15T10:00:00Z");
const LATER = new Date("2001-06-15T11:00:00Z");
const LENDER = "sweep-lender";
const BORROWER = "sweep-borrower";

const created: DocumentReference[] = [];

afterEach(async () => {
  await Promise.all(created.splice(0).map((ref) => ref.delete()));
  jest.clearAllMocks();
});

/** A disputed rental and its dispute escalating at [escalatesAt]; [fields] override the dispute. */
async function dispute(escalatesAt: Date, fields: object = {}): Promise<DocumentReference> {
  const id = randomUUID();
  const rentalRef = db.collection("rentals").doc(id);
  await rentalRef.set({ ownerId: LENDER, borrowerId: BORROWER, status: "DISPUTED", deposit: 40 });
  const ref = db.collection("disputes").doc(id);
  created.push(ref);
  await ref.set({
    stage: "SETTLE",
    proposals: [],
    escalatesAt: Timestamp.fromDate(escalatesAt),
    escalatedBy: null,
    agreedAt: null,
    ...fields,
  });
  return ref;
}

async function messages(ref: DocumentReference): Promise<string[]> {
  const snap = await db.collection(`rentals/${ref.id}/messages`).get();
  return snap.docs.map((doc) => doc.get("text"));
}

describe("deployment", () => {
  it("runs escalateDisputes on a schedule, in the app's region", () => {
    expect(escalateDisputes.__endpoint.region).toEqual(["europe-west1"]);
    expect(escalateDisputes.__endpoint.scheduleTrigger).toBeDefined();
  });
});

describe("escalateOverdueDisputes", () => {
  it("moves an overdue dispute on by itself, and tells the chat", async () => {
    const ref = await dispute(OVERDUE);

    expect(await escalateOverdueDisputes(db, NOW)).toEqual({ done: 1, failed: 0 });

    expect((await ref.get()).data()).toMatchObject({ stage: "LLM_PROPOSAL", escalatedBy: null });
    expect(await messages(ref)).toEqual([
      "No agreement after 30 days · waiting for the LLM proposal",
    ]);
  });

  it("escalates exactly at escalatesAt, and leaves a dispute not yet due alone", async () => {
    const due = await dispute(NOW);
    const notYet = await dispute(LATER);

    expect(await escalateOverdueDisputes(db, NOW)).toEqual({ done: 1, failed: 0 });

    expect((await due.get()).get("stage")).toBe("LLM_PROPOSAL");
    expect((await notYet.get()).get("stage")).toBe("SETTLE");
  });

  it("logs a failure and goes on, counting only the disputes really escalated", async () => {
    const failing = await dispute(OVERDUE);
    const escalating = await dispute(OVERDUE);
    const error = new Error("Firestore is unavailable");

    const result = await escalateOverdueDisputes(db, NOW, async (ref, now) => {
      if (ref.id === failing.id) throw error;
      return escalateIfOverdue(ref, now);
    });

    expect(result).toEqual({ done: 1, failed: 1 });
    expect((await escalating.get()).get("stage")).toBe("LLM_PROPOSAL");
    expect((await failing.get()).get("stage")).toBe("SETTLE");
    expect(logger.error).toHaveBeenCalledWith(
      `Could not escalate the dispute of rental ${failing.id}`,
      error,
    );
  });

  it("opens no transaction for a dispute already settled or escalated", async () => {
    await dispute(OVERDUE, { agreedAt: Timestamp.fromDate(OVERDUE) });
    await dispute(OVERDUE, { stage: "LLM_PROPOSAL", escalatedBy: LENDER });
    const open = await dispute(OVERDUE);
    const handled: string[] = [];

    const result = await escalateOverdueDisputes(db, NOW, async (ref, now) => {
      handled.push(ref.id);
      return escalateIfOverdue(ref, now);
    });

    expect(handled).toEqual([open.id]);
    expect(result).toEqual({ done: 1, failed: 0 });
  });
});

describe("escalateIfOverdue", () => {
  it("does nothing to a settled dispute or one already escalated, without a message", async () => {
    const settled = await dispute(OVERDUE, { agreedAt: Timestamp.fromDate(OVERDUE) });
    const escalated = await dispute(OVERDUE, { stage: "LLM_PROPOSAL", escalatedBy: LENDER });

    expect(await escalateIfOverdue(settled, NOW)).toBe(false);
    expect(await escalateIfOverdue(escalated, NOW)).toBe(false);
    expect((await settled.get()).get("stage")).toBe("SETTLE");
    expect((await escalated.get()).get("escalatedBy")).toBe(LENDER);
    expect(await messages(settled)).toEqual([]);
    expect(await messages(escalated)).toEqual([]);
  });

  it("does nothing to a missing dispute", async () => {
    expect(await escalateIfOverdue(db.collection("disputes").doc(randomUUID()), NOW)).toBe(false);
  });

  it("does nothing to a dispute not yet due", async () => {
    const notYet = await dispute(LATER);

    expect(await escalateIfOverdue(notYet, NOW)).toBe(false);
    expect((await notYet.get()).get("stage")).toBe("SETTLE");
  });
});

describe("an agreement racing the sweep", () => {
  it("escalates the dispute once, whoever comes first, and never settles it", async () => {
    const ref = await dispute(OVERDUE, {
      proposals: [{ authorId: LENDER, lenderPercent: 50, createdAt: Timestamp.fromDate(OVERDUE) }],
    });

    const [accept, sweep] = await Promise.allSettled([
      acceptSplit(db, BORROWER, { rentalId: ref.id, lenderPercent: 50 }, NOW),
      escalateIfOverdue(ref, NOW),
    ]);

    expect(accept).toMatchObject({
      status: "rejected",
      reason: { details: { reason: "STAGE_CHANGED" } },
    });
    expect(sweep.status).toBe("fulfilled");
    expect((await ref.get()).data()).toMatchObject({
      stage: "LLM_PROPOSAL",
      escalatedBy: null,
      agreedAt: null,
    });
    expect(await messages(ref)).toEqual([
      "No agreement after 30 days · waiting for the LLM proposal",
    ]);
  });

  it("leaves a dispute settled between the query and its transaction", async () => {
    const ref = await dispute(OVERDUE, {
      proposals: [{ authorId: LENDER, lenderPercent: 50, createdAt: Timestamp.fromDate(OVERDUE) }],
    });
    const justInTime = new Date(OVERDUE.getTime() - 1);

    // The query found the dispute still open; the borrower agrees before its transaction runs.
    const result = await escalateOverdueDisputes(db, NOW, async (found, now) => {
      await acceptSplit(db, BORROWER, { rentalId: found.id, lenderPercent: 50 }, justInTime);
      return escalateIfOverdue(found, now);
    });

    expect(result).toEqual({ done: 0, failed: 0 });
    expect((await ref.get()).get("stage")).toBe("SETTLE");
    expect((await db.doc(`rentals/${ref.id}`).get()).get("status")).toBe("CLOSED");
    expect(await messages(ref)).toEqual([
      "Dispute settled · 20 PP to the lender, 20 PP back to the borrower",
    ]);
  });
});
