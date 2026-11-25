// Made with Claude.

import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { FieldValue, Firestore, Timestamp, getFirestore } from "firebase-admin/firestore";
import {
  REJECTED_MESSAGE,
  acceptLlmProposal,
  rejectLlmProposal,
  retryLlmProposal,
} from "../src/llmProposalAnswers";
import { hasSeenLastMessage } from "../src/testing/readTimes";
import { People, fund, people, rows, wallet } from "../src/testing/walletFixtures";

// Firebase loads this ES module to verify tokens, which Jest cannot load; these tests do not sign in.
jest.mock("jose", () => ({}));

// The transactions are tested on the emulator; the input check is in disputeRules.test.ts.
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

const STRANGER = "llm-answer-stranger";
const NOW = new Date("2026-11-24T10:00:00Z");
const DEPOSIT = 33;

/** A ready proposal on the first attempt, giving the lender [lenderPercent]. */
function ready(lenderPercent = 50, fields: object = {}) {
  return {
    status: "READY",
    attempt: 1,
    lenderPercent,
    reasoning: "A scratch on the lid.",
    model: "test-model",
    photoIds: [],
    createdAt: Timestamp.fromDate(NOW),
    ...fields,
  };
}

/** A failed proposal on [attempt]. */
function failed(attempt = 1) {
  return { status: "FAILED", attempt, createdAt: Timestamp.fromDate(NOW) };
}

/**
 * A disputed rental of its own people, its dispute in `LLM_PROPOSAL` with [llmProposal], and
 * [DEPOSIT] PP held in the borrower's wallet with its hold row.
 *
 * @param llmProposal the dispute's LLM proposal.
 * @param dispute overrides of the dispute's fields.
 * @param rentalFields overrides of the rental's fields.
 * @returns the rental id and its people.
 */
async function escalated(llmProposal: object, dispute: object = {}, rentalFields: object = {}) {
  const who = people();
  const id = randomUUID();
  await db.collection("rentals").doc(id).set({
    ownerId: who.lender,
    borrowerId: who.borrower,
    participants: [who.lender, who.borrower],
    status: "DISPUTED",
    listingTitle: "Camping tent",
    deposit: DEPOSIT,
    ...rentalFields,
  });
  await db
    .collection("disputes")
    .doc(id)
    .set({
      stage: "LLM_PROPOSAL",
      proposals: [],
      escalatesAt: Timestamp.fromDate(new Date("2026-11-30T10:00:00Z")),
      escalatedBy: who.lender,
      agreedAt: null,
      llmProposal,
      ...dispute,
    });
  await fund(who.borrower, { available: 10, heldAsDeposits: DEPOSIT, heldForRentals: 0 });
  await fund(who.lender, { available: 5, heldAsDeposits: 0, heldForRentals: 0 });
  await db
    .collection(`wallets/${who.borrower}/transactions`)
    .doc(`deposit_held_${id}`)
    .set({ type: "DEPOSIT_HELD", amount: -DEPOSIT, itemTitle: "Camping tent" });
  return { id, who };
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

/** Both wallets' available and held deposits, to compare as a whole. */
async function balances(who: People) {
  const [borrower, lender] = [await wallet(who.borrower), await wallet(who.lender)];
  return {
    borrower: [borrower?.available, borrower?.heldAsDeposits],
    lender: [lender?.available, lender?.heldAsDeposits],
  };
}

/** Expects nothing to have changed on a dispute that started with [llmProposal]. */
async function expectUnchanged(id: string, who: People, llmProposal: object) {
  expect(await dispute(id)).toMatchObject({
    stage: "LLM_PROPOSAL",
    agreedAt: null,
    escalatedBy: who.lender,
    llmProposal,
  });
  expect((await rental(id))?.status).toBe("DISPUTED");
  expect(await balances(who)).toEqual({ borrower: [10, DEPOSIT], lender: [5, 0] });
  expect(await systemMessages(id)).toEqual([]);
}

describe("acceptLlmProposal", () => {
  it("records one acceptance, without settling and without a chat message", async () => {
    const { id, who } = await escalated(ready());

    await acceptLlmProposal(db, who.lender, { rentalId: id, attempt: 1 }, NOW);

    expect(await dispute(id)).toMatchObject({
      agreedAt: null,
      llmProposal: { ...ready(), acceptedBy: [who.lender] },
    });
    expect((await rental(id))?.status).toBe("DISPUTED");
    expect(await balances(who)).toEqual({ borrower: [10, DEPOSIT], lender: [5, 0] });
    expect(await systemMessages(id)).toEqual([]);
  });

  it("settles on both acceptances: rental closed, deposit split, shares in the chat", async () => {
    const { id, who } = await escalated(ready(50));

    await acceptLlmProposal(db, who.lender, { rentalId: id, attempt: 1 }, NOW);
    await acceptLlmProposal(db, who.borrower, { rentalId: id, attempt: 1 }, NOW);

    const settled = await dispute(id);
    expect(settled?.agreedAt.toDate()).toEqual(NOW);
    expect(settled?.llmProposal.acceptedBy).toEqual([who.lender, who.borrower]);
    expect(settled?.stage).toBe("LLM_PROPOSAL");
    const message = "Dispute settled · 17 PP to the lender, 16 PP back to the borrower";
    expect(await rental(id)).toMatchObject({ status: "CLOSED", lastMessage: message });
    expect(await systemMessages(id)).toEqual([message]);
    // 50% of 33 PP: 16 PP back to the borrower (rounded down), 17 PP to the lender.
    expect(await balances(who)).toEqual({ borrower: [26, 0], lender: [22, 0] });
    expect((await rows(who.lender)).map((row) => row.id)).toEqual([`deposit_split_${id}`]);
    expect(hasSeenLastMessage(await rental(id), who.borrower)).toBe(true);
    expect(hasSeenLastMessage(await rental(id), who.lender)).toBe(false);
  });

  it("takes the late fees off the deposit before splitting it", async () => {
    const { id, who } = await escalated(ready(50), {}, { lateFees: 13 });
    await fund(who.borrower, { available: 10, heldAsDeposits: 20, heldForRentals: 0 });

    await acceptLlmProposal(db, who.borrower, { rentalId: id, attempt: 1 }, NOW);
    await acceptLlmProposal(db, who.lender, { rentalId: id, attempt: 1 }, NOW);

    expect((await rental(id))?.lastMessage).toBe(
      "Dispute settled · 10 PP to the lender, 10 PP back to the borrower",
    );
    expect(await balances(who)).toEqual({ borrower: [20, 0], lender: [15, 0] });
  });

  it("changes nothing when the same party accepts again", async () => {
    const { id, who } = await escalated(ready());

    await acceptLlmProposal(db, who.lender, { rentalId: id, attempt: 1 }, NOW);
    await acceptLlmProposal(db, who.lender, { rentalId: id, attempt: 1 }, NOW);

    expect(await dispute(id)).toMatchObject({
      agreedAt: null,
      llmProposal: { acceptedBy: [who.lender] },
    });
  });

  it("refuses an attempt other than the one on the table, so nobody accepts an unseen proposal", async () => {
    const proposal = ready(80, { attempt: 2 });
    const { id, who } = await escalated(proposal);

    await expect(
      acceptLlmProposal(db, who.borrower, { rentalId: id, attempt: 1 }, NOW),
    ).rejects.toMatchObject({
      code: "failed-precondition",
      details: { reason: "LLM_PROPOSAL_CHANGED" },
    });
    await expectUnchanged(id, who, proposal);
  });

  it("refuses a pending or failed proposal, without the race reason", async () => {
    for (const proposal of [{ status: "PENDING", attempt: 1 }, failed()]) {
      const { id, who } = await escalated(proposal);

      const error = await acceptLlmProposal(db, who.lender, { rentalId: id, attempt: 1 }, NOW)
        .then(() => null)
        .catch((e) => e);

      expect(error).toMatchObject({ code: "failed-precondition" });
      expect(error.details).toBeUndefined();
      await expectUnchanged(id, who, proposal);
    }
  });

  it("refuses a ready proposal whose split is not a whole percentage", async () => {
    const proposal = ready(50, { lenderPercent: 50.5 });
    const { id, who } = await escalated(proposal);

    await expect(
      acceptLlmProposal(db, who.lender, { rentalId: id, attempt: 1 }, NOW),
    ).rejects.toMatchObject({ code: "failed-precondition" });
    await expectUnchanged(id, who, proposal);
  });

  it("settles once when both parties accept at the same time", async () => {
    const { id, who } = await escalated(ready(50));

    await Promise.all([
      acceptLlmProposal(db, who.lender, { rentalId: id, attempt: 1 }, NOW),
      acceptLlmProposal(db, who.borrower, { rentalId: id, attempt: 1 }, NOW),
    ]);

    expect((await rental(id))?.status).toBe("CLOSED");
    expect(await systemMessages(id)).toHaveLength(1);
    expect(await balances(who)).toEqual({ borrower: [26, 0], lender: [22, 0] });
  });

  it("settles nothing when the held deposits are below the deposit", async () => {
    const { id, who } = await escalated(ready(50, { acceptedBy: [] }));
    await acceptLlmProposal(db, who.lender, { rentalId: id, attempt: 1 }, NOW);
    await fund(who.borrower, { available: 10, heldAsDeposits: 5, heldForRentals: 0 });

    await expect(
      acceptLlmProposal(db, who.borrower, { rentalId: id, attempt: 1 }, NOW),
    ).rejects.toMatchObject({ code: "internal" });

    expect(await dispute(id)).toMatchObject({
      agreedAt: null,
      llmProposal: { acceptedBy: [who.lender] },
    });
    expect((await rental(id))?.status).toBe("DISPUTED");
  });
});

describe("rejectLlmProposal", () => {
  it("sends a ready or a failed proposal to court, with the caller and a chat message", async () => {
    for (const proposal of [ready(), failed(3)]) {
      const { id, who } = await escalated(proposal);

      await rejectLlmProposal(db, who.borrower, id);

      expect(await dispute(id)).toMatchObject({
        stage: "COURT",
        escalatedBy: who.borrower,
        agreedAt: null,
        llmProposal: proposal,
      });
      expect((await rental(id))?.status).toBe("DISPUTED");
      expect(await systemMessages(id)).toEqual([REJECTED_MESSAGE]);
      expect(hasSeenLastMessage(await rental(id), who.borrower)).toBe(true);
      expect(hasSeenLastMessage(await rental(id), who.lender)).toBe(false);
      // The deposit stays held.
      expect(await balances(who)).toEqual({ borrower: [10, DEPOSIT], lender: [5, 0] });
    }
  });

  it("rejects after one party accepted, the accepting party included", async () => {
    const lenderAccepted = await escalated(ready());
    const borrowerAccepted = await escalated(ready());
    await acceptLlmProposal(db, lenderAccepted.who.lender, {
      rentalId: lenderAccepted.id,
      attempt: 1,
    });
    await acceptLlmProposal(db, borrowerAccepted.who.borrower, {
      rentalId: borrowerAccepted.id,
      attempt: 1,
    });

    await rejectLlmProposal(db, lenderAccepted.who.borrower, lenderAccepted.id);
    await rejectLlmProposal(db, borrowerAccepted.who.borrower, borrowerAccepted.id);

    for (const { id } of [lenderAccepted, borrowerAccepted]) {
      expect(await dispute(id)).toMatchObject({ stage: "COURT", agreedAt: null });
    }
  });

  it("refuses a pending proposal as changed: the other party asked for a new attempt", async () => {
    const proposal = { status: "PENDING", attempt: 2 };
    const { id, who } = await escalated(proposal);

    await expect(rejectLlmProposal(db, who.lender, id)).rejects.toMatchObject({
      code: "failed-precondition",
      details: { reason: "LLM_PROPOSAL_CHANGED" },
    });
    await expectUnchanged(id, who, proposal);
  });

  it("moves the dispute once when both parties reject at the same time", async () => {
    const { id, who } = await escalated(ready());

    const results = await Promise.allSettled([
      rejectLlmProposal(db, who.lender, id),
      rejectLlmProposal(db, who.borrower, id),
    ]);

    expect(results.filter((r) => r.status === "fulfilled")).toHaveLength(1);
    expect(results.find((r) => r.status === "rejected")).toMatchObject({
      reason: { code: "failed-precondition", details: { reason: "STAGE_CHANGED" } },
    });
    expect(await systemMessages(id)).toEqual([REJECTED_MESSAGE]);
  });

  it("lets only one of an acceptance and a rejection sent together win", async () => {
    const { id, who } = await escalated(ready(50, { acceptedBy: [] }));
    await acceptLlmProposal(db, who.lender, { rentalId: id, attempt: 1 }, NOW);

    const results = await Promise.allSettled([
      acceptLlmProposal(db, who.borrower, { rentalId: id, attempt: 1 }, NOW),
      rejectLlmProposal(db, who.lender, id),
    ]);

    expect(results.filter((r) => r.status === "fulfilled")).toHaveLength(1);
    expect(results.find((r) => r.status === "rejected")).toMatchObject({
      reason: { code: "failed-precondition", details: { reason: "STAGE_CHANGED" } },
    });
    const after = await dispute(id);
    // Either settled on the LLM proposal, or in court: never both.
    expect(after?.stage === "COURT").toBe(!after?.agreedAt);
    expect(await systemMessages(id)).toHaveLength(1);
  });

  it("lets only one of a rejection and a retry sent together win", async () => {
    const { id, who } = await escalated(failed(1));

    const results = await Promise.allSettled([
      rejectLlmProposal(db, who.lender, id),
      retryLlmProposal(db, who.borrower, id),
    ]);

    expect(results.filter((r) => r.status === "fulfilled")).toHaveLength(1);
    // The loser lost a race, so the app follows the dispute instead of showing an error.
    const loser = results.find((r) => r.status === "rejected") as PromiseRejectedResult;
    expect(["STAGE_CHANGED", "LLM_PROPOSAL_CHANGED"]).toContain(loser.reason.details?.reason);
    const after = await dispute(id);
    // Either in court with the failed proposal, or waiting for the second attempt: never both.
    expect(after?.stage === "COURT").toBe(after?.llmProposal.status === "FAILED");
  });
});

describe("retryLlmProposal", () => {
  it("starts the next attempt of a failed proposal, without a chat message", async () => {
    const { id, who } = await escalated(failed(1));

    await retryLlmProposal(db, who.borrower, id);
    expect((await dispute(id))?.llmProposal).toEqual({ status: "PENDING", attempt: 2 });

    await db.collection("disputes").doc(id).update({ llmProposal: failed(2) });
    await retryLlmProposal(db, who.lender, id);
    expect((await dispute(id))?.llmProposal).toEqual({ status: "PENDING", attempt: 3 });
    expect(await systemMessages(id)).toEqual([]);
  });

  it("refuses a fourth attempt", async () => {
    const proposal = failed(3);
    const { id, who } = await escalated(proposal);

    await expect(retryLlmProposal(db, who.lender, id)).rejects.toMatchObject({
      code: "failed-precondition",
    });
    await expectUnchanged(id, who, proposal);
  });

  it("refuses a proposal that did not fail as changed, so a lost answer reads as a race", async () => {
    for (const proposal of [ready(), { status: "PENDING", attempt: 1 }]) {
      const { id, who } = await escalated(proposal);

      await expect(retryLlmProposal(db, who.lender, id)).rejects.toMatchObject({
        code: "failed-precondition",
        details: { reason: "LLM_PROPOSAL_CHANGED" },
      });
      await expectUnchanged(id, who, proposal);
    }
  });

  it("refuses the same retry sent again after its answer was lost", async () => {
    const { id, who } = await escalated(failed(1));
    await retryLlmProposal(db, who.lender, id);

    await expect(retryLlmProposal(db, who.lender, id)).rejects.toMatchObject({
      code: "failed-precondition",
      details: { reason: "LLM_PROPOSAL_CHANGED" },
    });
    expect((await dispute(id))?.llmProposal).toEqual({ status: "PENDING", attempt: 2 });
  });

  it("refuses an invalid attempt number without a reason", async () => {
    const proposal = { status: "FAILED", attempt: "1" };
    const { id, who } = await escalated(proposal);

    const error = await retryLlmProposal(db, who.lender, id).catch((e) => e);

    expect(error).toMatchObject({ code: "failed-precondition" });
    expect(error.details).toBeUndefined();
    await expectUnchanged(id, who, proposal);
  });

  it("starts one attempt when both parties retry at the same time", async () => {
    const { id, who } = await escalated(failed(1));

    const results = await Promise.allSettled([
      retryLlmProposal(db, who.lender, id),
      retryLlmProposal(db, who.borrower, id),
    ]);

    expect(results.filter((r) => r.status === "fulfilled")).toHaveLength(1);
    expect(results.find((r) => r.status === "rejected")).toMatchObject({
      reason: { code: "failed-precondition", details: { reason: "LLM_PROPOSAL_CHANGED" } },
    });
    expect((await dispute(id))?.llmProposal).toEqual({ status: "PENDING", attempt: 2 });
  });
});

describe("every answer", () => {
  /** Each answer, called by [caller] on the dispute of [id]. */
  const answers = [
    (id: string, caller: string) =>
      acceptLlmProposal(db, caller, { rentalId: id, attempt: 1 }, NOW),
    (id: string, caller: string) => rejectLlmProposal(db, caller, id),
    (id: string, caller: string) => retryLlmProposal(db, caller, id),
  ];

  it("refuses a stranger, and a missing dispute or rental", async () => {
    for (const answer of answers) {
      const proposal = failed(1);
      const { id, who } = await escalated(proposal);
      const withoutRental = await escalated(proposal);
      await db.collection("rentals").doc(withoutRental.id).delete();

      await expect(answer(id, STRANGER)).rejects.toMatchObject({ code: "permission-denied" });
      for (const rentalId of [randomUUID(), withoutRental.id]) {
        await expect(answer(rentalId, who.lender)).rejects.toMatchObject({ code: "not-found" });
      }
      await expectUnchanged(id, who, proposal);
    }
  });

  it("refuses a dispute in another stage or already settled, as a lost race", async () => {
    for (const answer of answers) {
      for (const fields of [
        { stage: "SETTLE" },
        { stage: "COURT" },
        { agreedAt: Timestamp.fromDate(NOW) },
      ]) {
        const { id, who } = await escalated(failed(1), fields);

        await expect(answer(id, who.lender)).rejects.toMatchObject({
          code: "failed-precondition",
          details: { reason: "STAGE_CHANGED" },
        });
        expect(await systemMessages(id)).toEqual([]);
      }
    }
  });

  it("refuses a dispute escalated before the LLM proposal existed, without a reason", async () => {
    for (const answer of answers) {
      const { id, who } = await escalated({});
      await db.collection("disputes").doc(id).update({ llmProposal: FieldValue.delete() });

      const error = await answer(id, who.lender).catch((e) => e);

      expect(error).toMatchObject({ code: "failed-precondition" });
      expect(error.details).toBeUndefined();
      expect((await dispute(id))?.stage).toBe("LLM_PROPOSAL");
    }
  });

  it("refuses a rental that is not DISPUTED, without the race reason", async () => {
    for (const answer of answers) {
      const proposal = failed(1);
      const { id, who } = await escalated(proposal, {}, { status: "ACTIVE" });

      const error = await answer(id, who.lender)
        .then(() => null)
        .catch((e) => e);

      expect(error).toMatchObject({ code: "failed-precondition" });
      expect(error.details).toBeUndefined();
      expect((await dispute(id))?.llmProposal).toEqual(proposal);
      expect((await rental(id))?.status).toBe("ACTIVE");
    }
  });
});
