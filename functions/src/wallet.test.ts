// Made with Claude.

import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { Firestore, getFirestore } from "firebase-admin/firestore";
import {
  Wallet,
  hasTransaction,
  readWallet,
  rentalHeldRowId,
  rentalReleasedRowId,
  writeRentalHold,
  writeRentalRelease,
  writeWallet,
} from "./wallet";

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

describe("readWallet", () => {
  it("reads a missing wallet as zeros", async () => {
    const wallet = await db.runTransaction((tx) => readWallet(tx, db, randomUUID()));

    expect(wallet).toEqual({ available: 0, heldAsDeposits: 0, heldForRentals: 0 });
  });

  it("reads a missing, negative or non-integer field as 0 and keeps the valid ones", async () => {
    const uid = randomUUID();
    await db.collection("wallets").doc(uid).set({ available: 70, heldAsDeposits: -3, heldForRentals: 1.5 });

    const wallet = await db.runTransaction((tx) => readWallet(tx, db, uid));

    expect(wallet).toEqual({ available: 70, heldAsDeposits: 0, heldForRentals: 0 });
  });
});

describe("writeWallet", () => {
  it("creates the wallet and its row, and hasTransaction then finds the row", async () => {
    const uid = randomUUID();
    const wallet = { available: 5, heldAsDeposits: 0, heldForRentals: 0 };
    const row = { id: "row-1", type: "RECHARGE", amount: 5, itemTitle: "" };

    expect(await db.runTransaction((tx) => hasTransaction(tx, db, uid, row.id))).toBe(false);
    await db.runTransaction(async (tx) => writeWallet(tx, db, uid, wallet, row));

    expect((await db.collection("wallets").doc(uid).get()).data()).toEqual(wallet);
    const saved = await db.collection("wallets").doc(uid).collection("transactions").doc("row-1").get();
    expect(saved.data()).toMatchObject({ type: "RECHARGE", amount: 5, itemTitle: "" });
    expect(saved.get("date").toMillis()).toBeGreaterThan(0);
    expect(await db.runTransaction((tx) => hasTransaction(tx, db, uid, row.id))).toBe(true);
  });
});

describe("rental price hold and release", () => {
  const rows = (uid: string) => db.collection("wallets").doc(uid).collection("transactions");

  it("names the rows after the rental", () => {
    expect(rentalHeldRowId("r1")).toBe("rental_held_r1");
    expect(rentalReleasedRowId("r1")).toBe("rental_released_r1");
  });

  it("holds the price: available down, heldForRentals up, one RENTAL_HELD row", async () => {
    const uid = randomUUID();
    const wallet: Wallet = { available: 100, heldAsDeposits: 5, heldForRentals: 10 };

    await db.runTransaction(async (tx) => writeRentalHold(tx, db, uid, wallet, 30, "r1", "Tent"));

    expect((await db.collection("wallets").doc(uid).get()).data()).toEqual({
      available: 70,
      heldAsDeposits: 5,
      heldForRentals: 40,
    });
    expect((await rows(uid).doc("rental_held_r1").get()).data()).toMatchObject({
      type: "RENTAL_HELD",
      amount: -30,
      itemTitle: "Tent",
    });
  });

  it("releases the price: heldForRentals down, available up, one RENTAL_RELEASED row", async () => {
    const uid = randomUUID();
    const wallet: Wallet = { available: 70, heldAsDeposits: 5, heldForRentals: 40 };

    const released = await db.runTransaction(async (tx) =>
      writeRentalRelease(tx, db, uid, wallet, 30, "r1", "Tent"),
    );

    expect(released).toBe(30);
    expect((await db.collection("wallets").doc(uid).get()).data()).toEqual({
      available: 100,
      heldAsDeposits: 5,
      heldForRentals: 10,
    });
    expect((await rows(uid).doc("rental_released_r1").get()).data()).toMatchObject({
      type: "RENTAL_RELEASED",
      amount: 30,
      itemTitle: "Tent",
    });
  });

  it("never releases more than heldForRentals, and the row shows what was released", async () => {
    const uid = randomUUID();
    const wallet: Wallet = { available: 0, heldAsDeposits: 0, heldForRentals: 12 };

    const released = await db.runTransaction(async (tx) =>
      writeRentalRelease(tx, db, uid, wallet, 30, "r1", "Tent"),
    );

    expect(released).toBe(12);
    expect((await db.collection("wallets").doc(uid).get()).data()).toEqual({
      available: 12,
      heldAsDeposits: 0,
      heldForRentals: 0,
    });
    expect((await rows(uid).doc("rental_released_r1").get()).get("amount")).toBe(12);
  });

  it("writes nothing when nothing is held", async () => {
    const uid = randomUUID();
    const wallet: Wallet = { available: 50, heldAsDeposits: 0, heldForRentals: 0 };

    const released = await db.runTransaction(async (tx) =>
      writeRentalRelease(tx, db, uid, wallet, 30, "r1", "Tent"),
    );

    expect(released).toBe(0);
    expect((await db.collection("wallets").doc(uid).get()).exists).toBe(false);
    expect((await rows(uid).doc("rental_released_r1").get()).exists).toBe(false);
  });
});
