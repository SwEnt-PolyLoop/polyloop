// Made with Claude.

import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { DocumentData, Firestore, Timestamp, getFirestore } from "firebase-admin/firestore";
import { FAILED_MESSAGE, draftLlmProposal } from "./llmProposal";

jest.mock("firebase-functions/logger");

// The transactions are tested on the emulator.
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

const LENDER = "llm-lender";
const BORROWER = "llm-borrower";

/** The dispute as it was in direct settlement, before it escalated. */
const SETTLING = { stage: "SETTLE", proposals: [], escalatedBy: null, agreedAt: null };

/**
 * A disputed rental and a dispute just escalated to the LLM proposal, as the Cloud Functions write
 * them. [dispute] overrides the dispute's fields. Returns the rental id and the dispute as stored.
 */
async function escalated(dispute: object = {}): Promise<{ id: string; after: DocumentData }> {
  const id = randomUUID();
  await db
    .collection("rentals")
    .doc(id)
    .set({
      listingTitle: "Road bike",
      ownerId: LENDER,
      borrowerId: BORROWER,
      participants: [LENDER, BORROWER],
      status: "DISPUTED",
      deposit: 200,
    });
  const after = {
    ...SETTLING,
    stage: "LLM_PROPOSAL",
    escalatedBy: LENDER,
    llmProposal: { status: "PENDING", attempt: 1 },
    ...dispute,
  };
  await db.collection("disputes").doc(id).set(after);
  return { id, after };
}

/** Runs a draft for the update of the dispute [id] from [before] to [after]. */
function draft(id: string, after: DocumentData, before: DocumentData = SETTLING): Promise<void> {
  return draftLlmProposal(db, id, before, after);
}

async function proposal(id: string) {
  return (await db.collection("disputes").doc(id).get()).get("llmProposal");
}

async function messages(id: string): Promise<string[]> {
  const snap = await db.collection(`rentals/${id}/messages`).orderBy("sentAt").get();
  expect(snap.docs.every((doc) => doc.get("type") === "SYSTEM")).toBe(true);
  return snap.docs.map((doc) => doc.get("text"));
}

describe("a new attempt", () => {
  it("fails at once, with a message in the chat from nobody, while no model is asked", async () => {
    const { id, after } = await escalated();

    await draft(id, after);

    const stored = await proposal(id);
    expect(stored).toMatchObject({ status: "FAILED", attempt: 1 });
    expect(stored.createdAt).toBeInstanceOf(Timestamp);
    expect(stored.lenderPercent).toBeUndefined();
    expect(await messages(id)).toEqual([FAILED_MESSAGE]);
    expect(FAILED_MESSAGE).toBe("The LLM proposal failed · try again or reject");
    // From nobody: neither participant's chat is marked as read.
    expect((await db.collection("rentals").doc(id).get()).get("lastReadAt")).toBeUndefined();
  });

  it("answers a new attempt after a failure", async () => {
    const { id, after } = await escalated({ llmProposal: { status: "PENDING", attempt: 2 } });
    const before = { ...after, llmProposal: { status: "FAILED", attempt: 1 } };

    await draft(id, after, before);

    expect(await proposal(id)).toMatchObject({ status: "FAILED", attempt: 2 });
  });
});

describe("when nothing is written", () => {
  it("ignores an update that started no new attempt", async () => {
    const { id, after } = await escalated();
    const pending = after.llmProposal;

    const updates: [DocumentData, DocumentData][] = [
      // Another field changed while the proposal waits: the attempt is the same.
      [{ ...after, escalatedBy: null }, after],
      // Its own result.
      [after, { ...after, llmProposal: { ...pending, status: "FAILED" } }],
      // Not in the LLM proposal stage, or no proposal at all.
      [SETTLING, { ...after, stage: "COURT" }],
      [SETTLING, { ...after, llmProposal: undefined }],
      [SETTLING, { ...after, llmProposal: { status: "PENDING", attempt: "1" } }],
    ];
    for (const [before, changed] of updates) {
      await draftLlmProposal(db, id, before, changed);
    }
    await draftLlmProposal(db, id, after, undefined);

    expect(await proposal(id)).toEqual(pending);
    expect(await messages(id)).toEqual([]);
  });

  it("leaves alone a dispute whose rental is no longer DISPUTED", async () => {
    const { id, after } = await escalated();
    await db.collection("rentals").doc(id).update({ status: "CLOSED" });

    await draft(id, after);

    expect(await proposal(id)).toEqual({ status: "PENDING", attempt: 1 });
    expect(await messages(id)).toEqual([]);
  });

  it("writes nothing when the dispute no longer waits for this attempt", async () => {
    const movedOn = [
      { stage: "COURT" },
      { agreedAt: Timestamp.now() },
      { llmProposal: { status: "PENDING", attempt: 2 } },
    ];
    for (const change of movedOn) {
      // The event still shows attempt 1, but the dispute changed before the result was written.
      const { id, after } = await escalated();
      await db.collection("disputes").doc(id).update(change);

      await draft(id, after);

      expect(await messages(id)).toEqual([]);
    }
  });

  it("keeps the first result when two runs of the same attempt overlap", async () => {
    const { id, after } = await escalated();

    await Promise.all([draft(id, after), draft(id, after)]);

    expect((await proposal(id)).status).toBe("FAILED");
    expect(await messages(id)).toEqual([FAILED_MESSAGE]);
  });
});
