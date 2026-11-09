// Made with Claude.

import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { Firestore, Timestamp, getFirestore } from "firebase-admin/firestore";
import { MAX_PROPOSALS, acceptSplit, escalateDispute, proposeSplit } from "./dispute";

// The transactions are tested on the emulator; the pure rules are in disputeRules.test.ts.
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

const LENDER = "dispute-lender";
const BORROWER = "dispute-borrower";
const STRANGER = "dispute-stranger";
const NOW = new Date("2026-11-04T10:00:00Z");
const ESCALATES_AT = new Date("2026-12-01T10:00:00Z");

/** A proposal as stored, sent one day before NOW. */
function proposal(authorId: string, lenderPercent: number) {
  return { authorId, lenderPercent, createdAt: Timestamp.fromDate(new Date("2026-11-03T10:00Z")) };
}

/**
 * A disputed rental with a deposit of [deposit] PP and its dispute, as the Cloud Functions write
 * them. [dispute] overrides the dispute's fields. Returns the rental id.
 */
async function disputed(dispute: object = {}, deposit = 33): Promise<string> {
  const id = randomUUID();
  await db.collection("rentals").doc(id).set({
    ownerId: LENDER,
    borrowerId: BORROWER,
    participants: [LENDER, BORROWER],
    status: "DISPUTED",
    deposit,
  });
  await db
    .collection("disputes")
    .doc(id)
    .set({
      stage: "SETTLE",
      proposals: [],
      escalatesAt: Timestamp.fromDate(ESCALATES_AT),
      escalatedBy: null,
      agreedAt: null,
      ...dispute,
    });
  return id;
}

async function rental(id: string) {
  return (await db.collection("rentals").doc(id).get()).data();
}

async function dispute(id: string) {
  return (await db.collection("disputes").doc(id).get()).data();
}

/** The texts of the rental's chat, oldest first, all of which must be SYSTEM messages. */
async function systemMessages(id: string): Promise<string[]> {
  const snap = await db.collection(`rentals/${id}/messages`).orderBy("sentAt").get();
  expect(snap.docs.every((doc) => doc.get("type") === "SYSTEM")).toBe(true);
  return snap.docs.map((doc) => doc.get("text"));
}

describe("proposeSplit", () => {
  it("adds the caller's split as the latest proposal, without a chat message", async () => {
    const id = await disputed({ proposals: [proposal(LENDER, 80)] });

    await proposeSplit(db, BORROWER, { rentalId: id, lenderPercent: 40 }, NOW);

    const proposals = (await dispute(id))?.proposals;
    expect(proposals).toHaveLength(2);
    expect(proposals[1]).toMatchObject({ authorId: BORROWER, lenderPercent: 40 });
    expect(proposals[1].createdAt.toDate()).toEqual(NOW);
    expect(await systemMessages(id)).toEqual([]);
  });

  it("refuses the split already on the table, whoever sent it", async () => {
    const id = await disputed({ proposals: [proposal(LENDER, 80)] });

    for (const caller of [LENDER, BORROWER]) {
      await expect(
        proposeSplit(db, caller, { rentalId: id, lenderPercent: 80 }, NOW),
      ).rejects.toMatchObject({ code: "failed-precondition" });
    }
    expect((await dispute(id))?.proposals).toHaveLength(1);
  });

  it("refuses a proposal once the dispute holds the most it can", async () => {
    const full = Array.from({ length: MAX_PROPOSALS }, (_, i) =>
      proposal(i % 2 === 0 ? LENDER : BORROWER, i % 2 === 0 ? 50 : 51),
    );
    const id = await disputed({ proposals: full });

    await expect(
      proposeSplit(db, LENDER, { rentalId: id, lenderPercent: 60 }, NOW),
    ).rejects.toMatchObject({ code: "failed-precondition" });
    expect((await dispute(id))?.proposals).toHaveLength(MAX_PROPOSALS);
  });

  it("refuses someone who takes no part in the rental, and a missing dispute", async () => {
    const id = await disputed();

    await expect(
      proposeSplit(db, STRANGER, { rentalId: id, lenderPercent: 50 }, NOW),
    ).rejects.toMatchObject({ code: "permission-denied" });
    await expect(
      proposeSplit(db, LENDER, { rentalId: randomUUID(), lenderPercent: 50 }, NOW),
    ).rejects.toMatchObject({ code: "not-found" });
    expect((await dispute(id))?.proposals).toEqual([]);
  });

  it("refuses a settled dispute and one that left direct settlement", async () => {
    const settled = await disputed({ agreedAt: Timestamp.fromDate(NOW) });
    const escalated = await disputed({ stage: "LLM_PROPOSAL", escalatedBy: LENDER });

    await expect(
      proposeSplit(db, LENDER, { rentalId: settled, lenderPercent: 50 }, NOW),
    ).rejects.toMatchObject({ code: "failed-precondition", details: undefined });
    await expect(
      proposeSplit(db, LENDER, { rentalId: escalated, lenderPercent: 50 }, NOW),
    ).rejects.toMatchObject({
      code: "failed-precondition",
      details: { reason: "STAGE_CHANGED" },
    });
  });
});

describe("acceptSplit", () => {
  it("settles the dispute and closes the rental, with the shares in the chat", async () => {
    const id = await disputed({ proposals: [proposal(LENDER, 50)] });

    await acceptSplit(db, BORROWER, { rentalId: id, lenderPercent: 50 }, NOW);

    expect((await dispute(id))?.agreedAt.toDate()).toEqual(NOW);
    expect(await rental(id)).toMatchObject({
      status: "CLOSED",
      // No money moves yet: the deposit stays as it was.
      deposit: 33,
      lastMessage: "Dispute settled · 17 PP to the lender, 16 PP back to the borrower",
    });
    expect(await systemMessages(id)).toEqual([
      "Dispute settled · 17 PP to the lender, 16 PP back to the borrower",
    ]);
  });

  it("refuses a split that is no longer the latest proposal, and writes nothing", async () => {
    const id = await disputed({ proposals: [proposal(BORROWER, 30), proposal(LENDER, 70)] });

    const error = await acceptSplit(db, BORROWER, { rentalId: id, lenderPercent: 30 }, NOW).catch(
      (e) => e,
    );

    expect(error).toMatchObject({
      code: "failed-precondition",
      details: { reason: "PROPOSAL_CHANGED" },
    });
    expect((await dispute(id))?.agreedAt).toBeNull();
    expect((await rental(id))?.status).toBe("DISPUTED");
  });

  it("refuses the caller's own proposal and a dispute without one", async () => {
    const own = await disputed({ proposals: [proposal(LENDER, 70)] });
    const empty = await disputed();

    await expect(
      acceptSplit(db, LENDER, { rentalId: own, lenderPercent: 70 }, NOW),
    ).rejects.toMatchObject({ code: "permission-denied" });
    await expect(
      acceptSplit(db, LENDER, { rentalId: empty, lenderPercent: 70 }, NOW),
    ).rejects.toMatchObject({ code: "failed-precondition" });
  });

  it("refuses a dispute already settled, so it is never settled twice", async () => {
    const id = await disputed({ proposals: [proposal(LENDER, 50)] });
    await acceptSplit(db, BORROWER, { rentalId: id, lenderPercent: 50 }, NOW);

    await expect(
      acceptSplit(db, BORROWER, { rentalId: id, lenderPercent: 50 }, NOW),
    ).rejects.toMatchObject({ code: "failed-precondition" });
    expect(await systemMessages(id)).toHaveLength(1);
  });

  it("reads a missing deposit as 0 PP", async () => {
    const id = await disputed({ proposals: [proposal(LENDER, 50)] });
    await db.collection("rentals").doc(id).update({ deposit: null });

    await acceptSplit(db, BORROWER, { rentalId: id, lenderPercent: 50 }, NOW);

    expect((await rental(id))?.lastMessage).toBe(
      "Dispute settled · 0 PP to the lender, 0 PP back to the borrower",
    );
  });
});

describe("escalateDispute", () => {
  it("moves the dispute on to the LLM proposal, keeping escalatesAt", async () => {
    const id = await disputed();

    await escalateDispute(db, BORROWER, id);

    const data = await dispute(id);
    expect(data).toMatchObject({ stage: "LLM_PROPOSAL", escalatedBy: BORROWER });
    expect(data?.escalatesAt.toDate()).toEqual(ESCALATES_AT);
    expect((await rental(id))?.status).toBe("DISPUTED");
    expect(await systemMessages(id)).toEqual([
      "Dispute escalated · waiting for the LLM proposal",
    ]);
  });

  it("refuses a dispute that already moved on, without a second message", async () => {
    const id = await disputed();
    await escalateDispute(db, LENDER, id);

    await expect(escalateDispute(db, BORROWER, id)).rejects.toMatchObject({
      code: "failed-precondition",
      details: { reason: "STAGE_CHANGED" },
    });
    expect((await dispute(id))?.escalatedBy).toBe(LENDER);
    expect(await systemMessages(id)).toHaveLength(1);
  });

  it("escalates only once when both parties escalate at the same time", async () => {
    const id = await disputed();

    const results = await Promise.allSettled([
      escalateDispute(db, LENDER, id),
      escalateDispute(db, BORROWER, id),
    ]);

    expect(results.filter((result) => result.status === "fulfilled")).toHaveLength(1);
    expect((await dispute(id))?.stage).toBe("LLM_PROPOSAL");
    expect(await systemMessages(id)).toHaveLength(1);
  });

  it("refuses a settled dispute and someone who takes no part in the rental", async () => {
    const settled = await disputed({ agreedAt: Timestamp.fromDate(NOW) });
    const open = await disputed();

    await expect(escalateDispute(db, LENDER, settled)).rejects.toMatchObject({
      code: "failed-precondition",
    });
    await expect(escalateDispute(db, STRANGER, open)).rejects.toMatchObject({
      code: "permission-denied",
    });
    expect((await dispute(settled))?.stage).toBe("SETTLE");
    expect((await dispute(open))?.stage).toBe("SETTLE");
  });
});
