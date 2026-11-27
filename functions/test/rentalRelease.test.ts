// Made with Claude.

import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { Firestore, getFirestore } from "firebase-admin/firestore";
import { cancelInTransaction } from "../src/cancel";
import {
  declineRequest,
  expireOffer,
  expireRequest,
  makeOffer,
  respondToOffer,
} from "../src/negotiation";
import {
  People,
  fund,
  heldRental,
  people,
  rental,
  rows,
  wallet,
} from "../src/testing/walletFixtures";
import { Wallet, readPriceRelease, releasePrice } from "../src/wallet";

// cancel.ts loads handover.ts, which loads firebase-functions, which loads an ES module that Jest
// cannot load.
jest.mock("jose", () => ({}));

// The release runs inside the declines' and the cancel's transactions, so it is tested on the
// Firestore emulator.
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

const PRICE = 36;
const NOW = new Date("2026-11-04T10:00:00Z");
const ONE_WEEK_LATER = new Date("2026-11-11T10:00:00Z");
// 11:00 in Zurich on 1 December, the first day of the default request.
const FIRST_DAY = new Date("2026-12-01T10:00:00Z");
const ADDRESSES = {
  pickupAddress: "Route Cantonale 1, Ecublens",
  returnAddress: "Rue de Lausanne 10, Renens",
};

/** A listing of [lender] with no booking; returns its id. */
async function listing(lender: string): Promise<string> {
  const id = randomUUID();
  await db.collection("listings").doc(id).set({ ownerId: lender, bookedRanges: [] });
  return id;
}

/** A REQUESTED rental of [listingId] whose 36 PP price was held (its row); no wallet. */
async function heldRequest(
  who: People,
  listingId: string,
  startDate = "2026-12-01",
  endDate = "2026-12-03",
): Promise<string> {
  return heldRental(who, "REQUESTED", {
    listingId,
    startDate,
    endDate,
    totalPrice: PRICE,
    pickupAddress: "",
    returnAddress: "",
  });
}

/** A wallet with [available] PP, [heldForRentals] PP held for rentals and no deposit. */
const funds = (available: number, heldForRentals = PRICE): Wallet => ({
  available,
  heldAsDeposits: 0,
  heldForRentals,
});

const status = async (id: string) => (await db.doc(`rentals/${id}`).get()).get("status");

/** The borrower's `rental_price_released_<id>` row, or undefined. */
async function releasedRow(borrower: string, id: string) {
  return (await rows(borrower)).find((row) => row.id === `rental_price_released_${id}`);
}

/** Checks that [id]'s price went back to [borrower], whose wallet is now [expected]. */
async function expectReleased(borrower: string, id: string, expected: Wallet): Promise<void> {
  expect(await wallet(borrower)).toEqual(expected);
  expect(await releasedRow(borrower, id)).toMatchObject({
    type: "RENTAL_PAYMENT",
    amount: PRICE,
    itemTitle: "Camping tent",
  });
}

describe("the release steps", () => {
  const read = (id: string) =>
    db.runTransaction(async (tx) => {
      const data = (await tx.get(db.doc(`rentals/${id}`))).data() ?? {};
      return readPriceRelease(tx, db, id, data);
    });

  it("gives back what the hold took, not the rental's stored price", async () => {
    const who = people();
    const id = await heldRequest(who, "listing");
    await db.doc(`rentals/${id}`).update({ totalPrice: 50 });

    expect(await read(id)).toEqual({
      borrowerId: who.borrower,
      rentalId: id,
      amount: PRICE,
      itemTitle: "Camping tent",
    });
  });

  it("gives nothing back without a held row, once given back, or with no borrower", async () => {
    const who = people();
    const neverHeld = await rental(who, "REQUESTED");
    const givenBack = await heldRequest(who, "listing");
    await db
      .doc(`wallets/${who.borrower}/transactions/rental_price_released_${givenBack}`)
      .set({ type: "RENTAL_PAYMENT", amount: PRICE, itemTitle: "Camping tent" });
    const noBorrower = await heldRequest(who, "listing");
    await db.doc(`rentals/${noBorrower}`).update({ borrowerId: "" });

    expect(await read(neverHeld)).toBeNull();
    expect(await read(givenBack)).toBeNull();
    expect(await read(noBorrower)).toBeNull();
  });

  it("refuses a held row whose amount is broken", async () => {
    const who = people();
    const id = await heldRequest(who, "listing");
    await db
      .doc(`wallets/${who.borrower}/transactions/rental_price_held_${id}`)
      .update({ amount: 12 });

    await expect(read(id)).rejects.toMatchObject({ code: "internal" });
  });

  it("moves the amount from heldForRentals to available", () => {
    const release = { borrowerId: "b", rentalId: "r", amount: PRICE, itemTitle: "" };

    expect(releasePrice({ available: 4, heldAsDeposits: 7, heldForRentals: 40 }, release)).toEqual({
      available: 40,
      heldAsDeposits: 7,
      heldForRentals: 4,
    });
  });

  it("refuses when heldForRentals does not cover the amount", () => {
    const release = { borrowerId: "b", rentalId: "r", amount: PRICE, itemTitle: "" };

    expect(() => releasePrice(funds(100, PRICE - 1), release)).toThrow(
      expect.objectContaining({ code: "internal" }),
    );
  });
});

describe("each decline gives the held price back", () => {
  it("the borrower declining the lender's offer", async () => {
    const who = people();
    const id = await heldRequest(who, await listing(who.lender));
    await fund(who.borrower, funds(64));
    await makeOffer(db, who.lender, { rentalId: id, deposit: 50, ...ADDRESSES }, NOW);

    await respondToOffer(db, who.borrower, { rentalId: id, action: "DECLINE" }, NOW);

    expect(await status(id)).toBe("DECLINED");
    await expectReleased(who.borrower, id, funds(100, 0));
  });

  it("the lender declining a counteroffer", async () => {
    const who = people();
    const id = await heldRequest(who, await listing(who.lender));
    await fund(who.borrower, funds(64));
    await makeOffer(db, who.lender, { rentalId: id, deposit: 50, ...ADDRESSES }, NOW);
    await respondToOffer(db, who.borrower, { rentalId: id, action: "COUNTER", deposit: 40 }, NOW);

    await respondToOffer(db, who.lender, { rentalId: id, action: "DECLINE" }, NOW);

    expect(await status(id)).toBe("DECLINED");
    await expectReleased(who.borrower, id, funds(100, 0));
  });

  it("the lender declining a request (declineRequest)", async () => {
    const who = people();
    const id = await heldRequest(who, await listing(who.lender));
    await fund(who.borrower, funds(64));

    await declineRequest(db, who.lender, id);

    expect(await status(id)).toBe("DECLINED");
    await expectReleased(who.borrower, id, funds(100, 0));
  });

  it("an offer expiring (expireOffers)", async () => {
    const who = people();
    const id = await heldRequest(who, await listing(who.lender));
    await fund(who.borrower, funds(64));
    await makeOffer(db, who.lender, { rentalId: id, deposit: 50, ...ADDRESSES }, NOW);

    expect(await expireOffer(db.doc(`rentals/${id}`), ONE_WEEK_LATER)).toBe(true);

    expect(await status(id)).toBe("DECLINED");
    await expectReleased(who.borrower, id, funds(100, 0));
  });

  it("a request with no offer reaching its first day (expireOffers)", async () => {
    const who = people();
    const id = await heldRequest(who, await listing(who.lender));
    await fund(who.borrower, funds(64));

    expect(await expireRequest(db.doc(`rentals/${id}`), FIRST_DAY)).toBe(true);

    expect(await status(id)).toBe("DECLINED");
    await expectReleased(who.borrower, id, funds(100, 0));
  });

  it("another request being accepted, for every overlapping one", async () => {
    const accepting = people();
    const other = people();
    const listingId = await listing(accepting.lender);
    const id = await heldRequest(accepting, listingId);
    // The same other borrower twice, just before and just after (inside the buffer days).
    const before = await heldRequest(
      { lender: accepting.lender, borrower: other.borrower },
      listingId,
      "2026-11-27",
      "2026-11-30",
    );
    const after = await heldRequest(
      { lender: accepting.lender, borrower: other.borrower },
      listingId,
      "2026-12-04",
      "2026-12-06",
    );
    await fund(accepting.borrower, funds(100));
    await fund(other.borrower, funds(10, 2 * PRICE));
    await makeOffer(db, accepting.lender, { rentalId: id, deposit: 50, ...ADDRESSES }, NOW);

    await respondToOffer(db, accepting.borrower, { rentalId: id, action: "ACCEPT" }, NOW);

    expect(await status(before)).toBe("DECLINED");
    expect(await status(after)).toBe("DECLINED");
    expect(await wallet(other.borrower)).toEqual(funds(10 + 2 * PRICE, 0));
    expect(await releasedRow(other.borrower, before)).toMatchObject({ amount: PRICE });
    expect(await releasedRow(other.borrower, after)).toMatchObject({ amount: PRICE });
    // The accepted rental keeps its own price held until the pickup, and gets its deposit held.
    expect(await wallet(accepting.borrower)).toEqual({
      available: 50,
      heldAsDeposits: 50,
      heldForRentals: PRICE,
    });
    expect(await releasedRow(accepting.borrower, id)).toBeUndefined();
  });

  it("the accepting borrower's own overlapping request, before the deposit check", async () => {
    const who = people();
    const listingId = await listing(who.lender);
    const id = await heldRequest(who, listingId);
    const own = await heldRequest(who, listingId, "2026-11-27", "2026-11-30");
    // 20 PP available cannot cover the 50 PP deposit until the other request's 36 PP come back.
    await fund(who.borrower, funds(20, 2 * PRICE));
    await makeOffer(db, who.lender, { rentalId: id, deposit: 50, ...ADDRESSES }, NOW);

    await respondToOffer(db, who.borrower, { rentalId: id, action: "ACCEPT" }, NOW);

    expect(await status(own)).toBe("DECLINED");
    expect(await wallet(who.borrower)).toEqual({
      available: 20 + PRICE - 50,
      heldAsDeposits: 50,
      heldForRentals: PRICE,
    });
    expect(await releasedRow(who.borrower, own)).toMatchObject({ amount: PRICE });
    expect((await rows(who.borrower)).find((r) => r.id === `deposit_held_${id}`)).toMatchObject({
      amount: -50,
    });
  });
});

describe("cancelling gives the held price back", () => {
  // 11:00 in Zurich, the day before the first day of the accepted rental below.
  const DAY_BEFORE = new Date("2026-12-09T10:00:00Z");

  it("for a request", async () => {
    const who = people();
    const id = await heldRequest(who, await listing(who.lender));
    await fund(who.borrower, funds(64));

    await cancelInTransaction(db, who.borrower, id, NOW);

    expect(await status(id)).toBe("CANCELLED");
    await expectReleased(who.borrower, id, funds(100, 0));
  });

  it("for an accepted rental, together with the deposit", async () => {
    const who = people();
    const listingId = await listing(who.lender);
    await db.doc(`listings/${listingId}`).update({
      bookedRanges: [{ start: "2026-12-10", end: "2026-12-12" }],
    });
    const id = await heldRental(who, "ACCEPTED", {
      listingId,
      startDate: "2026-12-10",
      endDate: "2026-12-12",
      totalPrice: PRICE,
      deposit: 40,
    });
    await db
      .doc(`wallets/${who.borrower}/transactions/deposit_held_${id}`)
      .set({ type: "DEPOSIT_HELD", amount: -40, itemTitle: "Camping tent" });
    await fund(who.borrower, { available: 24, heldAsDeposits: 40, heldForRentals: PRICE });

    await cancelInTransaction(db, who.lender, id, DAY_BEFORE);

    expect(await status(id)).toBe("CANCELLED");
    await expectReleased(who.borrower, id, funds(100, 0));
    expect(
      (await rows(who.borrower)).find((r) => r.id === `deposit_released_${id}`),
    ).toMatchObject({ amount: 40 });
  });
});

describe("the price goes back once, and only when it was held", () => {
  it("releases nothing more for a rental whose price was already given back", async () => {
    const who = people();
    const id = await heldRequest(who, await listing(who.lender));
    await db
      .doc(`wallets/${who.borrower}/transactions/rental_price_released_${id}`)
      .set({ type: "RENTAL_PAYMENT", amount: PRICE, itemTitle: "Camping tent" });
    await fund(who.borrower, funds(100, 0));

    await declineRequest(db, who.lender, id);

    expect(await status(id)).toBe("DECLINED");
    expect(await wallet(who.borrower)).toEqual(funds(100, 0));
  });

  it("releases once when the expiry runs twice", async () => {
    const who = people();
    const id = await heldRequest(who, await listing(who.lender));
    await fund(who.borrower, funds(64));
    const ref = db.doc(`rentals/${id}`);

    expect(await expireRequest(ref, FIRST_DAY)).toBe(true);
    expect(await expireRequest(ref, FIRST_DAY)).toBe(false);

    await expectReleased(who.borrower, id, funds(100, 0));
  });

  it("releases nothing for a request sent before prices were held", async () => {
    const who = people();
    const listingId = await listing(who.lender);
    const id = await rental(who, "REQUESTED", { listingId, startDate: "2026-12-01" });
    await fund(who.borrower, funds(64, 0));

    await declineRequest(db, who.lender, id);

    expect(await status(id)).toBe("DECLINED");
    expect(await wallet(who.borrower)).toEqual(funds(64, 0));
    expect(await rows(who.borrower)).toEqual([]);
  });

  it("refuses a decline heldForRentals does not cover, writing nothing", async () => {
    const who = people();
    const id = await heldRequest(who, await listing(who.lender));
    await fund(who.borrower, funds(64, PRICE - 1));

    await expect(declineRequest(db, who.lender, id)).rejects.toMatchObject({ code: "internal" });

    expect(await status(id)).toBe("REQUESTED");
    expect(await wallet(who.borrower)).toEqual(funds(64, PRICE - 1));
    expect(await releasedRow(who.borrower, id)).toBeUndefined();
  });

  it("refuses an acceptance whose overlapping price cannot go back, writing nothing", async () => {
    const accepting = people();
    const other = people();
    const listingId = await listing(accepting.lender);
    const id = await heldRequest(accepting, listingId);
    const overlapping = await heldRequest(
      { lender: accepting.lender, borrower: other.borrower },
      listingId,
    );
    await fund(accepting.borrower, funds(100));
    await fund(other.borrower, funds(10, 0));
    await makeOffer(db, accepting.lender, { rentalId: id, deposit: 50, ...ADDRESSES }, NOW);

    await expect(
      respondToOffer(db, accepting.borrower, { rentalId: id, action: "ACCEPT" }, NOW),
    ).rejects.toMatchObject({ code: "internal" });

    expect(await status(id)).toBe("REQUESTED");
    expect(await status(overlapping)).toBe("REQUESTED");
    expect(await wallet(accepting.borrower)).toEqual(funds(100));
  });
});
