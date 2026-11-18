// Made with Claude.

import { randomUUID } from "node:crypto";
import { FieldValue, Firestore, getFirestore } from "firebase-admin/firestore";
import { payLender } from "./index";
import {
  People,
  asUser,
  fund,
  people,
  rental as newRental,
  rows,
  wallet,
} from "./testing/walletFixtures";
import { PAID_STATUSES } from "./payLender";

// Firebase loads this ES module to verify tokens, which Jest cannot load; `run` skips that step.
jest.mock("jose", () => ({}));

// The rental statuses (docs/ARCHITECTURE.md, "Rental statuses").
const ALL_STATUSES = [
  "REQUESTED",
  "ACCEPTED",
  "ACTIVE",
  "RETURNED",
  "CLOSED",
  "DECLINED",
  "CANCELLED",
  "OVERDUE",
  "LOST",
  "DISPUTED",
  "FROZEN",
];
const UNPAID_STATUSES = ["REQUESTED", "ACCEPTED", "DECLINED", "CANCELLED"];

// payLender as index.ts exports it, called with what the app sends, on the Firestore emulator.
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

/** An ACTIVE rental by default. */
function rental(who: People, fields: object = {}): Promise<string> {
  return newRental(who, "ACTIVE", fields);
}

/** The borrower's wallet once the request has held the 15 PP price, with [fields] on top. */
const held = (fields: object = {}) => ({ available: 100, heldForRentals: 15, ...fields });

/** The lender's payment row of the rental [id], with [title]. */
const paymentRow = (id: string, title = "Camping tent") =>
  expect.objectContaining({
    id: `rental_payment_${id}`,
    type: "RENTAL_PAYMENT",
    amount: 15,
    itemTitle: title,
  });

describe("paying the lender", () => {
  it("moves the held price to the lender, with one row in the lender's history", async () => {
    const who = people();
    const id = await rental(who);
    await fund(who.borrower, { available: 100, heldAsDeposits: 40, heldForRentals: 18 });
    await fund(who.lender, { available: 20, heldAsDeposits: 7 });

    const result = await payLender.run(asUser(who.borrower, { rentalId: id }));

    expect(result).toEqual({});
    // available does not change: it went down when the price was held, at request time.
    expect(await wallet(who.borrower)).toEqual({
      available: 100,
      heldAsDeposits: 40,
      heldForRentals: 3,
    });
    expect(await wallet(who.lender)).toMatchObject({ available: 35, heldAsDeposits: 7 });
    expect(await rows(who.borrower)).toEqual([]);
    expect(await rows(who.lender)).toEqual([paymentRow(id)]);
    const saved = await db
      .collection("wallets")
      .doc(who.lender)
      .collection("transactions")
      .doc(`rental_payment_${id}`)
      .get();
    expect(saved.get("date").toMillis()).toBeGreaterThan(0);
  });

  it("pays when exactly the price is held", async () => {
    const who = people();
    const id = await rental(who);
    await fund(who.borrower, { heldForRentals: 15 });
    await fund(who.lender, { available: 0 });

    await payLender.run(asUser(who.borrower, { rentalId: id }));

    expect(await wallet(who.borrower)).toMatchObject({ heldForRentals: 0 });
    expect(await wallet(who.lender)).toMatchObject({ available: 15 });
  });

  it("creates the lender's wallet when it does not exist", async () => {
    const who = people();
    const id = await rental(who);
    await fund(who.borrower, held());
    expect(await wallet(who.lender)).toBeUndefined();

    await payLender.run(asUser(who.borrower, { rentalId: id }));

    expect(await wallet(who.lender)).toEqual({
      available: 15,
      heldAsDeposits: 0,
      heldForRentals: 0,
    });
    expect(await rows(who.lender)).toHaveLength(1);
  });

  it("pays the same way when the lender calls", async () => {
    const who = people();
    const id = await rental(who);
    await fund(who.borrower, held());

    await payLender.run(asUser(who.lender, { rentalId: id }));

    expect(await wallet(who.borrower)).toMatchObject({ available: 100, heldForRentals: 0 });
    expect(await wallet(who.lender)).toMatchObject({ available: 15 });
  });

  it("writes an empty itemTitle for a rental without listingTitle", async () => {
    const who = people();
    const id = await rental(who);
    await db.collection("rentals").doc(id).update({ listingTitle: FieldValue.delete() });
    await fund(who.borrower, held());

    await payLender.run(asUser(who.borrower, { rentalId: id }));

    expect(await rows(who.lender)).toEqual([paymentRow(id, "")]);
  });

  it.each(PAID_STATUSES)("pays a rental that is %s", async (status) => {
    const who = people();
    const id = await rental(who, { status });
    await fund(who.borrower, held());

    await payLender.run(asUser(who.borrower, { rentalId: id }));

    expect(await wallet(who.borrower)).toMatchObject({ available: 100, heldForRentals: 0 });
    expect(await wallet(who.lender)).toMatchObject({ available: 15 });
    expect(await rows(who.borrower)).toEqual([]);
    expect(await rows(who.lender)).toHaveLength(1);
  });

  it("knows every rental status as paid or not paid, so a new status is noticed", () => {
    expect([...PAID_STATUSES, ...UNPAID_STATUSES].sort()).toEqual([...ALL_STATUSES].sort());
  });

  it("succeeds twice and pays once when the borrower and then the lender call", async () => {
    const who = people();
    const id = await rental(who);
    await fund(who.borrower, held());

    await payLender.run(asUser(who.borrower, { rentalId: id }));
    await payLender.run(asUser(who.lender, { rentalId: id }));

    expect(await wallet(who.borrower)).toMatchObject({ heldForRentals: 0 });
    expect(await wallet(who.lender)).toMatchObject({ available: 15 });
    expect(await rows(who.lender)).toHaveLength(1);
  });

  it("succeeds again once the rental has moved on, paying nothing more", async () => {
    const who = people();
    const id = await rental(who);
    await fund(who.borrower, held({ heldForRentals: 30 }));
    await payLender.run(asUser(who.borrower, { rentalId: id }));

    for (const status of ["RETURNED", "CLOSED", "DISPUTED"]) {
      await db.collection("rentals").doc(id).update({ status });
      await payLender.run(asUser(who.borrower, { rentalId: id }));
    }

    // 30 held: a second payment would have found enough to take another 15.
    expect(await wallet(who.borrower)).toMatchObject({ heldForRentals: 15 });
    expect(await wallet(who.lender)).toMatchObject({ available: 15 });
    expect(await rows(who.lender)).toHaveLength(1);
  });

  it("counts a payment made before the price was held, whose rows are on both sides", async () => {
    const who = people();
    const id = await rental(who);
    await fund(who.borrower, held());
    await db
      .collection("wallets")
      .doc(who.lender)
      .collection("transactions")
      .doc(`rental_payment_${id}`)
      .set({ type: "RENTAL_PAYMENT", amount: 15, itemTitle: "Camping tent" });

    await payLender.run(asUser(who.borrower, { rentalId: id }));

    expect(await wallet(who.borrower)).toEqual(held());
  });

  it("pays only once when two calls run at the same time", async () => {
    const who = people();
    const id = await rental(who);
    await fund(who.borrower, held({ heldForRentals: 30 }));

    await Promise.all([
      payLender.run(asUser(who.borrower, { rentalId: id })),
      payLender.run(asUser(who.lender, { rentalId: id })),
    ]);

    expect(await wallet(who.borrower)).toMatchObject({ heldForRentals: 15 });
    expect(await wallet(who.lender)).toMatchObject({ available: 15 });
    expect(await rows(who.lender)).toHaveLength(1);
  }, 15000);
});

describe("until the price is held at request (#357)", () => {
  it("takes a price that is not held from available, with a row for each", async () => {
    const who = people();
    const id = await rental(who);
    await fund(who.borrower, { available: 100, heldForRentals: 14 });

    await payLender.run(asUser(who.lender, { rentalId: id }));

    // The 14 PP held for another rental stay held.
    expect(await wallet(who.borrower)).toMatchObject({ available: 85, heldForRentals: 14 });
    expect(await wallet(who.lender)).toMatchObject({ available: 15 });
    expect(await rows(who.borrower)).toEqual([
      expect.objectContaining({ id: `rental_payment_${id}`, type: "RENTAL_PAYMENT", amount: -15 }),
    ]);
    expect(await rows(who.lender)).toEqual([paymentRow(id)]);
  });

  it("takes it only once when called twice", async () => {
    const who = people();
    const id = await rental(who);
    await fund(who.borrower, { available: 100 });

    await payLender.run(asUser(who.borrower, { rentalId: id }));
    await payLender.run(asUser(who.lender, { rentalId: id }));

    expect(await wallet(who.borrower)).toMatchObject({ available: 85 });
    expect(await wallet(who.lender)).toMatchObject({ available: 15 });
    expect(await rows(who.borrower)).toHaveLength(1);
  });
});

describe("when it refuses", () => {
  it("refuses when neither the held price nor available covers it, writing nothing", async () => {
    const who = people();
    const id = await rental(who);
    await fund(who.borrower, { available: 14, heldForRentals: 14 });

    await expect(payLender.run(asUser(who.borrower, { rentalId: id }))).rejects.toMatchObject({
      code: "failed-precondition",
      message: expect.stringContaining("Insufficient funds"),
    });

    expect(await wallet(who.borrower)).toEqual({ available: 14, heldForRentals: 14 });
    expect(await wallet(who.lender)).toBeUndefined();
    expect(await rows(who.borrower)).toEqual([]);
    expect(await rows(who.lender)).toEqual([]);
  });

  it("refuses and creates no wallet when the borrower has none", async () => {
    const who = people();
    const id = await rental(who);

    await expect(payLender.run(asUser(who.lender, { rentalId: id }))).rejects.toMatchObject({
      code: "failed-precondition",
      message: expect.stringContaining("Insufficient funds"),
    });

    expect(await wallet(who.borrower)).toBeUndefined();
    expect(await wallet(who.lender)).toBeUndefined();
  });

  it("refuses a rental that has not been picked up, writing nothing", async () => {
    const who = people();
    await fund(who.borrower, { available: 100 });

    for (const status of UNPAID_STATUSES) {
      const id = await rental(who, { status });
      await expect(payLender.run(asUser(who.borrower, { rentalId: id }))).rejects.toMatchObject({
        code: "failed-precondition",
      });
    }

    expect(await wallet(who.borrower)).toEqual({ available: 100 });
    expect(await wallet(who.lender)).toBeUndefined();
    expect(await rows(who.borrower)).toEqual([]);
  });

  it("refuses a caller who is neither the borrower nor the lender", async () => {
    const who = people();
    const id = await rental(who);
    await fund(who.borrower, { available: 100 });

    await expect(
      payLender.run(asUser(`stranger-${randomUUID()}`, { rentalId: id })),
    ).rejects.toMatchObject({ code: "permission-denied" });

    expect(await wallet(who.borrower)).toEqual({ available: 100 });
    expect(await wallet(who.lender)).toBeUndefined();
  });

  it("refuses a caller who is not signed in", async () => {
    const who = people();
    const id = await rental(who);

    await expect(payLender.run({ data: { rentalId: id } } as never)).rejects.toMatchObject({
      code: "unauthenticated",
    });
  });

  it("refuses a caller whose email is not verified or not an EPFL one", async () => {
    const who = people();
    const id = await rental(who);
    await fund(who.borrower, { available: 100 });

    await expect(
      payLender.run(asUser(who.borrower, { rentalId: id }, "borrower@epfl.ch", false)),
    ).rejects.toMatchObject({ code: "permission-denied" });
    await expect(
      payLender.run(asUser(who.borrower, { rentalId: id }, "borrower@gmail.com")),
    ).rejects.toMatchObject({ code: "permission-denied" });

    expect(await wallet(who.borrower)).toEqual({ available: 100 });
    expect(await wallet(who.lender)).toBeUndefined();
    expect(await rows(who.borrower)).toEqual([]);
  });

  it("refuses a rental that does not exist", async () => {
    await expect(
      payLender.run(asUser("anyone", { rentalId: randomUUID() })),
    ).rejects.toMatchObject({ code: "not-found" });
  });

  it("refuses a missing rentalId", async () => {
    await expect(payLender.run(asUser("anyone", {}))).rejects.toMatchObject({
      code: "invalid-argument",
    });
  });

  it("refuses a rental whose price is missing, zero, negative or not a whole number", async () => {
    const who = people();
    await fund(who.borrower, { available: 100 });
    const missing = await rental(who);
    await db.collection("rentals").doc(missing).update({ totalPrice: FieldValue.delete() });
    await expect(payLender.run(asUser(who.borrower, { rentalId: missing }))).rejects.toMatchObject({
      code: "internal",
    });

    for (const totalPrice of [null, 0, -5, 12.5, "15", Number.MAX_VALUE]) {
      const id = await rental(who, { totalPrice });
      await expect(payLender.run(asUser(who.borrower, { rentalId: id }))).rejects.toMatchObject({
        code: "internal",
      });
    }

    expect(await wallet(who.borrower)).toEqual({ available: 100 });
    expect(await wallet(who.lender)).toBeUndefined();
    expect(await rows(who.borrower)).toEqual([]);
  });

  it("refuses a rental with no borrower, writing nothing", async () => {
    const who = people();
    await fund(who.borrower, { available: 100 });
    const missing = await rental(who);
    await db.collection("rentals").doc(missing).update({ borrowerId: FieldValue.delete() });
    await expect(payLender.run(asUser(who.lender, { rentalId: missing }))).rejects.toMatchObject({
      code: "internal",
    });

    const blank = await rental(who, { borrowerId: "" });
    await expect(payLender.run(asUser(who.lender, { rentalId: blank }))).rejects.toMatchObject({
      code: "internal",
    });

    expect(await wallet(who.borrower)).toEqual({ available: 100 });
    expect(await rows(who.borrower)).toEqual([]);
  });

  it("refuses a rental with no lender, writing nothing", async () => {
    const who = people();
    await fund(who.borrower, { available: 100 });
    const missing = await rental(who);
    await db.collection("rentals").doc(missing).update({ ownerId: FieldValue.delete() });
    await expect(payLender.run(asUser(who.borrower, { rentalId: missing }))).rejects.toMatchObject({
      code: "internal",
    });

    const blank = await rental(who, { ownerId: "" });
    await expect(payLender.run(asUser(who.borrower, { rentalId: blank }))).rejects.toMatchObject({
      code: "internal",
    });

    expect(await wallet(who.borrower)).toEqual({ available: 100 });
    expect(await rows(who.borrower)).toEqual([]);
  });

  it("refuses a rental whose borrower and lender are the same user, writing nothing", async () => {
    const who = people();
    await fund(who.borrower, { available: 100 });
    const id = await rental(who, { ownerId: who.borrower });

    await expect(payLender.run(asUser(who.borrower, { rentalId: id }))).rejects.toMatchObject({
      code: "internal",
    });

    expect(await wallet(who.borrower)).toEqual({ available: 100 });
    expect(await rows(who.borrower)).toEqual([]);
  });
});

describe("deployment", () => {
  it("is a callable in the app's region", () => {
    expect(payLender.__endpoint.region).toEqual(["europe-west1"]);
    expect(payLender.__endpoint.callableTrigger).toBeDefined();
  });
});
