// Made with Claude.

import { randomUUID } from "node:crypto";
import { FieldValue, Firestore, getFirestore } from "firebase-admin/firestore";
import { payLender } from "./index";

// Firebase loads this ES module to verify tokens, which Jest cannot load; `run` skips that step.
jest.mock("jose", () => ({}));

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

/** The request of the signed-in, verified EPFL user [uid], sending [data]. */
function asUser(uid: string, data: object, email = `${uid}@epfl.ch`, verified = true) {
  return { auth: { uid, token: { email, email_verified: verified } }, data } as never;
}

/** A rental with its own people, so tests do not share wallets. */
function people() {
  const id = randomUUID();
  return { lender: `lender-${id}`, borrower: `borrower-${id}` };
}

async function rental(
  who: { lender: string; borrower: string },
  fields: object = {},
): Promise<string> {
  const id = randomUUID();
  await db
    .collection("rentals")
    .doc(id)
    .set({
      ownerId: who.lender,
      borrowerId: who.borrower,
      status: "ACTIVE",
      deposit: 40,
      totalPrice: 15,
      listingTitle: "Camping tent",
      ...fields,
    });
  return id;
}

async function fund(uid: string, wallet: object): Promise<void> {
  await db.collection("wallets").doc(uid).set(wallet);
}

async function wallet(uid: string) {
  return (await db.collection("wallets").doc(uid).get()).data();
}

async function rows(uid: string) {
  const snapshot = await db.collection("wallets").doc(uid).collection("transactions").get();
  return snapshot.docs.map((doc) => ({ id: doc.id, ...doc.data() }));
}

describe("paying the lender", () => {
  it("moves the price from the borrower to the lender and writes one row on each side", async () => {
    const who = people();
    const id = await rental(who);
    await fund(who.borrower, { available: 100, heldAsDeposits: 40, heldForRentals: 3 });
    await fund(who.lender, { available: 20, heldAsDeposits: 7 });

    const result = await payLender.run(asUser(who.borrower, { rentalId: id }));

    expect(result).toEqual({});
    expect(await wallet(who.borrower)).toEqual({
      available: 85,
      heldAsDeposits: 40,
      heldForRentals: 3,
    });
    expect(await wallet(who.lender)).toMatchObject({ available: 35, heldAsDeposits: 7 });
    expect(await rows(who.borrower)).toEqual([
      expect.objectContaining({
        id: `rental_payment_${id}`,
        type: "RENTAL_PAYMENT",
        amount: -15,
        itemTitle: "Camping tent",
      }),
    ]);
    expect(await rows(who.lender)).toEqual([
      expect.objectContaining({
        id: `rental_payment_${id}`,
        type: "RENTAL_PAYMENT",
        amount: 15,
        itemTitle: "Camping tent",
      }),
    ]);
    const saved = await db
      .collection("wallets")
      .doc(who.lender)
      .collection("transactions")
      .doc(`rental_payment_${id}`)
      .get();
    expect(saved.get("date").toMillis()).toBeGreaterThan(0);
  });

  it("pays when available is exactly the price", async () => {
    const who = people();
    const id = await rental(who);
    await fund(who.borrower, { available: 15 });
    await fund(who.lender, { available: 0 });

    await payLender.run(asUser(who.borrower, { rentalId: id }));

    expect(await wallet(who.borrower)).toMatchObject({ available: 0 });
    expect(await wallet(who.lender)).toMatchObject({ available: 15 });
  });

  it("creates the lender's wallet when it does not exist", async () => {
    const who = people();
    const id = await rental(who);
    await fund(who.borrower, { available: 100 });
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
    await fund(who.borrower, { available: 100 });

    await payLender.run(asUser(who.lender, { rentalId: id }));

    expect(await wallet(who.borrower)).toMatchObject({ available: 85 });
    expect(await wallet(who.lender)).toMatchObject({ available: 15 });
  });

  it("writes an empty itemTitle for a rental without listingTitle", async () => {
    const who = people();
    const id = await rental(who);
    await db.collection("rentals").doc(id).update({ listingTitle: FieldValue.delete() });
    await fund(who.borrower, { available: 100 });

    await payLender.run(asUser(who.borrower, { rentalId: id }));

    expect(await rows(who.borrower)).toEqual([expect.objectContaining({ amount: -15, itemTitle: "" })]);
    expect(await rows(who.lender)).toEqual([expect.objectContaining({ amount: 15, itemTitle: "" })]);
  });

  it("succeeds twice and pays once when the borrower and then the lender call", async () => {
    const who = people();
    const id = await rental(who);
    await fund(who.borrower, { available: 15 });

    await payLender.run(asUser(who.borrower, { rentalId: id }));
    await payLender.run(asUser(who.lender, { rentalId: id }));

    expect(await wallet(who.borrower)).toMatchObject({ available: 0 });
    expect(await wallet(who.lender)).toMatchObject({ available: 15 });
    expect(await rows(who.borrower)).toHaveLength(1);
    expect(await rows(who.lender)).toHaveLength(1);
  });

  it("succeeds again once the rental has moved on, paying nothing more", async () => {
    const who = people();
    const id = await rental(who);
    await fund(who.borrower, { available: 100 });
    await payLender.run(asUser(who.borrower, { rentalId: id }));

    for (const status of ["RETURNED", "CLOSED", "DISPUTED"]) {
      await db.collection("rentals").doc(id).update({ status });
      await payLender.run(asUser(who.borrower, { rentalId: id }));
    }

    expect(await wallet(who.borrower)).toMatchObject({ available: 85 });
    expect(await wallet(who.lender)).toMatchObject({ available: 15 });
    expect(await rows(who.borrower)).toHaveLength(1);
    expect(await rows(who.lender)).toHaveLength(1);
  });

  it("pays only once when two calls run at the same time", async () => {
    const who = people();
    const id = await rental(who);
    await fund(who.borrower, { available: 100 });

    await Promise.all([
      payLender.run(asUser(who.borrower, { rentalId: id })),
      payLender.run(asUser(who.lender, { rentalId: id })),
    ]);

    expect(await wallet(who.borrower)).toMatchObject({ available: 85 });
    expect(await wallet(who.lender)).toMatchObject({ available: 15 });
    expect(await rows(who.borrower)).toHaveLength(1);
    expect(await rows(who.lender)).toHaveLength(1);
  }, 15000);
});

describe("when it refuses", () => {
  it("fails with insufficient funds and writes nothing when available is too low", async () => {
    const who = people();
    const id = await rental(who);
    await fund(who.borrower, { available: 14 });

    await expect(payLender.run(asUser(who.borrower, { rentalId: id }))).rejects.toMatchObject({
      code: "failed-precondition",
      message: expect.stringContaining("Insufficient funds"),
    });

    expect(await wallet(who.borrower)).toEqual({ available: 14 });
    expect(await wallet(who.lender)).toBeUndefined();
    expect(await rows(who.borrower)).toEqual([]);
    expect(await rows(who.lender)).toEqual([]);
  });

  it("fails with insufficient funds and creates no wallet when the borrower has none", async () => {
    const who = people();
    const id = await rental(who);

    await expect(payLender.run(asUser(who.lender, { rentalId: id }))).rejects.toMatchObject({
      code: "failed-precondition",
      message: expect.stringContaining("Insufficient funds"),
    });

    expect(await wallet(who.borrower)).toBeUndefined();
    expect(await wallet(who.lender)).toBeUndefined();
  });

  it("refuses a rental that is not ACTIVE, writing nothing", async () => {
    const who = people();
    await fund(who.borrower, { available: 100 });

    for (const status of ["REQUESTED", "ACCEPTED", "RETURNED", "DECLINED"]) {
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
