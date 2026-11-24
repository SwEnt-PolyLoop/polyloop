// Made with Claude.

import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { Firestore, getFirestore } from "firebase-admin/firestore";
import { sortBookedRanges } from "./sortBookedRanges";

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

// The backfill reads every listing, so each test starts from none.
beforeEach(async () => {
  await db.recursiveDelete(db.collection("listings"));
});

const NOVEMBER = { start: "2026-11-20", end: "2026-11-22" };
const DECEMBER = { start: "2026-12-01", end: "2026-12-03" };
const JANUARY = { start: "2027-01-10", end: "2027-01-12" };

/** A fresh listing with these fields, as stored before the backfill. */
async function listing(fields: object): Promise<string> {
  const id = randomUUID();
  await db.doc(`listings/${id}`).set({ title: "Tent", ...fields });
  return id;
}

async function stored(id: string): Promise<unknown> {
  return (await db.doc(`listings/${id}`).get()).get("bookedRanges");
}

async function updateTime(id: string): Promise<string | undefined> {
  return (await db.doc(`listings/${id}`).get()).updateTime?.toDate().toISOString();
}

describe("sortBookedRanges", () => {
  it("sorts a list stored out of order, so its last range ends last", async () => {
    const id = await listing({ bookedRanges: [JANUARY, NOVEMBER, DECEMBER] });

    expect(await sortBookedRanges(db)).toBe(1);

    expect(await stored(id)).toEqual([NOVEMBER, DECEMBER, JANUARY]);
    expect((await db.doc(`listings/${id}`).get()).get("title")).toBe("Tent");
  });

  it("leaves out malformed entries, as respondToOffer does", async () => {
    const id = await listing({ bookedRanges: [NOVEMBER, "malformed", DECEMBER] });

    expect(await sortBookedRanges(db)).toBe(1);

    expect(await stored(id)).toEqual([NOVEMBER, DECEMBER]);
  });

  it("does not write a listing already in order, nor one without bookings", async () => {
    const sorted = await listing({ bookedRanges: [NOVEMBER, DECEMBER] });
    const empty = await listing({ bookedRanges: [] });
    const missing = await listing({});
    const before = await Promise.all([sorted, empty, missing].map(updateTime));

    expect(await sortBookedRanges(db)).toBe(0);

    expect(await Promise.all([sorted, empty, missing].map(updateTime))).toEqual(before);
    expect(await stored(missing)).toBeUndefined();
  });

  it("rewrites nothing the second time it runs", async () => {
    await listing({ bookedRanges: [DECEMBER, NOVEMBER] });
    await listing({ bookedRanges: [JANUARY, DECEMBER] });

    expect(await sortBookedRanges(db)).toBe(2);
    expect(await sortBookedRanges(db)).toBe(0);
  });
});
