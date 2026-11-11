// Made with Claude.

import { randomUUID } from "node:crypto";
import { FieldValue, Firestore, getFirestore } from "firebase-admin/firestore";
import { reserveDeposit } from "./index";
import {
  People,
  asUser,
  fund,
  people,
  rental as newRental,
  rows,
  wallet,
} from "./testing/walletFixtures";

// Firebase loads this ES module to verify tokens, which Jest cannot load; `run` skips that step.
jest.mock("jose", () => ({}));

// reserveDeposit as index.ts exports it, called with what the app sends, on the Firestore emulator.
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

/** An ACCEPTED rental by default. */
function rental(who: People, fields: object = {}): Promise<string> {
  return newRental(who, "ACCEPTED", fields);
}

describe("holding the deposit", () => {
  it("moves the deposit from available to held and writes one DEPOSIT_HELD row", async () => {
    const who = people();
    const id = await rental(who);
    await fund(who.borrower, { available: 100, heldAsDeposits: 5, heldForRentals: 15 });

    const result = await reserveDeposit.run(asUser(who.borrower, { rentalId: id }));

    expect(result).toEqual({});
    expect(await wallet(who.borrower)).toEqual({
      available: 60,
      heldAsDeposits: 45,
      heldForRentals: 15,
    });
    const history = await rows(who.borrower);
    expect(history).toEqual([
      expect.objectContaining({
        id: `deposit_held_${id}`,
        type: "DEPOSIT_HELD",
        amount: -40,
        itemTitle: "Camping tent",
      }),
    ]);
    const saved = await db
      .collection("wallets")
      .doc(who.borrower)
      .collection("transactions")
      .doc(`deposit_held_${id}`)
      .get();
    expect(saved.get("date").toMillis()).toBeGreaterThan(0);
  });

  it("charges the borrower, not the lender, when the lender calls", async () => {
    const who = people();
    const id = await rental(who);
    await fund(who.borrower, { available: 100 });
    await fund(who.lender, { available: 100 });

    await reserveDeposit.run(asUser(who.lender, { rentalId: id }));

    expect(await wallet(who.borrower)).toMatchObject({ available: 60, heldAsDeposits: 40 });
    expect(await wallet(who.lender)).toEqual({ available: 100 });
    expect(await rows(who.lender)).toEqual([]);
  });

  it("holds the deposit of a rental without listingTitle, with an empty itemTitle", async () => {
    const who = people();
    const id = await rental(who);
    await db.collection("rentals").doc(id).update({ listingTitle: FieldValue.delete() });
    await fund(who.borrower, { available: 100 });

    await reserveDeposit.run(asUser(who.borrower, { rentalId: id }));

    expect(await wallet(who.borrower)).toMatchObject({ available: 60, heldAsDeposits: 40 });
    expect(await rows(who.borrower)).toEqual([
      expect.objectContaining({ id: `deposit_held_${id}`, amount: -40, itemTitle: "" }),
    ]);
  });

  it("succeeds twice and charges once when the borrower and then the lender call", async () => {
    const who = people();
    const id = await rental(who);
    await fund(who.borrower, { available: 40 });

    await reserveDeposit.run(asUser(who.borrower, { rentalId: id }));
    await reserveDeposit.run(asUser(who.lender, { rentalId: id }));

    expect(await wallet(who.borrower)).toMatchObject({ available: 0, heldAsDeposits: 40 });
    expect(await rows(who.borrower)).toHaveLength(1);
  });

  it("succeeds again once the rental is ACTIVE, charging nothing more", async () => {
    const who = people();
    const id = await rental(who);
    await fund(who.borrower, { available: 100 });
    await reserveDeposit.run(asUser(who.borrower, { rentalId: id }));
    await db.collection("rentals").doc(id).update({ status: "ACTIVE" });

    await reserveDeposit.run(asUser(who.borrower, { rentalId: id }));

    expect(await wallet(who.borrower)).toMatchObject({ available: 60, heldAsDeposits: 40 });
    expect(await rows(who.borrower)).toHaveLength(1);
  });

  it("charges only once when two calls run at the same time", async () => {
    const who = people();
    const id = await rental(who);
    await fund(who.borrower, { available: 100 });

    await Promise.all([
      reserveDeposit.run(asUser(who.borrower, { rentalId: id })),
      reserveDeposit.run(asUser(who.lender, { rentalId: id })),
    ]);

    expect(await wallet(who.borrower)).toMatchObject({ available: 60, heldAsDeposits: 40 });
    expect(await rows(who.borrower)).toHaveLength(1);
  });
});

describe("when it refuses", () => {
  it("fails with insufficient funds and writes nothing when available is too low", async () => {
    const who = people();
    const id = await rental(who);
    await fund(who.borrower, { available: 39 });

    await expect(reserveDeposit.run(asUser(who.borrower, { rentalId: id }))).rejects.toMatchObject({
      code: "failed-precondition",
      message: expect.stringContaining("Insufficient funds"),
    });

    expect(await wallet(who.borrower)).toEqual({ available: 39 });
    expect(await rows(who.borrower)).toEqual([]);
  });

  it("fails with insufficient funds and creates no wallet when there is none", async () => {
    const who = people();
    const id = await rental(who);

    await expect(reserveDeposit.run(asUser(who.borrower, { rentalId: id }))).rejects.toMatchObject({
      code: "failed-precondition",
      message: expect.stringContaining("Insufficient funds"),
    });

    expect(await wallet(who.borrower)).toBeUndefined();
    expect(await rows(who.borrower)).toEqual([]);
  });

  it("refuses a rental that is not ACCEPTED, writing nothing", async () => {
    const who = people();
    await fund(who.borrower, { available: 100 });

    for (const status of ["REQUESTED", "ACTIVE", "DECLINED"]) {
      const id = await rental(who, { status });
      await expect(
        reserveDeposit.run(asUser(who.borrower, { rentalId: id })),
      ).rejects.toMatchObject({ code: "failed-precondition" });
    }

    expect(await wallet(who.borrower)).toEqual({ available: 100 });
    expect(await rows(who.borrower)).toEqual([]);
  });

  it("refuses a caller who is neither the borrower nor the lender", async () => {
    const who = people();
    const id = await rental(who);
    await fund(who.borrower, { available: 100 });

    await expect(
      reserveDeposit.run(asUser(`stranger-${randomUUID()}`, { rentalId: id })),
    ).rejects.toMatchObject({ code: "permission-denied" });

    expect(await wallet(who.borrower)).toEqual({ available: 100 });
  });

  it("refuses a caller who is not signed in", async () => {
    const who = people();
    const id = await rental(who);

    await expect(reserveDeposit.run({ data: { rentalId: id } } as never)).rejects.toMatchObject({
      code: "unauthenticated",
    });
  });

  it("refuses a caller whose email is not verified or not an EPFL one", async () => {
    const who = people();
    const id = await rental(who);
    await fund(who.borrower, { available: 100 });

    await expect(
      reserveDeposit.run(asUser(who.borrower, { rentalId: id }, "borrower@epfl.ch", false)),
    ).rejects.toMatchObject({ code: "permission-denied" });
    await expect(
      reserveDeposit.run(asUser(who.borrower, { rentalId: id }, "borrower@gmail.com")),
    ).rejects.toMatchObject({ code: "permission-denied" });

    expect(await wallet(who.borrower)).toEqual({ available: 100 });
    expect(await rows(who.borrower)).toEqual([]);
  });

  it("refuses a rental that does not exist", async () => {
    await expect(
      reserveDeposit.run(asUser("anyone", { rentalId: randomUUID() })),
    ).rejects.toMatchObject({ code: "not-found" });
  });

  it("refuses a missing rentalId", async () => {
    await expect(reserveDeposit.run(asUser("anyone", {}))).rejects.toMatchObject({
      code: "invalid-argument",
    });
  });

  it("refuses a rental whose deposit is missing, zero or not a whole number", async () => {
    const who = people();
    await fund(who.borrower, { available: 100 });
    const missing = await rental(who);
    await db.collection("rentals").doc(missing).update({ deposit: FieldValue.delete() });
    await expect(
      reserveDeposit.run(asUser(who.borrower, { rentalId: missing })),
    ).rejects.toMatchObject({ code: "internal" });

    for (const deposit of [null, 0, 12.5, "40", 100_000]) {
      const id = await rental(who, { deposit });
      await expect(
        reserveDeposit.run(asUser(who.borrower, { rentalId: id })),
      ).rejects.toMatchObject({ code: "internal" });
    }

    expect(await wallet(who.borrower)).toEqual({ available: 100 });
    expect(await rows(who.borrower)).toEqual([]);
  });

  it("refuses a rental with no borrower, writing nothing", async () => {
    const who = people();
    await fund(who.borrower, { available: 100 });
    const missing = await rental(who);
    await db.collection("rentals").doc(missing).update({ borrowerId: FieldValue.delete() });
    await expect(
      reserveDeposit.run(asUser(who.lender, { rentalId: missing })),
    ).rejects.toMatchObject({ code: "internal" });

    const blank = await rental(who, { borrowerId: "" });
    await expect(
      reserveDeposit.run(asUser(who.lender, { rentalId: blank })),
    ).rejects.toMatchObject({ code: "internal" });

    expect(await wallet(who.borrower)).toEqual({ available: 100 });
    expect(await rows(who.borrower)).toEqual([]);
  });
});

describe("deployment", () => {
  it("is a callable in the app's region", () => {
    expect(reserveDeposit.__endpoint.region).toEqual(["europe-west1"]);
    expect(reserveDeposit.__endpoint.callableTrigger).toBeDefined();
  });
});
