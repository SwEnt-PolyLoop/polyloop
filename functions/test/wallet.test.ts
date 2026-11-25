// Made with Claude.

import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { Firestore, getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import {
  DepositOutcome,
  depositToSettle,
  hasTransaction,
  readDepositSettlement,
  readWallet,
  writeDepositSettlement,
  writeWallet,
} from "../src/wallet";
import { fund, people, rows, wallet } from "../src/testing/walletFixtures";

// The logger's exports cannot be spied on one by one, so its two calls are replaced here.
jest.mock("firebase-functions/logger", () => ({ warn: jest.fn(), error: jest.fn() }));

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

describe("depositToSettle", () => {
  it("takes the late fees off the deposit and never goes below 0", () => {
    expect(depositToSettle({ deposit: 40 })).toBe(40);
    expect(depositToSettle({ deposit: 40, lateFees: 12 })).toBe(28);
    expect(depositToSettle({ deposit: 40, lateFees: 55 })).toBe(0);
  });

  it("reads a missing or invalid deposit or late fees as 0", () => {
    expect(depositToSettle({})).toBe(0);
    expect(depositToSettle({ deposit: "40", lateFees: 5 })).toBe(0);
    expect(depositToSettle({ deposit: 40, lateFees: -5 })).toBe(40);
    expect(depositToSettle({ deposit: 40, lateFees: 1.5 })).toBe(40);
  });
});

describe("deposit settlement", () => {
  const warn = logger.warn as jest.Mock;
  const error = logger.error as jest.Mock;

  beforeEach(() => jest.clearAllMocks());

  /**
   * Creates a borrower with 10 PP available and [held] PP held as deposit, a lender with 5 PP, and
   * the borrower's hold row unless [hold] is false.
   *
   * @param held the deposit held in the borrower's wallet.
   * @param hold whether the `deposit_held_<rentalId>` row exists.
   * @returns the two people, the rental id and the parties to settle between.
   */
  async function heldDeposit(held: number, hold = true) {
    const who = people();
    const rentalId = randomUUID();
    await fund(who.borrower, { available: 10, heldAsDeposits: held, heldForRentals: 0 });
    await fund(who.lender, { available: 5, heldAsDeposits: 0, heldForRentals: 0 });
    if (hold) {
      await db
        .collection("wallets")
        .doc(who.borrower)
        .collection("transactions")
        .doc(`deposit_held_${rentalId}`)
        .set({ type: "DEPOSIT_HELD", amount: -held, itemTitle: "Tent" });
    }
    const parties = { borrowerId: who.borrower, lenderId: who.lender };
    return { who, rentalId, parties };
  }

  /**
   * Reads then writes one settlement in its own transaction, as acceptSplit does.
   *
   * @param d what [heldDeposit] returned.
   * @param outcome a clean return, or a split.
   * @param rental the rental's data, with a deposit of 40 PP and no late fees by default.
   * @returns the shares settled, or a rejection with the refusal of the write step, if any.
   */
  function settle(
    d: Awaited<ReturnType<typeof heldDeposit>>,
    outcome: DepositOutcome,
    rental: object = { deposit: 40, listingTitle: "Tent" },
  ) {
    return db.runTransaction(async (tx) => {
      const state = await readDepositSettlement(tx, db, d.parties, d.rentalId);
      return writeDepositSettlement(tx, db, d.parties, d.rentalId, rental, state, outcome);
    });
  }

  it("releases the whole deposit to the borrower on a clean return", async () => {
    const d = await heldDeposit(40);

    const shares = await settle(d, { kind: "release" });
    expect(shares).toEqual({ lender: 0, borrower: 40 });
    expect(warn).not.toHaveBeenCalled();
    expect(error).not.toHaveBeenCalled();

    expect(await wallet(d.who.borrower)).toEqual({ available: 50, heldAsDeposits: 0, heldForRentals: 0 });
    expect(await wallet(d.who.lender)).toMatchObject({ available: 5 });
    expect(await rows(d.who.borrower)).toContainEqual(
      expect.objectContaining({
        id: `deposit_released_${d.rentalId}`,
        type: "DEPOSIT_RELEASED",
        amount: 40,
        itemTitle: "Tent",
      }),
    );
    expect(await rows(d.who.lender)).toEqual([]);
  });

  it("splits it with a row for each side, the borrower's share rounded down", async () => {
    const d = await heldDeposit(33);

    const shares = await settle(d, { kind: "split", lenderPercent: 50 }, { deposit: 33, listingTitle: "Tent" });
    expect(shares).toEqual({ lender: 17, borrower: 16 });

    expect(await wallet(d.who.borrower)).toMatchObject({ available: 26, heldAsDeposits: 0 });
    expect(await wallet(d.who.lender)).toMatchObject({ available: 22 });
    expect(await rows(d.who.lender)).toEqual([
      expect.objectContaining({ id: `deposit_split_${d.rentalId}`, type: "DEPOSIT_SPLIT", amount: 17 }),
    ]);
    expect(await rows(d.who.borrower)).toContainEqual(
      expect.objectContaining({ id: `deposit_split_${d.rentalId}`, type: "DEPOSIT_SPLIT", amount: 16 }),
    );
  });

  it("writes no row for a share of 0", async () => {
    const all = await heldDeposit(40);
    await settle(all, { kind: "split", lenderPercent: 100 });
    expect(await wallet(all.who.lender)).toMatchObject({ available: 45 });
    expect(await rows(all.who.borrower)).toHaveLength(1); // only the hold row
    expect(await wallet(all.who.borrower)).toMatchObject({ available: 10, heldAsDeposits: 0 });

    const none = await heldDeposit(40);
    await settle(none, { kind: "split", lenderPercent: 0 });
    expect(await rows(none.who.lender)).toEqual([]);
    expect(await wallet(none.who.borrower)).toMatchObject({ available: 50 });
  });

  it.each([
    [40, 0],
    [0, 40],
    [10, 30],
  ])(
    "changes nothing the second time, even when two run together, at lender %i / borrower %i",
    async (lender, borrower) => {
      const d = await heldDeposit(40);
      const split: DepositOutcome = { kind: "split", lenderPercent: (lender / 40) * 100 };

      await Promise.all([settle(d, split), settle(d, split)]);
      await settle(d, split);

      expect(await wallet(d.who.borrower)).toMatchObject({
        available: 10 + borrower,
        heldAsDeposits: 0,
      });
      expect(await wallet(d.who.lender)).toMatchObject({ available: 5 + lender });
      // A side that gets nothing has no row, so only the hold row is the borrower's without a share.
      expect(await rows(d.who.lender)).toHaveLength(lender > 0 ? 1 : 0);
      expect(await rows(d.who.borrower)).toHaveLength(borrower > 0 ? 2 : 1);
    },
  );

  it("moves nothing, with no error, when the deposit was already released", async () => {
    const d = await heldDeposit(40);
    // The row of a release by another function (#424, #501).
    await db
      .collection("wallets")
      .doc(d.who.borrower)
      .collection("transactions")
      .doc(`deposit_released_${d.rentalId}`)
      .set({ type: "DEPOSIT_RELEASED", amount: 40, itemTitle: "Tent" });

    const shares = await settle(d, { kind: "split", lenderPercent: 50 });

    expect(shares).toEqual({ lender: 0, borrower: 0 });
    expect(await wallet(d.who.borrower)).toMatchObject({ available: 10, heldAsDeposits: 40 });
    expect(await wallet(d.who.lender)).toMatchObject({ available: 5 });
    expect(await rows(d.who.lender)).toEqual([]);
    expect(warn).not.toHaveBeenCalled();
    expect(error).not.toHaveBeenCalled();
  });

  it("moves nothing when no deposit was held", async () => {
    const d = await heldDeposit(40, false);

    const shares = await settle(d, { kind: "split", lenderPercent: 25 });

    expect(shares).toEqual({ lender: 0, borrower: 0 });
    expect(warn).toHaveBeenCalledTimes(1);
    expect(warn).toHaveBeenCalledWith(expect.stringContaining("never held"), {
      rentalId: d.rentalId,
    });
    expect(error).not.toHaveBeenCalled();
    expect(await wallet(d.who.borrower)).toMatchObject({ available: 10, heldAsDeposits: 40 });
    expect(await rows(d.who.borrower)).toEqual([]);
    expect(await rows(d.who.lender)).toEqual([]);
  });

  it("moves nothing when there is nothing to settle", async () => {
    const d = await heldDeposit(40);

    const shares = await settle(d, { kind: "split", lenderPercent: 25 }, { deposit: 0 });

    expect(shares).toEqual({ lender: 0, borrower: 0 });

    expect(await wallet(d.who.borrower)).toMatchObject({ available: 10, heldAsDeposits: 40 });
  });

  it("refuses with internal and writes nothing when the held deposits are below the amount", async () => {
    const d = await heldDeposit(20);

    await expect(settle(d, { kind: "split", lenderPercent: 25 })).rejects.toMatchObject({ code: "internal" });

    expect(error).toHaveBeenCalledTimes(1);
    expect(error).toHaveBeenCalledWith(expect.stringContaining("do not cover"), {
      rentalId: d.rentalId,
      amount: 40,
      heldAsDeposits: 20,
    });
    expect(warn).not.toHaveBeenCalled();
    expect(await wallet(d.who.borrower)).toMatchObject({ available: 10, heldAsDeposits: 20 });
    expect(await wallet(d.who.lender)).toMatchObject({ available: 5 });
  });

  it.each([
    [{ deposit: 40 }, 50, { lender: 20, borrower: 20 }],
    [{ deposit: 40, lateFees: 12 }, 50, { lender: 14, borrower: 14 }],
    [{ deposit: 40, lateFees: 55 }, 50, { lender: 0, borrower: 0 }],
    [{ deposit: 33 }, 50, { lender: 17, borrower: 16 }],
  ])("moves the whole deposit left after late fees for %j at %i%%", async (rental, percent, expected) => {
    const left = depositToSettle(rental);
    const d = await heldDeposit(left > 0 ? left : 40);

    const shares = await settle(d, { kind: "split", lenderPercent: percent }, rental);

    expect(shares).toEqual(expected);
    expect(shares.lender + shares.borrower).toBe(left);
    // Nothing stays held for good: the borrower's held deposits went down by the whole amount.
    expect((await wallet(d.who.borrower))?.heldAsDeposits).toBe(left > 0 ? 0 : 40);
    expect((await wallet(d.who.lender))?.available).toBe(5 + shares.lender);
    expect((await wallet(d.who.borrower))?.available).toBe(10 + shares.borrower);
  });
});
