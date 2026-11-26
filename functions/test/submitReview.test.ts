// Made with Claude.

import { Firestore, Timestamp, getFirestore } from "firebase-admin/firestore";
import { submitReview } from "../src/index";
import { asUser, people, People, rental } from "../src/testing/walletFixtures";

// Firebase loads this ES module to verify tokens, which Jest cannot load; `run` skips that step.
jest.mock("jose", () => ({}));

// submitReview as index.ts exports it, called with what the app sends, on the Firestore emulator.
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

/** A rental with [status] between new people, who both have a profile. */
async function closed(status = "CLOSED") {
  const who = people();
  for (const [uid, name] of [
    [who.lender, "Lena"],
    [who.borrower, "Bo"],
  ]) {
    await db.doc(`users/${uid}`).set({ name, email: `${uid}@epfl.ch` });
  }
  const id = await rental(who, status, { ownerName: "Lena", borrowerName: "Bo" });
  return { who, id };
}

const review = (id: string, author: string) =>
  submitReview.run(asUser(author, { rentalId: id, stars: 4 }));
const profile = async (uid: string) => (await db.doc(`users/${uid}`).get()).data();
const reviewDoc = async (id: string, reviewee: string) =>
  (await db.doc(`users/${reviewee}/reviews/${id}`).get()).data();

/** Checks that [run] fails with [code] and that nothing was written for [who] on [id]. */
async function refusedUnchanged(
  who: People,
  id: string,
  run: () => Promise<unknown>,
  code: object,
) {
  const before = [await profile(who.lender), await profile(who.borrower)];
  await expect(run()).rejects.toMatchObject(code);
  expect([await profile(who.lender), await profile(who.borrower)]).toEqual(before);
  expect(await reviewDoc(id, who.borrower)).toBeUndefined();
  expect(await reviewDoc(id, who.lender)).toBeUndefined();
}

describe("submitReview", () => {
  it.each(["CLOSED", "LOST"])(
    "saves a first review on a %s rental and gives the reviewee their first rating",
    async (status) => {
      const { who, id } = await closed(status);

      const result = await submitReview.run(asUser(who.borrower, { rentalId: id, stars: 4 }));

      expect(result).toEqual({});
      const saved = await reviewDoc(id, who.lender);
      expect(saved).toMatchObject({
        rentalId: id,
        authorId: who.borrower,
        authorName: "Bo",
        stars: 4,
        itemTitle: "Camping tent",
      });
      expect(saved?.createdAt).toBeInstanceOf(Timestamp);
      expect(Object.keys(saved ?? {}).sort()).toEqual(
        ["authorId", "authorName", "createdAt", "itemTitle", "rentalId", "stars"],
      );
      expect(await profile(who.lender)).toMatchObject({ averageRating: 4, reviewCount: 1 });
      expect(await profile(who.borrower)).not.toHaveProperty("reviewCount");
    },
  );

  it.each([
    { stored: {}, stars: [5, 2], average: 3.5, count: 2 },
    { stored: {}, stars: [5, 4, 4], average: 13 / 3, count: 3 },
    { stored: { averageRating: 3, reviewCount: 2 }, stars: [5], average: 11 / 3, count: 3 },
    { stored: { averageRating: "x", reviewCount: -1 }, stars: [2], average: 2, count: 1 },
  ])(
    "averages $stars on top of $stored without rounding",
    async ({ stored, stars, average, count }) => {
      const lender = people().lender;
      await db
        .doc(`users/${lender}`)
        .set({ name: "Lena", email: `${lender}@epfl.ch`, photoUrl: "p", ...stored });
      for (const given of stars) {
        const borrower = people().borrower;
        const id = await rental({ lender, borrower }, "CLOSED");
        await submitReview.run(asUser(borrower, { rentalId: id, stars: given }));
      }

      const after = await profile(lender);
      expect(after?.averageRating).toBeCloseTo(average, 12);
      expect(after).toMatchObject({
        reviewCount: count,
        name: "Lena",
        email: `${lender}@epfl.ch`,
        photoUrl: "p",
      });
    },
  );

  it("lets the borrower and the lender each review the other once", async () => {
    const { who, id } = await closed();

    await submitReview.run(asUser(who.borrower, { rentalId: id, stars: 5 }));
    await submitReview.run(asUser(who.lender, { rentalId: id, stars: 2 }));

    expect(await profile(who.lender)).toMatchObject({ averageRating: 5, reviewCount: 1 });
    expect(await profile(who.borrower)).toMatchObject({ averageRating: 2, reviewCount: 1 });
    expect(await reviewDoc(id, who.borrower)).toMatchObject({
      authorId: who.lender,
      authorName: "Lena",
      stars: 2,
    });
  });

  it("ignores a second review by the same person and keeps the first stars", async () => {
    const { who, id } = await closed();
    await submitReview.run(asUser(who.borrower, { rentalId: id, stars: 4 }));
    const before = await reviewDoc(id, who.lender);

    const result = await submitReview.run(asUser(who.borrower, { rentalId: id, stars: 2 }));

    expect(result).toEqual({});
    expect(await reviewDoc(id, who.lender)).toEqual(before);
    expect(before).toMatchObject({ stars: 4 });
    expect(await profile(who.lender)).toMatchObject({ averageRating: 4, reviewCount: 1 });
  });

  it.each(["RETURNED", "DISPUTED", "ACTIVE"])("refuses a %s rental", async (status) => {
    const { who, id } = await closed(status);

    await refusedUnchanged(who, id, () => review(id, who.borrower), {
      code: "failed-precondition",
      details: { reason: "NOT_CLOSED" },
    });
  });

  it("refuses a stranger, and a caller not signed in, not verified or not from EPFL", async () => {
    const { who, id } = await closed();
    const data = { rentalId: id, stars: 4 };

    for (const [request, code] of [
      [asUser("stranger", data), "permission-denied"],
      [{ data }, "unauthenticated"],
      [asUser(who.borrower, data, `${who.borrower}@epfl.ch`, false), "permission-denied"],
      [asUser(who.borrower, data, "bo@gmail.com"), "permission-denied"],
    ] as const) {
      await refusedUnchanged(who, id, () => submitReview.run(request as never), { code });
    }
  });

  it("checks the caller before the input", async () => {
    const { who, id } = await closed();
    const unverified = (data: object) =>
      asUser(who.borrower, data, `${who.borrower}@epfl.ch`, false);

    for (const data of [{ rentalId: id, stars: 0 }, { stars: 4 }]) {
      for (const [request, code] of [
        [{ data }, "unauthenticated"],
        [unverified(data), "permission-denied"],
        [asUser(who.borrower, data, "bo@gmail.com"), "permission-denied"],
      ] as const) {
        await refusedUnchanged(who, id, () => submitReview.run(request as never), { code });
      }
    }
  });

  it.each([0, 6, 2.5, "4", null, undefined])("refuses %p stars", async (stars) => {
    const { who, id } = await closed();

    const request = asUser(who.borrower, { rentalId: id, stars });

    await refusedUnchanged(who, id, () => submitReview.run(request), {
      code: "invalid-argument",
    });
  });

  it("refuses an input with no rentalId", async () => {
    for (const data of [{ stars: 4 }, { rentalId: " ", stars: 4 }, { rentalId: 42, stars: 4 }]) {
      await expect(submitReview.run(asUser("someone", data))).rejects.toMatchObject({
        code: "invalid-argument",
      });
    }
  });

  it.each([{ reviewCount: 3 }, { reviewCount: 3, averageRating: 7 }])(
    "refuses a reviewee whose stored rating is %p and writes nothing",
    async (stored) => {
      const { who, id } = await closed();
      await db.doc(`users/${who.lender}`).set(stored, { merge: true });

      await refusedUnchanged(who, id, () => review(id, who.borrower), { code: "internal" });
    },
  );

  it("refuses a missing rental", async () => {
    await expect(review("no-such-rental", "someone")).rejects.toMatchObject({ code: "not-found" });
  });

  it("refuses when the reviewee has no profile and writes nothing", async () => {
    const { who, id } = await closed();
    await db.doc(`users/${who.lender}`).delete();

    await expect(review(id, who.borrower)).rejects.toMatchObject({ code: "not-found" });
    expect(await reviewDoc(id, who.lender)).toBeUndefined();
    expect(await profile(who.lender)).toBeUndefined();
  });

  it("accepts two reviews sent at once and counts one", async () => {
    const { who, id } = await closed();

    const results = await Promise.all([
      submitReview.run(asUser(who.borrower, { rentalId: id, stars: 5 })),
      submitReview.run(asUser(who.borrower, { rentalId: id, stars: 5 })),
    ]);

    expect(results).toEqual([{}, {}]);
    expect(await reviewDoc(id, who.lender)).toMatchObject({ stars: 5 });
    expect(await profile(who.lender)).toMatchObject({ averageRating: 5, reviewCount: 1 });
  });

  it("is a callable in the app's region", () => {
    expect(submitReview.__endpoint.region).toEqual(["europe-west1"]);
    expect(submitReview.__endpoint.callableTrigger).toBeDefined();
  });
});
