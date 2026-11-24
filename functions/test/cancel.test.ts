// Made with Claude.

import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { Firestore, Timestamp, getFirestore } from "firebase-admin/firestore";
import { CANCELLED, TOO_LATE, cancelRental } from "../src/cancel";
import { RENTAL_CHANGED } from "../src/handover";
import { hasSeenLastMessage } from "../src/testing/readTimes";
import {
  People,
  fund,
  people,
  rental as newRental,
  rows,
  wallet,
} from "../src/testing/walletFixtures";

// handover.ts loads firebase-functions, which loads an ES module that Jest cannot load.
jest.mock("jose", () => ({}));

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

const START = "2026-12-10";
const END = "2026-12-12";
const OTHER_RANGE = { start: "2027-01-05", end: "2027-01-07" };
// 11:00 in Zurich, the day before the first day.
const DAY_BEFORE = new Date("2026-12-09T10:00:00Z");

const rentalData = async (id: string) => (await db.doc(`rentals/${id}`).get()).data();
const exists = async (path: string) => (await db.doc(path).get()).exists;
const messages = async (id: string) =>
  (await db.collection(`rentals/${id}/messages`).get()).docs.map((doc) => doc.data());
const bookedRanges = async (listingId: string) =>
  (await db.doc(`listings/${listingId}`).get()).get("bookedRanges");

/** A REQUESTED rental, with the lender's offer and its addresses when [withOffer]. */
async function requested(who: People, withOffer: boolean): Promise<string> {
  const id = await newRental(who, "REQUESTED", {
    startDate: START,
    endDate: END,
    ...(withOffer
      ? {
          offer: {
            amount: 40,
            authorId: who.lender,
            isCounter: false,
            expiresAt: Timestamp.fromDate(new Date("2026-12-09T23:00:00Z")),
          },
        }
      : {}),
  });
  if (withOffer) {
    await db.doc(`rentals/${id}/private/addresses`).set({ pickup: "Rolex", dropOff: "BC" });
  }
  return id;
}

/**
 * An ACCEPTED rental from [startDate] to [endDate] with its 40 PP deposit held, its pickup code,
 * and a listing that books its days and another range; overridden by [fields].
 */
async function accepted(
  who: People,
  { startDate = START, endDate = END, fields = {} } = {},
): Promise<{ id: string; listingId: string }> {
  const listingId = randomUUID();
  await db.doc(`listings/${listingId}`).set({
    bookedRanges: [{ start: startDate, end: endDate }, OTHER_RANGE],
  });
  await fund(who.borrower, { available: 10, heldAsDeposits: 40, heldForRentals: 0 });
  await fund(who.lender, { available: 7, heldAsDeposits: 0, heldForRentals: 0 });
  const id = await newRental(who, "ACCEPTED", {
    listingId,
    startDate,
    endDate,
    pickupAddress: "Rolex",
    returnAddress: "BC",
    pickupCodeHash: "fingerprint",
    ...fields,
  });
  await db.doc(`rentals/${id}/handoverCodes/PICKUP`).set({ code: "secret" });
  return { id, listingId };
}

describe("cancelling a request", () => {
  for (const withOffer of [false, true]) {
    for (const role of ["borrower", "lender"] as const) {
      it(`lets the ${role} cancel a request ${withOffer ? "with" : "without"} an offer`, async () => {
        const who = people();
        const id = await requested(who, withOffer);
        const caller = who[role];
        const other = role === "borrower" ? who.lender : who.borrower;

        await cancelRental(db, caller, id, DAY_BEFORE);

        const data = await rentalData(id);
        expect(data?.status).toBe(CANCELLED);
        expect(data?.offer).toBeUndefined();
        expect(await exists(`rentals/${id}/private/addresses`)).toBe(false);
        expect(await messages(id)).toEqual([expect.objectContaining({ text: "Cancelled", type: "SYSTEM" })]);
        expect(data?.lastMessage).toBe("Cancelled");
        expect(hasSeenLastMessage(data, caller)).toBe(true);
        expect(hasSeenLastMessage(data, other)).toBe(false);
        expect(await wallet(who.borrower)).toBeUndefined();
        expect(await wallet(who.lender)).toBeUndefined();
      });
    }
  }

  for (const now of ["2026-12-09T23:00:00Z", "2026-12-13T10:00:00Z"]) {
    it(`still cancels a request on or after its first day (${now})`, async () => {
      const who = people();
      const id = await requested(who, false);

      await cancelRental(db, who.borrower, id, new Date(now));

      expect((await rentalData(id))?.status).toBe(CANCELLED);
    });
  }
});

describe("cancelling an accepted rental", () => {
  it("frees its days, deletes the handover codes and gives the deposit back", async () => {
    const who = people();
    const { id, listingId } = await accepted(who, { fields: { returnCodeHash: "fingerprint" } });
    await db.doc(`rentals/${id}/handoverCodes/RETURN`).set({ code: "return secret" });

    await cancelRental(db, who.lender, id, DAY_BEFORE);

    const data = await rentalData(id);
    expect(data?.status).toBe(CANCELLED);
    expect(data?.pickupCodeHash).toBeUndefined();
    expect(await exists(`rentals/${id}/handoverCodes/PICKUP`)).toBe(false);
    expect(data?.returnCodeHash).toBeUndefined();
    expect(await exists(`rentals/${id}/handoverCodes/RETURN`)).toBe(false);
    expect(await bookedRanges(listingId)).toEqual([OTHER_RANGE]);
    expect(await messages(id)).toEqual([expect.objectContaining({ text: "Cancelled", type: "SYSTEM" })]);
    expect(hasSeenLastMessage(data, who.lender)).toBe(true);
    expect(hasSeenLastMessage(data, who.borrower)).toBe(false);
    expect(await wallet(who.borrower)).toEqual({ available: 50, heldAsDeposits: 0, heldForRentals: 0 });
    expect(await rows(who.borrower)).toEqual([
      expect.objectContaining({
        id: `deposit_released_${id}`,
        type: "DEPOSIT_RELEASED",
        amount: 40,
        itemTitle: "Camping tent",
      }),
    ]);
    expect(await wallet(who.lender)).toEqual({ available: 7, heldAsDeposits: 0, heldForRentals: 0 });
    expect(await rows(who.lender)).toEqual([]);
  });

  it("is still cancelled, with its deposit released, when its listing was deleted", async () => {
    const who = people();
    const { id, listingId } = await accepted(who);
    await db.doc(`listings/${listingId}`).delete();

    await cancelRental(db, who.borrower, id, DAY_BEFORE);

    expect((await rentalData(id))?.status).toBe(CANCELLED);
    expect(await exists(`listings/${listingId}`)).toBe(false);
    expect(await wallet(who.borrower)).toMatchObject({ available: 50, heldAsDeposits: 0 });
  });

  // Zurich is 1 hour ahead of UTC in winter and 2 in summer, so the first day starts at 23:00 or
  // 22:00 UTC the day before.
  const seasons = [
    {
      startDate: "2026-12-10",
      endDate: "2026-12-12",
      lastMoment: "2026-12-09T22:59:59Z",
      midnight: "2026-12-09T23:00:00Z",
      later: "2026-12-10T15:00:00Z",
    },
    {
      startDate: "2026-07-10",
      endDate: "2026-07-12",
      lastMoment: "2026-07-09T21:59:59Z",
      midnight: "2026-07-09T22:00:00Z",
      later: "2026-07-10T15:00:00Z",
    },
  ];
  for (const { startDate, endDate, lastMoment, midnight, later } of seasons) {
    for (const now of [midnight, later]) {
      it(`refuses an accepted rental starting ${startDate} with TOO_LATE at ${now}`, async () => {
        const who = people();
        const { id, listingId } = await accepted(who, { startDate, endDate });

        await expect(cancelRental(db, who.borrower, id, new Date(now))).rejects.toMatchObject({
          code: "failed-precondition",
          details: { reason: TOO_LATE },
        });

        expect((await rentalData(id))?.status).toBe("ACCEPTED");
        expect(await exists(`rentals/${id}/handoverCodes/PICKUP`)).toBe(true);
        expect(await bookedRanges(listingId)).toHaveLength(2);
        expect(await wallet(who.borrower)).toMatchObject({ available: 10, heldAsDeposits: 40 });
        expect(await messages(id)).toEqual([]);
      });
    }

    it(`cancels an accepted rental starting ${startDate} at ${lastMoment}`, async () => {
      const who = people();
      const { id } = await accepted(who, { startDate, endDate });

      await cancelRental(db, who.borrower, id, new Date(lastMoment));

      expect((await rentalData(id))?.status).toBe(CANCELLED);
    });
  }

  it("succeeds and changes nothing when cancelled a second time", async () => {
    const who = people();
    const { id } = await accepted(who);
    await cancelRental(db, who.borrower, id, DAY_BEFORE);
    const walletBefore = await wallet(who.borrower);
    const rowsBefore = await rows(who.borrower);
    const messagesBefore = await messages(id);

    await cancelRental(db, who.lender, id, DAY_BEFORE);

    expect(await wallet(who.borrower)).toEqual(walletBefore);
    expect(await rows(who.borrower)).toEqual(rowsBefore);
    expect(await messages(id)).toEqual(messagesBefore);
    expect(messagesBefore).toHaveLength(1);
  });
});

describe("refusals", () => {
  it("refuses a missing rental", async () => {
    await expect(cancelRental(db, "someone", randomUUID(), DAY_BEFORE)).rejects.toMatchObject({
      code: "not-found",
    });
  });

  it("refuses a third person and writes nothing", async () => {
    const who = people();
    const { id } = await accepted(who);

    await expect(cancelRental(db, `stranger-${randomUUID()}`, id, DAY_BEFORE)).rejects.toMatchObject({
      code: "permission-denied",
    });

    expect((await rentalData(id))?.status).toBe("ACCEPTED");
    expect(await wallet(who.borrower)).toMatchObject({ available: 10, heldAsDeposits: 40 });
    expect(await messages(id)).toEqual([]);
  });

  for (const status of ["ACTIVE", "DECLINED", "CLOSED"]) {
    it(`refuses a ${status} rental with RENTAL_CHANGED and writes nothing`, async () => {
      const who = people();
      const { id, listingId } = await accepted(who, { fields: { status } });

      await expect(cancelRental(db, who.borrower, id, DAY_BEFORE)).rejects.toMatchObject({
        code: "failed-precondition",
        details: { reason: RENTAL_CHANGED },
      });

      expect((await rentalData(id))?.status).toBe(status);
      expect(await exists(`rentals/${id}/handoverCodes/PICKUP`)).toBe(true);
      expect(await bookedRanges(listingId)).toHaveLength(2);
      expect(await wallet(who.borrower)).toMatchObject({ available: 10, heldAsDeposits: 40 });
      expect(await messages(id)).toEqual([]);
    });
  }
});
