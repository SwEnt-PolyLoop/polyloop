// Made with Claude.

import { randomUUID } from "node:crypto";
import { Firestore, getFirestore } from "firebase-admin/firestore";
import { cancelRental } from "../src/index";
import { zurichDay } from "../src/serverToday";
import { asUser, fund, people, rental, wallet } from "../src/testing/walletFixtures";

// Firebase loads this ES module to verify tokens, which Jest cannot load; `run` skips that step.
jest.mock("jose", () => ({}));

// cancelRental as index.ts exports it, called with what the app sends, on the Firestore emulator.
// The checks themselves are tested on the logic, in cancel.test.ts.
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

/** The day [days] after today in Zurich, as YYYY-MM-DD (the callable uses the real clock). */
function inDays(days: number): string {
  const today = Date.parse(`${zurichDay(new Date())}T00:00:00Z`);
  return new Date(today + days * 86_400_000).toISOString().slice(0, 10);
}

/** An ACCEPTED rental whose pickup is [days] from today, with its 40 PP deposit held. */
async function acceptedIn(days: number) {
  const who = people();
  const listingId = randomUUID();
  const range = { start: inDays(days), end: inDays(days + 2) };
  await db.doc(`listings/${listingId}`).set({ bookedRanges: [range] });
  await fund(who.borrower, { available: 10, heldAsDeposits: 40, heldForRentals: 0 });
  const id = await rental(who, "ACCEPTED", {
    listingId,
    startDate: range.start,
    endDate: range.end,
  });
  return { who, id };
}

const status = async (id: string) => (await db.doc(`rentals/${id}`).get()).get("status");

describe("cancelRental", () => {
  it("cancels an accepted rental whose pickup is in a few days", async () => {
    const { who, id } = await acceptedIn(3);

    const result = await cancelRental.run(asUser(who.lender, { rentalId: id }));

    expect(result).toEqual({});
    expect(await status(id)).toBe("CANCELLED");
    expect(await wallet(who.borrower)).toMatchObject({ available: 50, heldAsDeposits: 0 });
  });

  it("gives the app the reason of a refusal on the pickup day", async () => {
    const { who, id } = await acceptedIn(0);

    await expect(
      cancelRental.run(asUser(who.borrower, { rentalId: id })),
    ).rejects.toMatchObject({ code: "failed-precondition", details: { reason: "TOO_LATE" } });
    expect(await status(id)).toBe("ACCEPTED");
  });

  it("refuses a caller who is not signed in, not verified or not from EPFL", async () => {
    const { who, id } = await acceptedIn(3);
    const data = { rentalId: id };

    await expect(cancelRental.run({ data } as never)).rejects.toMatchObject({
      code: "unauthenticated",
    });
    await expect(
      cancelRental.run(asUser(who.borrower, data, "borrower@epfl.ch", false)),
    ).rejects.toMatchObject({ code: "permission-denied" });
    await expect(
      cancelRental.run(asUser(who.borrower, data, "borrower@gmail.com")),
    ).rejects.toMatchObject({ code: "permission-denied" });
    expect(await status(id)).toBe("ACCEPTED");
  });

  it("refuses an input with no rentalId", async () => {
    const { who, id } = await acceptedIn(3);

    for (const data of [{}, { rentalId: " " }, { rentalId: 42 }]) {
      await expect(cancelRental.run(asUser(who.borrower, data))).rejects.toMatchObject({
        code: "invalid-argument",
      });
    }
    expect(await status(id)).toBe("ACCEPTED");
  });

  it("is a callable in the app's region", () => {
    expect(cancelRental.__endpoint.region).toEqual(["europe-west1"]);
    expect(cancelRental.__endpoint.callableTrigger).toBeDefined();
  });
});
