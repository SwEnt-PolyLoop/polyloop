// Made with Claude.

import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { Firestore, getFirestore } from "firebase-admin/firestore";
import { acceptRental, declineRental } from "./rentalAnswers";

// The transactions are tested on the emulator; the rules themselves are in rentalRules.test.ts.
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

const LENDER = "lender-uid";
const BORROWER = "borrower-uid";

/** A fresh listing with [bookedRanges] (accepted rentals only), owned by LENDER. */
async function listing(bookedRanges: object[] = []): Promise<string> {
  const id = randomUUID();
  await db.collection("listings").doc(id).set({ ownerId: LENDER, bookedRanges });
  return id;
}

/** A request on [listingId] for [start] to [end], as the app writes it. */
async function request(
  listingId: string,
  start: string,
  end: string,
  status = "REQUESTED",
): Promise<string> {
  const id = randomUUID();
  await db.collection("rentals").doc(id).set({
    listingId,
    ownerId: LENDER,
    borrowerId: BORROWER,
    startDate: start,
    endDate: end,
    status,
  });
  return id;
}

async function rental(id: string) {
  return (await db.collection("rentals").doc(id).get()).data();
}

const ANSWER = {
  deposit: 80,
  pickupAddress: "Route Cantonale 1, Ecublens",
  returnAddress: "Rue de Lausanne 10, Renens",
};

describe("acceptRental", () => {
  it("accepts the request, stores the deposit and addresses, and books the dates", async () => {
    const listingId = await listing();
    const id = await request(listingId, "2026-12-01", "2026-12-03");

    const result = await acceptRental(db, LENDER, { rentalId: id, ...ANSWER });

    expect(result.declinedRentalIds).toEqual([]);
    expect(await rental(id)).toMatchObject({ status: "ACCEPTED", ...ANSWER });
    const listingData = (await db.collection("listings").doc(listingId).get()).data();
    expect(listingData?.bookedRanges).toEqual([{ start: "2026-12-01", end: "2026-12-03" }]);
  });

  it("declines the other requests whose dates overlap, buffer day included", async () => {
    const listingId = await listing();
    const accepted = await request(listingId, "2026-12-10", "2026-12-12");
    const touching = await request(listingId, "2026-12-13", "2026-12-14");
    const insideGap = await request(listingId, "2026-12-14", "2026-12-15");
    // The booking starts on the 10th, so the 9th is its buffer day: a request ending there overlaps.
    const beforeBuffer = await request(listingId, "2026-12-08", "2026-12-09");

    const result = await acceptRental(db, LENDER, { rentalId: accepted, ...ANSWER });

    expect(result.declinedRentalIds.sort()).toEqual([touching, beforeBuffer].sort());
    expect((await rental(touching))?.status).toBe("DECLINED");
    expect((await rental(beforeBuffer))?.status).toBe("DECLINED");
    expect((await rental(insideGap))?.status).toBe("REQUESTED");
  });

  it("leaves requests on other listings alone", async () => {
    const listingId = await listing();
    const otherListing = await listing();
    const accepted = await request(listingId, "2026-12-20", "2026-12-21");
    const elsewhere = await request(otherListing, "2026-12-20", "2026-12-21");

    await acceptRental(db, LENDER, { rentalId: accepted, ...ANSWER });

    expect((await rental(elsewhere))?.status).toBe("REQUESTED");
  });

  it("refuses dates already booked by an accepted rental", async () => {
    const listingId = await listing([{ start: "2027-01-05", end: "2027-01-06" }]);
    const id = await request(listingId, "2027-01-07", "2027-01-08");

    await expect(acceptRental(db, LENDER, { rentalId: id, ...ANSWER })).rejects.toMatchObject({
      code: "failed-precondition",
    });
    expect((await rental(id))?.status).toBe("REQUESTED");
  });

  it("refuses a caller who is not the lender, and writes nothing", async () => {
    const listingId = await listing();
    const id = await request(listingId, "2026-12-01", "2026-12-02");

    await expect(
      acceptRental(db, BORROWER, { rentalId: id, ...ANSWER }),
    ).rejects.toMatchObject({ code: "permission-denied" });
    expect((await rental(id))?.status).toBe("REQUESTED");
  });

  it("refuses a rental that is not requested any more", async () => {
    const listingId = await listing();
    const id = await request(listingId, "2026-12-01", "2026-12-02", "DECLINED");

    await expect(acceptRental(db, LENDER, { rentalId: id, ...ANSWER })).rejects.toMatchObject({
      code: "failed-precondition",
    });
  });

  it("refuses a missing rental", async () => {
    await expect(
      acceptRental(db, LENDER, { rentalId: randomUUID(), ...ANSWER }),
    ).rejects.toMatchObject({ code: "not-found" });
  });
});

describe("declineRental", () => {
  it("declines a requested rental", async () => {
    const id = await request(await listing(), "2026-12-01", "2026-12-02");

    await declineRental(db, LENDER, id);

    expect((await rental(id))?.status).toBe("DECLINED");
  });

  it("refuses a caller who is not the lender", async () => {
    const id = await request(await listing(), "2026-12-01", "2026-12-02");

    await expect(declineRental(db, BORROWER, id)).rejects.toMatchObject({
      code: "permission-denied",
    });
    expect((await rental(id))?.status).toBe("REQUESTED");
  });

  it("refuses a rental that is not requested", async () => {
    const id = await request(await listing(), "2026-12-01", "2026-12-02", "ACCEPTED");

    await expect(declineRental(db, LENDER, id)).rejects.toMatchObject({
      code: "failed-precondition",
    });
  });
});
