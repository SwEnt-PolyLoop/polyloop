// Made with Claude.

import { randomUUID } from "node:crypto";
import { Firestore, Timestamp, getFirestore } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { requestRental } from "./index";
import {
  DATES_UNAVAILABLE,
  RentalRequestInput,
  createRentalRequest,
  parseRentalRequestInput,
} from "./rentalRequest";

// Firebase loads this ES module to verify tokens, which Jest cannot load; `run` skips that step.
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
  db = getFirestore();
});

// 10 March 2030 in Zurich (11:00 there).
const NOW = new Date("2030-03-10T10:00:00Z");

/** A fresh lender and borrower, each with a profile. */
async function people(): Promise<{ lender: string; borrower: string }> {
  const lender = `lender-${randomUUID()}`;
  const borrower = `borrower-${randomUUID()}`;
  await db.collection("users").doc(lender).set({ name: " Alice Martin " });
  await db.collection("users").doc(borrower).set({ name: "Bob Keller" });
  return { lender, borrower };
}

/** A published listing of [ownerId], available in March and April 2030, with [fields] on top. */
async function listing(ownerId: string, fields: object = {}): Promise<string> {
  const id = randomUUID();
  await db
    .collection("listings")
    .doc(id)
    .set({
      ownerId,
      title: "Camping tent",
      photoUrls: ["https://photos/0.jpg", "https://photos/1.jpg"],
      pricePerDay: 12,
      itemValue: 200,
      availableFrom: "2030-03-01",
      availableTo: "2030-04-30",
      status: "PUBLISHED",
      pickupArea: "Ecublens",
      dropOffArea: "Renens",
      bookedRanges: [],
      ...fields,
    });
  return id;
}

function input(listingId: string, startDate = "2030-03-20", endDate = "2030-03-22") {
  return { listingId, startDate, endDate } satisfies RentalRequestInput;
}

async function rental(id: string) {
  return (await db.collection("rentals").doc(id).get()).data();
}

/** How many rentals exist for [listingId]. */
async function rentalsOf(listingId: string): Promise<number> {
  return (await db.collection("rentals").where("listingId", "==", listingId).get()).size;
}

describe("parseRentalRequestInput", () => {
  it("keeps the listing and the days", () => {
    expect(
      parseRentalRequestInput({
        listingId: " L1 ",
        startDate: "2030-03-20",
        endDate: "2030-03-20",
      }),
    ).toEqual({ listingId: "L1", startDate: "2030-03-20", endDate: "2030-03-20" });
  });

  it("refuses a missing listing, a bad date and a start after the end", () => {
    const bad = [
      undefined,
      { startDate: "2030-03-20", endDate: "2030-03-22" },
      { listingId: "a/b", startDate: "2030-03-20", endDate: "2030-03-22" },
      { listingId: "L1", startDate: "2030-02-30", endDate: "2030-03-22" },
      { listingId: "L1", startDate: "2030-03-20", endDate: 20300322 },
      { listingId: "L1", startDate: "2030-03-22", endDate: "2030-03-20" },
    ];
    for (const data of bad) {
      expect(() => parseRentalRequestInput(data)).toThrow(
        expect.objectContaining({ code: "invalid-argument" }),
      );
    }
  });
});

describe("createRentalRequest", () => {
  it("creates the request with every field copied by the server", async () => {
    const { lender, borrower } = await people();
    const listingId = await listing(lender);

    const id = await createRentalRequest(db, borrower, input(listingId), NOW);

    const data = await rental(id);
    expect(data).toMatchObject({
      listingId,
      listingTitle: "Camping tent",
      listingPhotoUrl: "https://photos/0.jpg",
      ownerId: lender,
      ownerName: "Alice Martin",
      borrowerId: borrower,
      borrowerName: "Bob Keller",
      participants: [lender, borrower],
      startDate: "2030-03-20",
      endDate: "2030-03-22",
      status: "REQUESTED",
      pricePerDay: 12,
      totalPrice: 36,
      pickupArea: "Ecublens",
      dropOffArea: "Renens",
      pickupAddress: "",
      returnAddress: "",
      lastMessage: "",
    });
    expect(data?.createdAt).toBeInstanceOf(Timestamp);
    expect(data?.lastMessageAt).toEqual(data?.createdAt);
    expect(Object.keys(data ?? {})).toHaveLength(20);
  });

  it("copies empty texts when the listing has no photo or areas and a profile is missing", async () => {
    const lender = `lender-${randomUUID()}`;
    const { borrower } = await people();
    const listingId = await listing(lender, { photoUrls: [], pickupArea: null });
    await db.collection("listings").doc(listingId).update({ dropOffArea: null });

    const id = await createRentalRequest(
      db,
      borrower,
      input(listingId, "2030-03-20", "2030-03-20"),
      NOW,
    );

    expect(await rental(id)).toMatchObject({
      listingPhotoUrl: "",
      ownerName: "",
      pickupArea: "",
      dropOffArea: "",
      totalPrice: 12,
    });
  });

  it("refuses the caller's own listing", async () => {
    const { lender } = await people();
    const listingId = await listing(lender);

    await expect(createRentalRequest(db, lender, input(listingId), NOW)).rejects.toMatchObject({
      code: "permission-denied",
    });
    expect(await rentalsOf(listingId)).toBe(0);
  });

  it("refuses a listing that does not exist or is not published", async () => {
    const { lender, borrower } = await people();
    const draft = await listing(lender, { status: "DRAFT" });

    await expect(createRentalRequest(db, borrower, input(randomUUID()), NOW)).rejects.toMatchObject(
      { code: "not-found" },
    );
    await expect(createRentalRequest(db, borrower, input(draft), NOW)).rejects.toMatchObject({
      code: "failed-precondition",
    });
    expect(await rentalsOf(draft)).toBe(0);
  });

  it("refuses days outside the availability window", async () => {
    const { lender, borrower } = await people();
    const listingId = await listing(lender);

    for (const [start, end] of [
      ["2030-02-28", "2030-03-02"],
      ["2030-04-29", "2030-05-01"],
    ]) {
      await expect(
        createRentalRequest(db, borrower, input(listingId, start, end), NOW),
      ).rejects.toMatchObject({ code: "invalid-argument" });
    }
    // Both ends of the window are allowed.
    await createRentalRequest(db, borrower, input(listingId, "2030-04-30", "2030-04-30"), NOW);
    expect(await rentalsOf(listingId)).toBe(1);
  });

  it("refuses a listing whose window is broken", async () => {
    const { lender, borrower } = await people();
    const listingId = await listing(lender, { availableTo: null });

    await expect(createRentalRequest(db, borrower, input(listingId), NOW)).rejects.toMatchObject({
      code: "invalid-argument",
    });
  });

  it("refuses a first day in the past, by Zurich's day", async () => {
    const { lender, borrower } = await people();
    const listingId = await listing(lender);
    // 23:30 UTC on 9 March is already 10 March in Zurich.
    const lateEvening = new Date("2030-03-09T23:30:00Z");

    await expect(
      createRentalRequest(db, borrower, input(listingId, "2030-03-09", "2030-03-12"), lateEvening),
    ).rejects.toMatchObject({ code: "invalid-argument" });
    // Today is allowed.
    await createRentalRequest(
      db,
      borrower,
      input(listingId, "2030-03-10", "2030-03-12"),
      lateEvening,
    );
    expect(await rentalsOf(listingId)).toBe(1);
  });

  it("refuses days that overlap a booking, or only its buffer day", async () => {
    const { lender, borrower } = await people();
    const listingId = await listing(lender, {
      bookedRanges: [{ start: "2030-03-20", end: "2030-03-22" }, "malformed"],
    });

    for (const [start, end] of [
      ["2030-03-21", "2030-03-21"],
      ["2030-03-15", "2030-03-25"],
      ["2030-03-23", "2030-03-24"],
      ["2030-03-17", "2030-03-19"],
    ]) {
      await expect(
        createRentalRequest(db, borrower, input(listingId, start, end), NOW),
      ).rejects.toMatchObject({
        code: "failed-precondition",
        details: { reason: DATES_UNAVAILABLE },
      });
    }
    expect(await rentalsOf(listingId)).toBe(0);
    // Right after the buffer day is free.
    await createRentalRequest(db, borrower, input(listingId, "2030-03-24", "2030-03-25"), NOW);
    expect(await rentalsOf(listingId)).toBe(1);
  });

  it("creates both of two requests for the same days sent together", async () => {
    const { lender, borrower } = await people();
    const other = (await people()).borrower;
    const listingId = await listing(lender);

    const ids = await Promise.all([
      createRentalRequest(db, borrower, input(listingId), NOW),
      createRentalRequest(db, other, input(listingId), NOW),
    ]);

    // A pending request blocks no day; only an accepted one does.
    expect(new Set(ids).size).toBe(2);
    expect(await rentalsOf(listingId)).toBe(2);
  });

  it("refuses a request sent together with an acceptance that books its days", async () => {
    const { lender, borrower } = await people();
    const listingId = await listing(lender);
    const listingRef = db.collection("listings").doc(listingId);

    const [request] = await Promise.allSettled([
      createRentalRequest(db, borrower, input(listingId), NOW),
      // What an acceptance does to the listing, in its own transaction.
      db.runTransaction(async (tx) => {
        await tx.get(listingRef);
        tx.update(listingRef, { bookedRanges: [{ start: "2030-03-20", end: "2030-03-22" }] });
      }),
    ]);

    // Either the request read the listing before the booking, or it was refused after it.
    if (request.status === "rejected") {
      expect(request.reason).toMatchObject({ details: { reason: DATES_UNAVAILABLE } });
      expect(await rentalsOf(listingId)).toBe(0);
    } else {
      expect(await rentalsOf(listingId)).toBe(1);
    }
  });
});

describe("the requestRental callable", () => {
  /** The request of the signed-in user [uid], sending [data]. */
  function asUser(uid: string, data: unknown, email = `${uid}@epfl.ch`, verified = true) {
    return { auth: { uid, token: { email, email_verified: verified } }, data } as never;
  }

  // Far enough in the future to stay valid, whatever day the tests run.
  const FUTURE = { availableFrom: "2099-01-01", availableTo: "2099-12-31" };

  it("runs in the app's region as a callable", () => {
    expect(requestRental.__endpoint.region).toEqual(["europe-west1"]);
    expect(requestRental.__endpoint.callableTrigger).toBeDefined();
  });

  it("returns the new rental's id", async () => {
    const { lender, borrower } = await people();
    const listingId = await listing(lender, FUTURE);

    const result = await requestRental.run(
      asUser(borrower, { listingId, startDate: "2099-03-01", endDate: "2099-03-02" }),
    );

    expect((await rental(result.rentalId))?.borrowerId).toBe(borrower);
  });

  it("refuses a caller who is not signed in, outside EPFL or unverified", async () => {
    const { lender, borrower } = await people();
    const listingId = await listing(lender, FUTURE);
    const data = { listingId, startDate: "2099-03-01", endDate: "2099-03-02" };

    await expect(requestRental.run({ data } as never)).rejects.toMatchObject({
      code: "unauthenticated",
    });
    await expect(requestRental.run(asUser(borrower, data, "bob@gmail.com"))).rejects.toMatchObject({
      code: "permission-denied",
    });
    await expect(
      requestRental.run(asUser(borrower, data, `${borrower}@epfl.ch`, false)),
    ).rejects.toMatchObject({ code: "permission-denied" });
    expect(await rentalsOf(listingId)).toBe(0);
  });

  it("gives the app an HttpsError with the code and the reason", async () => {
    const { lender, borrower } = await people();
    const listingId = await listing(lender, {
      ...FUTURE,
      bookedRanges: [{ start: "2099-03-01", end: "2099-03-02" }],
    });

    const booked = requestRental.run(
      asUser(borrower, { listingId, startDate: "2099-03-01", endDate: "2099-03-01" }),
    );
    await expect(booked).rejects.toBeInstanceOf(HttpsError);
    await expect(booked).rejects.toMatchObject({
      code: "failed-precondition",
      details: { reason: DATES_UNAVAILABLE },
    });
    await expect(requestRental.run(asUser(borrower, { listingId }))).rejects.toMatchObject({
      code: "invalid-argument",
    });
  });
});
