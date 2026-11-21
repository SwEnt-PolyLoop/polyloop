// Made with Claude.

import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { Firestore, getFirestore } from "firebase-admin/firestore";
import {
  depositReleaseRowId,
  hasTransaction,
  readWallet,
  writeDepositRelease,
  writeWallet,
} from "../src/wallet";
import { RequestError } from "../src/rentalRules";

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

describe("writeDepositRelease", () => {
  it("gives the deposit back to available and adds one DEPOSIT_RELEASED row", async () => {
    const uid = randomUUID();
    const rentalId = randomUUID();
    await db.collection("wallets").doc(uid).set({ available: 20, heldAsDeposits: 90, heldForRentals: 4 });

    await db.runTransaction(async (tx) => {
      const wallet = await readWallet(tx, db, uid);
      writeDepositRelease(tx, db, uid, wallet, 60, rentalId, "Camera");
    });

    expect((await db.collection("wallets").doc(uid).get()).data()).toEqual({
      available: 80,
      heldAsDeposits: 30,
      heldForRentals: 4,
    });
    const rows = await db.collection("wallets").doc(uid).collection("transactions").get();
    expect(rows.docs.map((row) => row.id)).toEqual([depositReleaseRowId(rentalId)]);
    expect(rows.docs[0].data()).toMatchObject({ type: "DEPOSIT_RELEASED", amount: 60, itemTitle: "Camera" });
    expect(rows.docs[0].get("date").toMillis()).toBeGreaterThan(0);
  });

  it("refuses to release more than heldAsDeposits and writes nothing", async () => {
    const uid = randomUUID();
    const before = { available: 20, heldAsDeposits: 59, heldForRentals: 0 };
    await db.collection("wallets").doc(uid).set(before);

    const release = db.runTransaction(async (tx) => {
      const wallet = await readWallet(tx, db, uid);
      writeDepositRelease(tx, db, uid, wallet, 60, randomUUID(), "Camera");
    });

    await expect(release).rejects.toBeInstanceOf(RequestError);
    await expect(release).rejects.toMatchObject({ code: "internal" });
    expect((await db.collection("wallets").doc(uid).get()).data()).toEqual(before);
    expect((await db.collection("wallets").doc(uid).collection("transactions").get()).empty).toBe(true);
  });
});
