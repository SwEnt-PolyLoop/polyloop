// Made with Claude.

import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { FieldValue, Firestore, Timestamp, getFirestore } from "firebase-admin/firestore";
import { MAX_PROPOSALS, acceptSplit, escalateDispute, proposeSplit } from "./dispute";
import { hasSeenLastMessage } from "./testing/readTimes";
import { People, fund, people, rows, wallet } from "./testing/walletFixtures";

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

  it("takes proposals up to the most a dispute can hold, then refuses the next", async () => {
    // Ends with the lender's 50, so neither call below repeats the split on the table.
    const almostFull = Array.from({ length: MAX_PROPOSALS - 1 }, (_, i) =>
      proposal(i % 2 === 0 ? LENDER : BORROWER, i % 2 === 0 ? 50 : 51),
    );
    const id = await disputed({ proposals: almostFull });

    await proposeSplit(db, BORROWER, { rentalId: id, lenderPercent: 60 }, NOW);
    await expect(
      proposeSplit(db, LENDER, { rentalId: id, lenderPercent: 70 }, NOW),
    ).rejects.toMatchObject({ code: "failed-precondition" });
    expect((await dispute(id))?.proposals).toHaveLength(MAX_PROPOSALS);
  });

  it("starts the list on a dispute stored without proposals", async () => {
    const id = await disputed();
    await db.collection("disputes").doc(id).update({ proposals: FieldValue.delete() });

    await proposeSplit(db, LENDER, { rentalId: id, lenderPercent: 60 }, NOW);

    expect((await dispute(id))?.proposals).toMatchObject([{ authorId: LENDER, lenderPercent: 60 }]);
  });

  it("refuses a stranger, and a missing dispute or rental", async () => {
    const id = await disputed();
    const withoutRental = await disputed();
    await db.collection("rentals").doc(withoutRental).delete();

    await expect(
      proposeSplit(db, STRANGER, { rentalId: id, lenderPercent: 50 }, NOW),
    ).rejects.toMatchObject({ code: "permission-denied" });
    for (const rentalId of [randomUUID(), withoutRental]) {
      await expect(
        proposeSplit(db, LENDER, { rentalId, lenderPercent: 50 }, NOW),
      ).rejects.toMatchObject({ code: "not-found" });
    }
    expect((await dispute(id))?.proposals).toEqual([]);
    expect((await dispute(withoutRental))?.proposals).toEqual([]);
  });

  it("refuses a settled dispute and one that left direct settlement", async () => {
    const settled = await disputed({ agreedAt: Timestamp.fromDate(NOW) });
    const escalated = await disputed({ stage: "LLM_PROPOSAL", escalatedBy: LENDER });

    await expect(
      proposeSplit(db, LENDER, { rentalId: settled, lenderPercent: 50 }, NOW),
    ).rejects.toMatchObject({
      code: "failed-precondition",
      details: { reason: "STAGE_CHANGED" },
    });
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
    await fund(BORROWER, { available: 10, heldAsDeposits: 33, heldForRentals: 0 });
    await fund(LENDER, { available: 5, heldAsDeposits: 0, heldForRentals: 0 });
    await db
      .collection(`wallets/${BORROWER}/transactions`)
      .doc(`deposit_held_${id}`)
      .set({ type: "DEPOSIT_HELD", amount: -33, itemTitle: "" });

    await acceptSplit(db, BORROWER, { rentalId: id, lenderPercent: 50 }, NOW);

    expect((await dispute(id))?.agreedAt.toDate()).toEqual(NOW);
    expect(await rental(id)).toMatchObject({
      status: "CLOSED",
      // The rental keeps its deposit amount; the money moves in the wallets.
      deposit: 33,
      lastMessage: "Dispute settled · 17 PP to the lender, 16 PP back to the borrower",
    });
    expect(await systemMessages(id)).toEqual([
      "Dispute settled · 17 PP to the lender, 16 PP back to the borrower",
    ]);
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

  it("reports a changed split before refusing the caller's own, and writes nothing", async () => {
    // The lender saw the borrower's 30 on one phone, then proposed 70 from another.
    const id = await disputed({ proposals: [proposal(BORROWER, 30), proposal(LENDER, 70)] });

    await expect(
      acceptSplit(db, LENDER, { rentalId: id, lenderPercent: 30 }, NOW),
    ).rejects.toMatchObject({
      code: "failed-precondition",
      details: { reason: "PROPOSAL_CHANGED" },
    });
    expect((await dispute(id))?.agreedAt).toBeNull();
    expect((await rental(id))?.status).toBe("DISPUTED");
  });

  it("refuses a dispute already settled, so it is never settled twice", async () => {
    const id = await disputed({ proposals: [proposal(LENDER, 50)] });
    await acceptSplit(db, BORROWER, { rentalId: id, lenderPercent: 50 }, NOW);

    await expect(
      acceptSplit(db, BORROWER, { rentalId: id, lenderPercent: 50 }, NOW),
    ).rejects.toMatchObject({
      code: "failed-precondition",
      details: { reason: "STAGE_CHANGED" },
    });
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

describe("acceptSplit and the held deposit", () => {
  /**
   * A disputed rental of its own people with 33 PP held in the borrower's wallet (and its hold row
   * unless [hold] is false), and the lender's last proposal at [lenderPercent].
   *
   * @param lenderPercent the lender's share in the proposal waiting for the borrower.
   * @param fields overrides of the rental's fields, such as `lateFees`.
   * @param hold whether the `deposit_held_<rentalId>` row exists.
   * @returns the rental id and its people.
   */
  async function heldDispute(lenderPercent: number, fields: object = {}, hold = true) {
    const who = people();
    const id = randomUUID();
    const deposit = 33;
    await db.collection("rentals").doc(id).set({
      ownerId: who.lender,
      borrowerId: who.borrower,
      participants: [who.lender, who.borrower],
      status: "DISPUTED",
      listingTitle: "Camping tent",
      deposit,
      ...fields,
    });
    await db
      .collection("disputes")
      .doc(id)
      .set({
        stage: "SETTLE",
        proposals: [proposal(who.lender, lenderPercent)],
        escalatesAt: Timestamp.fromDate(ESCALATES_AT),
        escalatedBy: null,
        agreedAt: null,
      });
    await fund(who.borrower, { available: 10, heldAsDeposits: deposit, heldForRentals: 0 });
    await fund(who.lender, { available: 5, heldAsDeposits: 0, heldForRentals: 0 });
    if (hold) {
      await db
        .collection(`wallets/${who.borrower}/transactions`)
        .doc(`deposit_held_${id}`)
        .set({ type: "DEPOSIT_HELD", amount: -deposit, itemTitle: "Camping tent" });
    }
    return { id, who };
  }

  /**
   * Both wallets' available and held deposits, and the rows other than the hold, as
   * `<row id without the rental>:<amount>`.
   *
   * @param who the lender and the borrower.
   * @param id the rental id.
   * @returns what the settlement changed, to compare as a whole.
   */
  async function moved(who: People, id: string) {
    const [borrower, lender] = [await wallet(who.borrower), await wallet(who.lender)];
    const history = async (uid: string) =>
      (await rows(uid))
        .filter((row) => row.id !== `deposit_held_${id}`)
        .map((row) => `${row.id.replace(`_${id}`, "")}:${(row as { amount?: number }).amount}`);
    return {
      borrower: [borrower?.available, borrower?.heldAsDeposits],
      lender: [lender?.available, lender?.heldAsDeposits],
      borrowerRows: await history(who.borrower),
      lenderRows: await history(who.lender),
    };
  }

  it("rounds the borrower's share down: 50% of 33 PP is 16 to the borrower, 17 to the lender", async () => {
    const { id, who } = await heldDispute(50);

    await acceptSplit(db, who.borrower, { rentalId: id, lenderPercent: 50 }, NOW);

    expect(await moved(who, id)).toEqual({
      borrower: [26, 0],
      lender: [22, 0],
      borrowerRows: ["deposit_split:16"],
      lenderRows: ["deposit_split:17"],
    });
    expect(await rows(who.lender)).toEqual([
      expect.objectContaining({ type: "DEPOSIT_SPLIT", itemTitle: "Camping tent" }),
    ]);
  });

  it("gives everything to the lender at 100% and everything back at 0%, with no row for a 0 share", async () => {
    const all = await heldDispute(100);
    const none = await heldDispute(0);

    await acceptSplit(db, all.who.borrower, { rentalId: all.id, lenderPercent: 100 }, NOW);
    await acceptSplit(db, none.who.borrower, { rentalId: none.id, lenderPercent: 0 }, NOW);

    expect(await moved(all.who, all.id)).toEqual({
      borrower: [10, 0],
      lender: [38, 0],
      borrowerRows: [],
      lenderRows: ["deposit_split:33"],
    });
    expect(await moved(none.who, none.id)).toEqual({
      borrower: [43, 0],
      lender: [5, 0],
      borrowerRows: ["deposit_split:33"],
      lenderRows: [],
    });
  });

  it("takes the late fees off the deposit first and does not pay them again", async () => {
    const { id, who } = await heldDispute(50, { lateFees: 13 });
    // The late-fee job already took 13 PP from the held deposit and paid the lender.
    await fund(who.borrower, { available: 10, heldAsDeposits: 20, heldForRentals: 0 });
    await fund(who.lender, { available: 18, heldAsDeposits: 0, heldForRentals: 0 });

    await acceptSplit(db, who.borrower, { rentalId: id, lenderPercent: 50 }, NOW);

    expect(await moved(who, id)).toMatchObject({
      borrower: [20, 0],
      lender: [28, 0],
      borrowerRows: ["deposit_split:10"],
      lenderRows: ["deposit_split:10"],
    });
    expect((await rental(id))?.lastMessage).toBe(
      "Dispute settled · 10 PP to the lender, 10 PP back to the borrower",
    );
  });

  it("moves nothing when no deposit was held, but still settles the dispute, and the chat only says the split", async () => {
    const { id, who } = await heldDispute(50, {}, false);

    await acceptSplit(db, who.borrower, { rentalId: id, lenderPercent: 50 }, NOW);

    expect(await systemMessages(id)).toEqual(["Dispute settled · 50% to the lender"]);
    expect((await rental(id))?.lastMessage).toBe("Dispute settled · 50% to the lender");
    expect(await moved(who, id)).toEqual({
      borrower: [10, 33],
      lender: [5, 0],
      borrowerRows: [],
      lenderRows: [],
    });
    expect(await rental(id)).toMatchObject({ status: "CLOSED" });
  });

  it("moves the money once when the same acceptance is sent twice at once", async () => {
    const { id, who } = await heldDispute(50);

    const results = await Promise.allSettled([
      acceptSplit(db, who.borrower, { rentalId: id, lenderPercent: 50 }, NOW),
      acceptSplit(db, who.borrower, { rentalId: id, lenderPercent: 50 }, NOW),
    ]);

    expect(results.filter((r) => r.status === "fulfilled")).toHaveLength(1);
    expect(results.find((r) => r.status === "rejected")).toMatchObject({
      reason: { code: "failed-precondition", details: { reason: "STAGE_CHANGED" } },
    });
    expect(await moved(who, id)).toMatchObject({ borrower: [26, 0], lender: [22, 0] });
    expect(await systemMessages(id)).toHaveLength(1);
  });

  it("moves nothing for a stranger or for the author of the proposal", async () => {
    const { id, who } = await heldDispute(50);

    await expect(
      acceptSplit(db, STRANGER, { rentalId: id, lenderPercent: 50 }, NOW),
    ).rejects.toMatchObject({ code: "permission-denied" });
    await expect(
      acceptSplit(db, who.lender, { rentalId: id, lenderPercent: 50 }, NOW),
    ).rejects.toMatchObject({ code: "permission-denied" });

    expect(await moved(who, id)).toMatchObject({ borrower: [10, 33], lender: [5, 0] });
    expect((await dispute(id))?.agreedAt).toBeNull();
  });

  it("settles nothing and moves nothing when the held deposits are below the deposit", async () => {
    const { id, who } = await heldDispute(50);
    await fund(who.borrower, { available: 10, heldAsDeposits: 5, heldForRentals: 0 });

    await expect(
      acceptSplit(db, who.borrower, { rentalId: id, lenderPercent: 50 }, NOW),
    ).rejects.toMatchObject({ code: "internal" });

    expect(await moved(who, id)).toMatchObject({ borrower: [10, 5], lender: [5, 0] });
    expect((await dispute(id))?.agreedAt).toBeNull();
    expect((await rental(id))?.status).toBe("DISPUTED");
  });
});

describe("who has seen each system message", () => {
  it("the participant who accepts a split or escalates, not the other one", async () => {
    const settled = await disputed({ proposals: [proposal(LENDER, 50)] });
    const escalated = await disputed();

    await acceptSplit(db, BORROWER, { rentalId: settled, lenderPercent: 50 }, NOW);
    await escalateDispute(db, BORROWER, escalated);

    for (const id of [settled, escalated]) {
      expect(hasSeenLastMessage(await rental(id), BORROWER)).toBe(true);
      expect(hasSeenLastMessage(await rental(id), LENDER)).toBe(false);
    }
  });

  it("nobody, on the escalation after 30 days, when a participant's call starts it", async () => {
    const id = await disputed();
    const read = Timestamp.fromDate(NOW);
    await db
      .collection("rentals")
      .doc(id)
      .update({ lastReadAt: { [LENDER]: read, [BORROWER]: read } });

    await expect(escalateDispute(db, BORROWER, id, ESCALATES_AT)).rejects.toMatchObject({
      details: { reason: "STAGE_CHANGED" },
    });

    expect((await dispute(id))?.escalatedBy).toBeNull();
    expect(hasSeenLastMessage(await rental(id), BORROWER)).toBe(false);
    expect(hasSeenLastMessage(await rental(id), LENDER)).toBe(false);
  });
});

describe("escalateDispute", () => {
  it("moves the dispute on to the LLM proposal, keeping escalatesAt", async () => {
    const id = await disputed();

    await escalateDispute(db, BORROWER, id, NOW);

    const data = await dispute(id);
    expect(data).toMatchObject({
      stage: "LLM_PROPOSAL",
      escalatedBy: BORROWER,
      llmProposal: { status: "PENDING", attempt: 1 },
    });
    expect(data?.escalatesAt.toDate()).toEqual(ESCALATES_AT);
    expect((await rental(id))?.status).toBe("DISPUTED");
    expect(await systemMessages(id)).toEqual([
      "Dispute escalated · waiting for the LLM proposal",
    ]);
  });

  it("refuses a dispute that already moved on, without a second message", async () => {
    const id = await disputed();
    await escalateDispute(db, LENDER, id, NOW);

    await expect(escalateDispute(db, BORROWER, id, NOW)).rejects.toMatchObject({
      code: "failed-precondition",
      details: { reason: "STAGE_CHANGED" },
    });
    expect((await dispute(id))?.escalatedBy).toBe(LENDER);
    expect(await systemMessages(id)).toHaveLength(1);
  });

  it("escalates only once when both parties escalate at the same time", async () => {
    const id = await disputed();

    const results = await Promise.allSettled([
      escalateDispute(db, LENDER, id, NOW),
      escalateDispute(db, BORROWER, id, NOW),
    ]);

    expect(results.filter((result) => result.status === "fulfilled")).toHaveLength(1);
    expect((await dispute(id))?.stage).toBe("LLM_PROPOSAL");
    expect(await systemMessages(id)).toHaveLength(1);
  });
});

describe("a rental that is not DISPUTED", () => {
  it("refuses to propose, accept or escalate, without the race reason, and writes nothing", async () => {
    const id = await disputed({ proposals: [proposal(LENDER, 50)] });
    await db.collection("rentals").doc(id).update({ status: "LOST" });

    for (const action of [
      () => proposeSplit(db, LENDER, { rentalId: id, lenderPercent: 60 }, NOW),
      () => acceptSplit(db, BORROWER, { rentalId: id, lenderPercent: 50 }, NOW),
      () => escalateDispute(db, LENDER, id, NOW),
    ]) {
      const error = await action().catch((e) => e);
      expect(error).toMatchObject({ code: "failed-precondition" });
      expect(error.details).toBeUndefined();
    }
    expect(await rental(id)).toMatchObject({ status: "LOST" });
    expect(await dispute(id)).toMatchObject({
      stage: "SETTLE",
      proposals: [{ authorId: LENDER, lenderPercent: 50 }],
      agreedAt: null,
    });
    expect(await systemMessages(id)).toEqual([]);
  });
});

describe("escalatesAt, before the sweep", () => {
  const SWEEP_MESSAGE = "No agreement after 30 days · waiting for the LLM proposal";

  it("escalates instead of adding a proposal, from escalatesAt on", async () => {
    const id = await disputed();

    await expect(
      proposeSplit(db, LENDER, { rentalId: id, lenderPercent: 60 }, ESCALATES_AT),
    ).rejects.toMatchObject({ details: { reason: "STAGE_CHANGED" } });

    expect(await dispute(id)).toMatchObject({
      stage: "LLM_PROPOSAL",
      escalatedBy: null,
      proposals: [],
    });
    expect(await systemMessages(id)).toEqual([SWEEP_MESSAGE]);
  });

  it("escalates instead of settling", async () => {
    const id = await disputed({ proposals: [proposal(LENDER, 50)] });

    await expect(
      acceptSplit(db, BORROWER, { rentalId: id, lenderPercent: 50 }, ESCALATES_AT),
    ).rejects.toMatchObject({ details: { reason: "STAGE_CHANGED" } });

    expect(await dispute(id)).toMatchObject({ stage: "LLM_PROPOSAL", agreedAt: null });
    expect((await rental(id))?.status).toBe("DISPUTED");
    expect(await systemMessages(id)).toEqual([SWEEP_MESSAGE]);
  });

  it("lets neither a stranger nor a settled dispute trigger the escalation", async () => {
    const open = await disputed();
    const settled = await disputed({ agreedAt: Timestamp.fromDate(NOW) });

    await expect(
      proposeSplit(db, STRANGER, { rentalId: open, lenderPercent: 60 }, ESCALATES_AT),
    ).rejects.toMatchObject({ code: "permission-denied" });
    await expect(escalateDispute(db, LENDER, settled, ESCALATES_AT)).rejects.toMatchObject({
      details: { reason: "STAGE_CHANGED" },
    });

    for (const id of [open, settled]) {
      expect((await dispute(id))?.stage).toBe("SETTLE");
      expect(await systemMessages(id)).toEqual([]);
    }
  });

  it("records an escalation by hand as the automatic one", async () => {
    const id = await disputed();

    await expect(escalateDispute(db, LENDER, id, ESCALATES_AT)).rejects.toMatchObject({
      details: { reason: "STAGE_CHANGED" },
    });

    expect(await dispute(id)).toMatchObject({ stage: "LLM_PROPOSAL", escalatedBy: null });
    expect(await systemMessages(id)).toEqual([SWEEP_MESSAGE]);
  });

  it("never escalates a dispute without escalatesAt", async () => {
    const id = await disputed({ escalatesAt: null });

    await proposeSplit(db, LENDER, { rentalId: id, lenderPercent: 60 }, ESCALATES_AT);

    expect(await dispute(id)).toMatchObject({
      stage: "SETTLE",
      proposals: [{ lenderPercent: 60 }],
    });
    expect(await systemMessages(id)).toEqual([]);
  });
});
