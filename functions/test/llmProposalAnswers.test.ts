// Made with Claude.

import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { Firestore, Timestamp, getFirestore } from "firebase-admin/firestore";
import {
  REJECTED_MESSAGE,
  rejectLlmProposal,
  retryLlmProposal,
} from "../src/llmProposalAnswers";
import { hasSeenLastMessage } from "../src/testing/readTimes";
import { People, fund, people, wallet } from "../src/testing/walletFixtures";

// Firebase loads this ES module to verify tokens, which Jest cannot load; these tests do not sign in.
jest.mock("jose", () => ({}));

// The transactions are tested on the emulator; the input checks are in rentalRules.test.ts.
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

  it("refuses a pending proposal", async () => {
    const proposal = { status: "PENDING", attempt: 2 };
    const { id, who } = await escalated(proposal);

    await expect(rejectLlmProposal(db, who.lender, id)).rejects.toMatchObject({
      code: "failed-precondition",
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

  it("lets only one of a rejection and a retry sent together win", async () => {
    const { id, who } = await escalated(failed(1));

    const results = await Promise.allSettled([
      rejectLlmProposal(db, who.lender, id),
      retryLlmProposal(db, who.borrower, id),
    ]);

    expect(results.filter((r) => r.status === "fulfilled")).toHaveLength(1);
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

  it("refuses a proposal that did not fail", async () => {
    for (const proposal of [ready(), { status: "PENDING", attempt: 1 }]) {
      const { id, who } = await escalated(proposal);

      await expect(retryLlmProposal(db, who.lender, id)).rejects.toMatchObject({
        code: "failed-precondition",
      });
      await expectUnchanged(id, who, proposal);
    }
  });

  it("starts one attempt when both parties retry at the same time", async () => {
    const { id, who } = await escalated(failed(1));

    const results = await Promise.allSettled([
      retryLlmProposal(db, who.lender, id),
      retryLlmProposal(db, who.borrower, id),
    ]);

    expect(results.filter((r) => r.status === "fulfilled")).toHaveLength(1);
    expect((await dispute(id))?.llmProposal).toEqual({ status: "PENDING", attempt: 2 });
  });
});

describe("every answer", () => {
  /** Each answer, called by [caller] on the dispute of [id]. */
  const answers = [
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
