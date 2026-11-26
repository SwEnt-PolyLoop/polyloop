// Made with Claude.

import { Firestore, getFirestore } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { requestRental } from "../src/index";
import { DATES_UNAVAILABLE, createRentalRequest } from "../src/rentalRequest";
import { INSUFFICIENT_FUNDS } from "../src/wallet";
import { NOW, input, listing, people, rentalsOf } from "../src/testing/requestFixtures";
import { asUser, rows, wallet } from "../src/testing/walletFixtures";

// Firebase loads this ES module to verify tokens, which Jest cannot load; `run` skips that step.
jest.mock("jose", () => ({}));

// The price held when a request is sent (#494), on the Firestore emulator. The request itself is
// tested in src/rentalRequest.test.ts. input() asks for 3 days at 12 PP, so 36 PP.
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

const PRICE = 36;

/** The row a request of [rentalId] adds to the borrower's history. */
const heldRow = (rentalId: string) =>
  expect.objectContaining({
    id: `rental_price_held_${rentalId}`,
    type: "RENTAL_PAYMENT",
    amount: -PRICE,
    itemTitle: "Camping tent",
  });

const refusedForFunds = expect.objectContaining({
  code: "failed-precondition",
  details: { reason: INSUFFICIENT_FUNDS },
});

describe("holding the price when a request is sent", () => {
  it("moves the price from available to heldForRentals, with one row", async () => {
    const { lender, borrower } = await people(100);
    await db.collection("wallets").doc(borrower).update({ heldAsDeposits: 5, heldForRentals: 3 });
    const listingId = await listing(lender);

    const id = await createRentalRequest(db, borrower, input(listingId), NOW);

    expect(await wallet(borrower)).toEqual({
      available: 64,
      heldAsDeposits: 5,
      heldForRentals: 3 + PRICE,
    });
    expect(await rows(borrower)).toEqual([heldRow(id)]);
  });

  it("holds a price the available balance covers exactly", async () => {
    const { lender, borrower } = await people(PRICE);
    const listingId = await listing(lender);

    await createRentalRequest(db, borrower, input(listingId), NOW);

    expect(await wallet(borrower)).toMatchObject({ available: 0, heldForRentals: PRICE });
  });

  it("refuses a balance one PP too low, writing neither the rental nor the wallet", async () => {
    const { lender, borrower } = await people(PRICE - 1);
    const listingId = await listing(lender);

    await expect(createRentalRequest(db, borrower, input(listingId), NOW)).rejects.toEqual(
      refusedForFunds,
    );

    expect(await rentalsOf(listingId)).toBe(0);
    expect(await wallet(borrower)).toEqual({ available: PRICE - 1 });
    expect(await rows(borrower)).toEqual([]);
  });

  it("refuses a borrower with no wallet, and creates none", async () => {
    const { lender, borrower } = await people();
    await db.collection("wallets").doc(borrower).delete();
    const listingId = await listing(lender);

    await expect(createRentalRequest(db, borrower, input(listingId), NOW)).rejects.toEqual(
      refusedForFunds,
    );

    expect(await wallet(borrower)).toBeUndefined();
  });

  it("does not count what is already held as available", async () => {
    const { lender, borrower } = await people(PRICE - 1);
    await db
      .collection("wallets")
      .doc(borrower)
      .update({ heldAsDeposits: 500, heldForRentals: 500 });
    const listingId = await listing(lender);

    await expect(createRentalRequest(db, borrower, input(listingId), NOW)).rejects.toEqual(
      refusedForFunds,
    );
  });

  it("checks the dates before the balance, so a booked day is refused as such", async () => {
    const { lender, borrower } = await people(0);
    const listingId = await listing(lender, {
      bookedRanges: [{ start: "2030-03-20", end: "2030-03-22" }],
    });

    await expect(createRentalRequest(db, borrower, input(listingId), NOW)).rejects.toMatchObject({
      details: { reason: DATES_UNAVAILABLE },
    });
  });

  it("holds the price once when the same request is sent again", async () => {
    const { lender, borrower } = await people(100);
    const listingId = await listing(lender);

    const first = await createRentalRequest(db, borrower, input(listingId), NOW);
    const again = await createRentalRequest(db, borrower, input(listingId), NOW);

    expect(again).toBe(first);
    expect(await wallet(borrower)).toMatchObject({ available: 100 - PRICE, heldForRentals: PRICE });
    expect(await rows(borrower)).toEqual([heldRow(first)]);
  });

  it("answers a request sent again with the first one even once the balance is spent", async () => {
    const { lender, borrower } = await people(PRICE);
    const listingId = await listing(lender);

    const first = await createRentalRequest(db, borrower, input(listingId), NOW);

    // available is 0 now: a retry after a lost answer must still get its rental, not a refusal.
    await expect(createRentalRequest(db, borrower, input(listingId), NOW)).resolves.toBe(first);
  });

  it("holds each price when one borrower sends two requests at once", async () => {
    const { lender, borrower } = await people(100);
    const tent = await listing(lender);
    const bike = await listing(lender);

    const ids = await Promise.all([
      createRentalRequest(db, borrower, input(tent), NOW),
      createRentalRequest(db, borrower, input(bike), NOW),
    ]);

    expect(await wallet(borrower)).toMatchObject({
      available: 100 - 2 * PRICE,
      heldForRentals: 2 * PRICE,
    });
    expect(await rows(borrower)).toEqual(expect.arrayContaining(ids.map(heldRow)));
  });

  it("refuses the second of two requests at once when the balance covers only one", async () => {
    const { lender, borrower } = await people(PRICE + 10);
    const tent = await listing(lender);
    const bike = await listing(lender);

    const results = await Promise.allSettled([
      createRentalRequest(db, borrower, input(tent), NOW),
      createRentalRequest(db, borrower, input(bike), NOW),
    ]);

    // Both read the same balance; Firestore retries the second on the new one, which is too low.
    expect(results.map((result) => result.status).sort()).toEqual(["fulfilled", "rejected"]);
    const refused = results.find((result) => result.status === "rejected");
    expect((refused as PromiseRejectedResult).reason).toEqual(refusedForFunds);
    expect(await wallet(borrower)).toMatchObject({ available: 10, heldForRentals: PRICE });
    expect((await rentalsOf(tent)) + (await rentalsOf(bike))).toBe(1);
  });
});

describe("the requestRental callable", () => {
  // Far enough in the future to stay valid, whatever day the tests run.
  const FUTURE = { availableFrom: "2099-01-01", availableTo: "2099-12-31" };

  it("gives the app an HttpsError with the INSUFFICIENT_FUNDS reason", async () => {
    const { lender, borrower } = await people(0);
    const listingId = await listing(lender, FUTURE);

    const refused = requestRental.run(
      asUser(borrower, { listingId, startDate: "2099-03-01", endDate: "2099-03-02" }),
    );

    await expect(refused).rejects.toBeInstanceOf(HttpsError);
    await expect(refused).rejects.toMatchObject({
      code: "failed-precondition",
      details: { reason: INSUFFICIENT_FUNDS },
    });
  });
});
