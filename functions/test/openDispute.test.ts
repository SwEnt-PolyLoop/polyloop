// Made with Claude.

import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { DocumentReference, Firestore, getFirestore } from "firebase-admin/firestore";
import {
  escalateIfOverdue,
  escalateOverdueDisputes,
  openDispute,
  proposeSplit,
} from "../src/dispute";
import { hasSeenLastMessage } from "../src/testing/readTimes";

// The transaction is tested on the emulator; disputeCallables.test.ts checks the callable.
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

const LENDER = "open-dispute-lender";
const BORROWER = "open-dispute-borrower";
const STRANGER = "open-dispute-stranger";
const NOW = new Date("2026-11-04T10:00:00Z");
/** NOW plus the 30 days of direct settlement. */
const ESCALATES_AT = new Date("2026-12-04T10:00:00Z");

/** A rental with [status] between LENDER and BORROWER, with a 40 PP deposit; returns its id. */
async function rentalWith(status: string): Promise<string> {
  const id = randomUUID();
  await db.collection("rentals").doc(id).set({
    ownerId: LENDER,
    borrowerId: BORROWER,
    participants: [LENDER, BORROWER],
    status,
    deposit: 40,
  });
  return id;
}

async function rental(id: string) {
  return (await db.collection("rentals").doc(id).get()).data();
}

async function dispute(id: string) {
  return (await db.collection("disputes").doc(id).get()).data();
}

async function messages(id: string): Promise<string[]> {
  const snap = await db.collection(`rentals/${id}/messages`).get();
  return snap.docs.map((doc) => doc.get("text"));
}

describe("openDispute", () => {
  it("opens the dispute in direct settlement and tells the chat, seen by the lender", async () => {
    const id = await rentalWith("RETURNED");
    const borrowerWallet = { available: 10, heldAsDeposits: 40, heldForRentals: 0 };
    await db.collection("wallets").doc(BORROWER).set(borrowerWallet);

    await openDispute(db, LENDER, id, NOW);

    const opened = await dispute(id);
    expect(opened).toEqual({
      stage: "SETTLE",
      proposals: [],
      escalatesAt: expect.anything(),
      escalatedBy: null,
      agreedAt: null,
    });
    expect(opened?.escalatesAt.toDate()).toEqual(ESCALATES_AT);
    expect(await rental(id)).toMatchObject({
      status: "DISPUTED",
      lastMessage: "Damage flagged · dispute opened",
    });
    expect(await messages(id)).toEqual(["Damage flagged · dispute opened"]);
    expect(hasSeenLastMessage(await rental(id), LENDER)).toBe(true);
    expect(hasSeenLastMessage(await rental(id), BORROWER)).toBe(false);
    // The deposit stays held.
    expect((await db.collection("wallets").doc(BORROWER).get()).data()).toEqual(borrowerWallet);
  });

  it("refuses the borrower, a stranger and a missing rental, and writes nothing", async () => {
    const id = await rentalWith("RETURNED");

    for (const caller of [BORROWER, STRANGER]) {
      await expect(openDispute(db, caller, id, NOW)).rejects.toMatchObject({
        code: "permission-denied",
      });
    }
    await expect(openDispute(db, LENDER, randomUUID(), NOW)).rejects.toMatchObject({
      code: "not-found",
    });
    expect(await dispute(id)).toBeUndefined();
    expect((await rental(id))?.status).toBe("RETURNED");
  });

  it("refuses a rental that is not RETURNED with RENTAL_CHANGED, and writes nothing", async () => {
    // CLOSED stands for "Item is fine" sent first (once #491 exists); DISPUTED without a dispute is corrupt data.
    for (const status of ["ACTIVE", "CLOSED", "DISPUTED"]) {
      const id = await rentalWith(status);

      await expect(openDispute(db, LENDER, id, NOW)).rejects.toMatchObject({
        code: "failed-precondition",
        details: { reason: "RENTAL_CHANGED" },
      });
      expect(await dispute(id)).toBeUndefined();
      expect(await messages(id)).toEqual([]);
    }
  });

  it("succeeds without a change when the dispute is already open", async () => {
    const id = await rentalWith("RETURNED");
    await openDispute(db, LENDER, id, NOW);

    await openDispute(db, LENDER, id, ESCALATES_AT);

    expect((await dispute(id))?.escalatesAt.toDate()).toEqual(ESCALATES_AT);
    expect(await messages(id)).toHaveLength(1);
  });

  it("opens one dispute when two calls race", async () => {
    const id = await rentalWith("RETURNED");

    await Promise.all([openDispute(db, LENDER, id, NOW), openDispute(db, LENDER, id, NOW)]);

    expect((await dispute(id))?.stage).toBe("SETTLE");
    expect(await messages(id)).toHaveLength(1);
  });
});

describe("a dispute opened by openDispute", () => {
  it("takes proposals, without starting the LLM proposal", async () => {
    const id = await rentalWith("RETURNED");
    await openDispute(db, LENDER, id, NOW);

    await proposeSplit(db, LENDER, { rentalId: id, lenderPercent: 70 }, NOW);

    const opened = await dispute(id);
    expect(opened?.proposals).toMatchObject([{ authorId: LENDER, lenderPercent: 70 }]);
    expect(opened?.llmProposal).toBeUndefined();
  });

  it("is escalated by the sweep once the 30 days are up, not before", async () => {
    const id = await rentalWith("RETURNED");
    await openDispute(db, LENDER, id, NOW);
    // The other test files leave their disputes on the shared emulator: the sweep finds them too,
    // so it records every dispute it would escalate and escalates only this one.
    const swept: string[] = [];
    const escalate = (ref: DocumentReference, now: Date) => {
      swept.push(ref.id);
      return ref.id === id ? escalateIfOverdue(ref, now) : Promise.resolve(false);
    };

    await escalateOverdueDisputes(db, new Date(ESCALATES_AT.getTime() - 1), escalate);
    expect(swept).not.toContain(id);
    expect((await dispute(id))?.stage).toBe("SETTLE");

    await escalateOverdueDisputes(db, ESCALATES_AT, escalate);
    expect(swept).toContain(id);

    expect((await dispute(id))?.stage).toBe("LLM_PROPOSAL");
    expect(await messages(id)).toContain(
      "No agreement after 30 days · waiting for the LLM proposal",
    );
  });
});
