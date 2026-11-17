// Made with Claude.

import { getApps, initializeApp } from "firebase-admin/app";
import { Firestore, getFirestore } from "firebase-admin/firestore";
import { confirmPickup } from "./handover";
import { hasSeenLastMessage } from "./testing/readTimes";
import { People, people, rental as newRental } from "./testing/walletFixtures";

// payLender.ts loads firebase-functions, which loads an ES module that Jest cannot load.
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

// 10:00 in Zurich on the pickup day.
const NOW = new Date("2026-12-01T09:00:00Z");
const PICKUP_DAY = "2026-12-01";
const CODE = "Zk3_q-9AbCdEfGhIjKlMnOpQrStUvWxYz0123456789";

/** An ACCEPTED rental starting today, overridden by [fields], with its pickup code. */
async function accepted(who: People, fields: object = {}): Promise<string> {
  const id = await newRental(who, "ACCEPTED", {
    startDate: PICKUP_DAY,
    endDate: "2026-12-03",
    pickupCodeHash: "fingerprint",
    ...fields,
  });
  await db.doc(`rentals/${id}/handoverCodes/PICKUP`).set({ code: CODE });
  return id;
}

const scan = (rentalId: string, code = CODE) => ({ rentalId, code });

const rentalData = async (id: string) => (await db.doc(`rentals/${id}`).get()).data();
const codeExists = async (id: string) =>
  (await db.doc(`rentals/${id}/handoverCodes/PICKUP`).get()).exists;
const messages = async (id: string) =>
  (await db.collection(`rentals/${id}/messages`).get()).docs.map((doc) => doc.data());

describe("confirming the pickup", () => {
  it("makes the rental active, says so in the chat and uses the code up", async () => {
    const who = people();
    const id = await accepted(who);

    await confirmPickup(db, who.borrower, scan(id), NOW);

    const data = await rentalData(id);
    expect(data?.status).toBe("ACTIVE");
    expect(data?.pickedUpAt.toMillis()).toBeGreaterThan(0);
    expect(data?.pickupCodeHash).toBeUndefined();
    expect(data?.lastMessage).toBe("Picked up · rental started");
    expect(await codeExists(id)).toBe(false);
    expect(await messages(id)).toEqual([
      expect.objectContaining({
        text: "Picked up · rental started",
        type: "SYSTEM",
        senderId: "",
      }),
    ]);
    // The borrower scanned: no red dot for them; the lender has news.
    expect(hasSeenLastMessage(data, who.borrower)).toBe(true);
    expect(hasSeenLastMessage(data, who.lender)).toBe(false);
  });

  it("is on the pickup day in Zurich, even when it is still the day before in UTC", async () => {
    const who = people();
    const id = await accepted(who);

    // 00:30 in Zurich on 1 December is 23:30 UTC on 30 November.
    await confirmPickup(db, who.borrower, scan(id), new Date("2026-11-30T23:30:00Z"));

    expect((await rentalData(id))?.status).toBe("ACTIVE");
  });

  it("succeeds and changes nothing once the rental was picked up", async () => {
    const who = people();
    for (const status of ["ACTIVE", "RETURNED", "DISPUTED", "CLOSED"]) {
      const id = await newRental(who, status, { startDate: PICKUP_DAY });

      // Even with no code left, or a wrong one: the pickup already happened.
      await confirmPickup(db, who.borrower, scan(id, "anything"), NOW);

      expect((await rentalData(id))?.status).toBe(status);
      expect(await messages(id)).toEqual([]);
    }
  });
});
