// Made with Claude.

import { randomUUID } from "node:crypto";
import { Firestore, getFirestore } from "firebase-admin/firestore";
import { requestRental } from "./index";
import { zurichDay } from "./serverToday";
import { asUser, fund, rows, wallet } from "./testing/walletFixtures";

// Firebase loads this ES module to verify tokens, which Jest cannot load; `run` skips that step.
jest.mock("jose", () => ({}));

// requestRental as index.ts exports it, called with what the app sends, on the Firestore emulator.
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

/** The Zurich day [offset] days from today, as YYYY-MM-DD. */
function day(offset: number): string {
  const date = new Date(`${zurichDay(new Date())}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + offset);
  return date.toISOString().slice(0, 10);
}

interface Scene {
  lender: string;
  borrower: string;
  listingId: string;
}

/** A published listing of 10 PP a day, open from tomorrow for 60 days, and both profiles. */
async function scene(listing: object = {}): Promise<Scene> {
  const id = randomUUID();
  const scene = { lender: `lender-${id}`, borrower: `borrower-${id}`, listingId: `listing-${id}` };
  await db.collection("users").doc(scene.lender).set({ name: "  Lena Lender " });
  await db.collection("users").doc(scene.borrower).set({ name: "Ben Borrower" });
  // A field given as undefined is left out of the listing, to test a missing field.
  const fields = {
  ownerId: scene.lender,
  title: "Camping tent",
  photoUrls: ["https://x/0.jpg", "https://x/1.jpg", "https://x/2.jpg"],
  pricePerDay: 10,
  availableFrom: day(1),
  availableTo: day(60),
  status: "PUBLISHED",
  pickupArea: "Ecublens",
  dropOffArea: "Lausanne",
  bookedRanges: [],
  ...listing,
  };
  const stored = Object.fromEntries(Object.entries(fields).filter(([, v]) => v !== undefined));
  await db.collection("listings").doc(scene.listingId).set(stored);
  return scene;
}

/** What the app sends: a fresh rental id, the listing and the dates (3 days from day 10). */
function input(s: Scene, fields: object = {}) {
  return {
    rentalId: randomUUID(),
    listingId: s.listingId,
    startDate: day(10),
    endDate: day(12),
    ...fields,
  };
}

async function rentalDoc(id: string) {
  return (await db.collection("rentals").doc(id).get()).data();
}

describe("sending a request", () => {
  it("creates the rental, holds the price and writes one RENTAL_HELD row", async () => {
    const s = await scene();
    await fund(s.borrower, { available: 100, heldAsDeposits: 5, heldForRentals: 15 });
    const data = input(s);

    const result = await requestRental.run(asUser(s.borrower, data));

    expect(result).toEqual({ rentalId: data.rentalId });
    expect(await wallet(s.borrower)).toEqual({
      available: 70,
      heldAsDeposits: 5,
      heldForRentals: 45,
    });
    expect(await rows(s.borrower)).toEqual([
      expect.objectContaining({
        id: `rental_held_${data.rentalId}`,
        type: "RENTAL_HELD",
        amount: -30,
        itemTitle: "Camping tent",
      }),
    ]);
    const saved = await db.collection("rentals").doc(data.rentalId).get();
    expect(saved.data()).toEqual({
      listingId: s.listingId,
      listingTitle: "Camping tent",
      listingPhotoUrl: "https://x/0.jpg",
      ownerId: s.lender,
      ownerName: "Lena Lender",
      borrowerId: s.borrower,
      borrowerName: "Ben Borrower",
      participants: [s.lender, s.borrower],
      startDate: day(10),
      endDate: day(12),
      status: "REQUESTED",
      pricePerDay: 10,
      totalPrice: 30,
      pickupArea: "Ecublens",
      dropOffArea: "Lausanne",
      pickupAddress: "",
      returnAddress: "",
      createdAt: expect.anything(),
      lastMessage: "",
      lastMessageAt: expect.anything(),
    });
    expect(saved.get("createdAt").toMillis()).toBeGreaterThan(0);
    expect(saved.get("lastMessageAt").toMillis()).toBe(saved.get("createdAt").toMillis());
  });

  it("charges one day for a one-day rental", async () => {
    const s = await scene();
    await fund(s.borrower, { available: 100 });

    await requestRental.run(asUser(s.borrower, input(s, { endDate: day(10) })));

    expect(await wallet(s.borrower)).toMatchObject({ available: 90, heldForRentals: 10 });
  });

  it("succeeds with exactly the balance it needs, leaving 0 available", async () => {
    const s = await scene();
    await fund(s.borrower, { available: 30 });

    await requestRental.run(asUser(s.borrower, input(s)));

    expect(await wallet(s.borrower)).toEqual({
      available: 0,
      heldAsDeposits: 0,
      heldForRentals: 30,
    });
  });

  it("ignores any price, owner, name, title or area the client sends", async () => {
    const s = await scene();
    await fund(s.borrower, { available: 100 });
    const data = input(s, {
      totalPrice: 1,
      pricePerDay: 1,
      ownerId: s.borrower,
      ownerName: "Fake",
      borrowerName: "Fake",
      listingTitle: "Fake",
      listingPhotoUrl: "https://evil/x.jpg",
      pickupArea: "Nowhere",
      status: "ACCEPTED",
    });

    await requestRental.run(asUser(s.borrower, data));

    expect(await rentalDoc(data.rentalId)).toMatchObject({
      totalPrice: 30,
      pricePerDay: 10,
      ownerId: s.lender,
      ownerName: "Lena Lender",
      borrowerName: "Ben Borrower",
      listingTitle: "Camping tent",
      listingPhotoUrl: "https://x/0.jpg",
      pickupArea: "Ecublens",
      status: "REQUESTED",
    });
    expect(await wallet(s.borrower)).toMatchObject({ available: 70 });
  });

  it("stores an empty name for a missing profile or a blank name, and an empty photo for none", async () => {
    const s = await scene({ photoUrls: [] });
    await db.collection("users").doc(s.lender).delete();
    await db.collection("users").doc(s.borrower).set({ name: "   " });
    await fund(s.borrower, { available: 100 });
    const data = input(s);

    await requestRental.run(asUser(s.borrower, data));

    expect(await rentalDoc(data.rentalId)).toMatchObject({
      ownerName: "",
      borrowerName: "",
      listingPhotoUrl: "",
    });
  });

  it("is not blocked by a pending request for the same days", async () => {
    const s = await scene();
    await fund(s.borrower, { available: 100 });
    const other = `other-${randomUUID()}`;
    await fund(other, { available: 100 });

    await requestRental.run(asUser(other, input(s)));
    await requestRental.run(asUser(s.borrower, input(s)));

    expect(await wallet(s.borrower)).toMatchObject({ available: 70 });
  });
});

describe("a balance too low", () => {
  it("fails with INSUFFICIENT_FUNDS and writes nothing", async () => {
    const s = await scene();
    await fund(s.borrower, { available: 29, heldForRentals: 100 });
    const data = input(s);

    await expect(requestRental.run(asUser(s.borrower, data))).rejects.toMatchObject({
      code: "failed-precondition",
      details: { reason: "INSUFFICIENT_FUNDS" },
    });

    expect(await wallet(s.borrower)).toEqual({ available: 29, heldForRentals: 100 });
    expect(await rows(s.borrower)).toEqual([]);
    expect(await rentalDoc(data.rentalId)).toBeUndefined();
  });

  it("fails for a borrower with no wallet, and creates none", async () => {
    const s = await scene();

    await expect(requestRental.run(asUser(s.borrower, input(s)))).rejects.toMatchObject({
      code: "failed-precondition",
      details: { reason: "INSUFFICIENT_FUNDS" },
    });

    expect(await wallet(s.borrower)).toBeUndefined();
  });

  it("does not count PP that are already held", async () => {
    const s = await scene();
    await fund(s.borrower, { available: 20, heldForRentals: 500, heldAsDeposits: 500 });

    await expect(requestRental.run(asUser(s.borrower, input(s)))).rejects.toMatchObject({
      details: { reason: "INSUFFICIENT_FUNDS" },
    });
  });
});

describe("refused requests write nothing", () => {
  async function expectRefused(s: Scene, data: object, code: string) {
    await fund(s.borrower, { available: 100 });
    const id = (data as { rentalId: string }).rentalId;

    await expect(requestRental.run(asUser(s.borrower, data))).rejects.toMatchObject({ code });

    expect(await wallet(s.borrower)).toEqual({ available: 100 });
    expect(await rows(s.borrower)).toEqual([]);
    expect(await rentalDoc(id)).toBeUndefined();
  }

  it("refuses dates that touch a booking, buffer days included", async () => {
    const s = await scene({ bookedRanges: [{ start: day(13), end: day(15) }] });

    // day(12) is the buffer day before the booking.
    await expectRefused(s, input(s), "failed-precondition");
    await expectRefused(s, input(s, { startDate: day(14), endDate: day(20) }), "failed-precondition");
  });

  it("accepts dates one free day away from a booking", async () => {
    const s = await scene({ bookedRanges: [{ start: day(14), end: day(15) }] });
    await fund(s.borrower, { available: 100 });

    await requestRental.run(asUser(s.borrower, input(s)));

    expect(await wallet(s.borrower)).toMatchObject({ available: 70 });
  });

  it("refuses the caller's own listing", async () => {
    const s = await scene();
    await fund(s.lender, { available: 100 });

    await expect(requestRental.run(asUser(s.lender, input(s)))).rejects.toMatchObject({
      code: "permission-denied",
    });

    expect(await wallet(s.lender)).toEqual({ available: 100 });
  });

  it("refuses a listing that is not published", async () => {
    for (const status of ["DRAFT", "ARCHIVED", undefined]) {
      const s = await scene({ status });
      await expectRefused(s, input(s), "failed-precondition");
    }
  });

  it("refuses a listing that does not exist", async () => {
    const s = await scene();
    await expectRefused(s, input(s, { listingId: `missing-${randomUUID()}` }), "not-found");
  });

  it("refuses dates in the past, and today's date counts as not past", async () => {
    const s = await scene({ availableFrom: day(-30) });
    await expectRefused(s, input(s, { startDate: day(-1), endDate: day(2) }), "invalid-argument");
  });

  it("accepts a rental that starts today", async () => {
    const s = await scene({ availableFrom: day(-30) });
    await fund(s.borrower, { available: 100 });

    await requestRental.run(asUser(s.borrower, input(s, { startDate: day(0), endDate: day(0) })));

    expect(await wallet(s.borrower)).toMatchObject({ available: 90 });
  });

  it("refuses malformed dates and an end before the start", async () => {
    const s = await scene();
    await expectRefused(s, input(s, { startDate: "tomorrow" }), "invalid-argument");
    await expectRefused(s, input(s, { endDate: undefined }), "invalid-argument");
    await expectRefused(s, input(s, { startDate: day(12), endDate: day(10) }), "invalid-argument");
  });

  it("refuses dates outside the availability window", async () => {
    const s = await scene({ availableFrom: day(11), availableTo: day(60) });
    await expectRefused(s, input(s), "failed-precondition");
    const t = await scene({ availableFrom: day(1), availableTo: day(11) });
    await expectRefused(t, input(t), "failed-precondition");
  });

  it("accepts the first and the last day of the window", async () => {
    const s = await scene({ availableFrom: day(10), availableTo: day(12) });
    await fund(s.borrower, { available: 100 });

    await requestRental.run(asUser(s.borrower, input(s)));

    expect(await wallet(s.borrower)).toMatchObject({ available: 70 });
  });

  it("refuses a listing with no valid price or window, as a server error", async () => {
    for (const broken of [
      { pricePerDay: 0 },
      { pricePerDay: 2.5 },
      { pricePerDay: "10" },
      { availableFrom: undefined },
      { ownerId: undefined },
    ]) {
      const s = await scene(broken);
      await expectRefused(s, input(s), "internal");
    }
  });

  it("refuses a missing or malformed listing id or rental id", async () => {
    const s = await scene();
    await fund(s.borrower, { available: 100 });
    for (const patch of [{ listingId: undefined }, { rentalId: "a/b" }, { rentalId: undefined }]) {
      await expect(requestRental.run(asUser(s.borrower, input(s, patch)))).rejects.toMatchObject({
        code: "invalid-argument",
      });
    }
    expect(await wallet(s.borrower)).toEqual({ available: 100 });
  });
});

describe("calling twice", () => {
  it("returns the same id and holds the price only once for the same request", async () => {
    const s = await scene();
    await fund(s.borrower, { available: 100 });
    const data = input(s);

    const first = await requestRental.run(asUser(s.borrower, data));
    const second = await requestRental.run(asUser(s.borrower, data));

    expect(second).toEqual(first);
    expect(await wallet(s.borrower)).toMatchObject({ available: 70, heldForRentals: 30 });
    expect(await rows(s.borrower)).toHaveLength(1);
  });

  it("succeeds on a retry even when the balance has dropped since", async () => {
    const s = await scene();
    await fund(s.borrower, { available: 30 });
    const data = input(s);
    await requestRental.run(asUser(s.borrower, data));

    const retry = await requestRental.run(asUser(s.borrower, data));

    expect(retry).toEqual({ rentalId: data.rentalId });
    expect(await wallet(s.borrower)).toMatchObject({ available: 0, heldForRentals: 30 });
  });

  it("refuses the same id with other dates or another listing, changing nothing", async () => {
    const s = await scene();
    const t = await scene();
    await fund(s.borrower, { available: 100 });
    const data = input(s);
    await requestRental.run(asUser(s.borrower, data));

    for (const patch of [{ endDate: day(13) }, { startDate: day(11) }, { listingId: t.listingId }]) {
      await expect(
        requestRental.run(asUser(s.borrower, { ...data, ...patch })),
      ).rejects.toMatchObject({ code: "already-exists" });
    }

    expect(await wallet(s.borrower)).toMatchObject({ available: 70, heldForRentals: 30 });
    expect(await rentalDoc(data.rentalId)).toMatchObject({ endDate: day(12), totalPrice: 30 });
  });

  it("refuses the id of another borrower's rental, changing nothing", async () => {
    const s = await scene();
    const other = `other-${randomUUID()}`;
    await fund(s.borrower, { available: 100 });
    await fund(other, { available: 100 });
    const data = input(s);
    await requestRental.run(asUser(s.borrower, data));

    await expect(requestRental.run(asUser(other, data))).rejects.toMatchObject({
      code: "already-exists",
    });

    expect(await wallet(other)).toEqual({ available: 100 });
    expect(await rentalDoc(data.rentalId)).toMatchObject({ borrowerId: s.borrower });
  });

  it("refuses the id of an existing rental whatever its status", async () => {
    const s = await scene();
    await fund(s.borrower, { available: 100 });
    const data = input(s);
    await db.collection("rentals").doc(data.rentalId).set({ borrowerId: s.borrower, status: "ACTIVE" });

    await expect(requestRental.run(asUser(s.borrower, data))).rejects.toMatchObject({
      code: "already-exists",
    });

    expect(await wallet(s.borrower)).toEqual({ available: 100 });
  });
});

describe("the caller", () => {
  it("refuses a caller who is not signed in", async () => {
    const s = await scene();

    await expect(requestRental.run({ data: input(s) } as never)).rejects.toMatchObject({
      code: "unauthenticated",
    });
  });

  it("refuses an unverified or non-EPFL account, writing nothing", async () => {
    const s = await scene();
    await fund(s.borrower, { available: 100 });
    const data = input(s);

    await expect(
      requestRental.run(asUser(s.borrower, data, `${s.borrower}@epfl.ch`, false)),
    ).rejects.toMatchObject({ code: "permission-denied" });
    await expect(
      requestRental.run(asUser(s.borrower, data, `${s.borrower}@gmail.com`)),
    ).rejects.toMatchObject({ code: "permission-denied" });

    expect(await wallet(s.borrower)).toEqual({ available: 100 });
    expect(await rentalDoc(data.rentalId)).toBeUndefined();
  });

  it("refuses a call with no input", async () => {
    await expect(requestRental.run(asUser("anyone", {}))).rejects.toMatchObject({
      code: "invalid-argument",
    });
  });
});
