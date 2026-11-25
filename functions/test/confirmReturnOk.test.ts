// Made with Claude.

import { randomUUID } from "node:crypto";
import { Firestore, getFirestore } from "firebase-admin/firestore";
import { openDispute } from "../src/dispute";
import { confirmReturnOk } from "../src/handover";
import { confirmReturnOk as confirmReturnOkCallable } from "../src/index";
import { hasSeenLastMessage } from "../src/testing/readTimes";
import { People, asUser, fund, people, rental, rows, wallet } from "../src/testing/walletFixtures";

// Firebase loads this ES module to verify tokens, which Jest cannot load; `run` skips that step.
jest.mock("jose", () => ({}));

// The transaction on the emulator, then the callable as index.ts exports it to Firebase.
let db: Firestore;

beforeAll(() => {
  // Without the emulator, the Admin SDK would write to the real project.
  if (!process.env.FIRESTORE_EMULATOR_HOST) {
    throw new Error(
      "These tests need the Firestore emulator. In functions/, run: " +
        'firebase emulators:exec --only firestore "npm test"',
    );
  }
  db = getFirestore();
});

const NOW = new Date("2026-11-04T10:00:00Z");
const RELEASED = "Item is fine · 40 PP back to the borrower";
const CLOSED_ONLY = "Item is fine · rental closed";

/**
 * A rental with [status] between [who], whose 40 PP deposit (unless [fields] say otherwise) is held
 * in the borrower's wallet with its `deposit_held_<rentalId>` row; returns its id.
 */
async function heldRental(who: People, status: string, fields: object = {}): Promise<string> {
  const id = await rental(who, status, fields);
  await fund(who.borrower, { available: 10, heldAsDeposits: 40, heldForRentals: 0 });
  await db
    .collection("wallets")
    .doc(who.borrower)
    .collection("transactions")
    .doc(`deposit_held_${id}`)
    .set({ type: "DEPOSIT_HELD", amount: -40, itemTitle: "Camping tent", date: NOW });
  return id;
}

async function rentalData(id: string) {
  return (await db.collection("rentals").doc(id).get()).data();
}

async function messages(id: string): Promise<string[]> {
  const snap = await db.collection(`rentals/${id}/messages`).get();
  return snap.docs.map((doc) => doc.get("text"));
}

/** The borrower's history rows other than the hold written by [heldRental]. */
async function newRows(who: People, id: string) {
  return (await rows(who.borrower)).filter((row) => row.id !== `deposit_held_${id}`);
}

describe("confirmReturnOk", () => {
  it("releases the deposit, closes the rental and tells the chat, seen by the lender", async () => {
    const who = people();
    const id = await heldRental(who, "RETURNED");
    await fund(who.lender, { available: 5, heldAsDeposits: 0, heldForRentals: 0 });

    await confirmReturnOk(db, who.lender, id);

    const closed = await rentalData(id);
    expect(closed).toMatchObject({ status: "CLOSED", lastMessage: RELEASED });
    expect(await messages(id)).toEqual([RELEASED]);
    expect(hasSeenLastMessage(closed, who.lender)).toBe(true);
    expect(hasSeenLastMessage(closed, who.borrower)).toBe(false);
    expect(await wallet(who.borrower)).toEqual({
      available: 50,
      heldAsDeposits: 0,
      heldForRentals: 0,
    });
    expect(await newRows(who, id)).toMatchObject([
      {
        id: `deposit_released_${id}`,
        type: "DEPOSIT_RELEASED",
        amount: 40,
        itemTitle: "Camping tent",
      },
    ]);
    expect(await wallet(who.lender)).toEqual({ available: 5, heldAsDeposits: 0, heldForRentals: 0 });
    expect(await rows(who.lender)).toEqual([]);
  });

  it("gives back the deposit less the late fees", async () => {
    const who = people();
    // The late-fee job took its 10 PP from the held 60 PP deposit, leaving 50 held.
    const id = await heldRental(who, "RETURNED", { deposit: 60, lateFees: 10 });
    await fund(who.borrower, { available: 10, heldAsDeposits: 50, heldForRentals: 0 });

    await confirmReturnOk(db, who.lender, id);

    expect(await wallet(who.borrower)).toMatchObject({ available: 60, heldAsDeposits: 0 });
    expect(await newRows(who, id)).toMatchObject([{ amount: 50 }]);
    expect(await messages(id)).toEqual(["Item is fine · 50 PP back to the borrower"]);
  });

  it("succeeds without a change when the same answer is sent twice", async () => {
    const who = people();
    const id = await heldRental(who, "RETURNED");
    await confirmReturnOk(db, who.lender, id);

    await confirmReturnOk(db, who.lender, id);

    expect(await messages(id)).toHaveLength(1);
    expect(await newRows(who, id)).toHaveLength(1);
    expect(await wallet(who.borrower)).toMatchObject({ available: 50, heldAsDeposits: 0 });
  });

  it("refuses the borrower, a stranger and a missing rental, and writes nothing", async () => {
    const who = people();
    const id = await heldRental(who, "RETURNED");

    for (const caller of [who.borrower, "confirm-return-ok-stranger"]) {
      await expect(confirmReturnOk(db, caller, id)).rejects.toMatchObject({
        code: "permission-denied",
      });
    }
    await expect(confirmReturnOk(db, who.lender, randomUUID())).rejects.toMatchObject({
      code: "not-found",
    });
    expect((await rentalData(id))?.status).toBe("RETURNED");
    expect(await messages(id)).toEqual([]);
    expect(await newRows(who, id)).toEqual([]);
  });

  it("refuses a rental that is no longer RETURNED with RENTAL_CHANGED", async () => {
    for (const status of ["ACTIVE", "DISPUTED", "CLOSED"]) {
      const who = people();
      const id = await heldRental(who, status);
      // A CLOSED rental with a dispute was closed by the dispute's agreement, not by this answer.
      if (status === "CLOSED") await db.collection("disputes").doc(id).set({ stage: "SETTLE" });

      await expect(confirmReturnOk(db, who.lender, id)).rejects.toMatchObject({
        code: "failed-precondition",
        details: { reason: "RENTAL_CHANGED" },
      });
      expect((await rentalData(id))?.status).toBe(status);
      expect(await messages(id)).toEqual([]);
      expect(await newRows(who, id)).toEqual([]);
    }
  });
});
