// Made with Claude.

import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { Firestore, getFirestore } from "firebase-admin/firestore";
import {
  INSUFFICIENT_FUNDS,
  Wallet,
  readWallet,
  rentalHeldRowId,
  requireFunds,
  writeRentalHold,
} from "../src/wallet";
import { rows, wallet } from "../src/testing/walletFixtures";

// The helpers run inside a transaction, so they are tested on the Firestore emulator.
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

const funds = (available: number): Wallet => ({ available, heldAsDeposits: 7, heldForRentals: 4 });

describe("requireFunds", () => {
  it("lets through an amount the available balance covers exactly", () => {
    expect(() => requireFunds(funds(36), 36)).not.toThrow();
  });

  it("refuses one PP more with the reason the app reads and the caller's message", () => {
    expect(() => requireFunds(funds(35), 36, "Insufficient funds to hold the rental price.")).toThrow(
      expect.objectContaining({
        code: "failed-precondition",
        message: "Insufficient funds to hold the rental price.",
        details: { reason: INSUFFICIENT_FUNDS },
      }),
    );
  });

  it("names the deposit when the caller gives no message, as the deposit holds did", () => {
    expect(() => requireFunds(funds(0), 1)).toThrow("Insufficient funds to hold the deposit.");
  });
});

describe("writeRentalHold", () => {
  it("moves the price from available to heldForRentals, with one row of -price", async () => {
    const uid = randomUUID();
    const rentalId = randomUUID();
    await db.collection("wallets").doc(uid).set(funds(50));

    await db.runTransaction(async (tx) => {
      const read = await readWallet(tx, db, uid);
      writeRentalHold(tx, db, uid, read, 36, rentalId, "Camping tent");
    });

    expect(await wallet(uid)).toEqual({ available: 14, heldAsDeposits: 7, heldForRentals: 40 });
    expect(await rows(uid)).toEqual([
      expect.objectContaining({
        id: `rental_price_held_${rentalId}`,
        type: "RENTAL_PAYMENT",
        amount: -36,
        itemTitle: "Camping tent",
      }),
    ]);
  });

  it("names the row after the rental, so a second hold of the same rental finds it", () => {
    expect(rentalHeldRowId("r1")).toBe("rental_price_held_r1");
  });
});
