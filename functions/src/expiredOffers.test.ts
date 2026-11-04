// Made with Claude.

import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { DocumentReference, Firestore, Timestamp, getFirestore } from "firebase-admin/firestore";
import { expireOffer, expireOverdueOffers } from "./expiredOffers";

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

// Long before every date of the other test files, which share the emulator and run at the same
// time: a sweep at NOW never reaches their offers.
const NOW = new Date("2001-06-15T10:00:00Z");
const EXPIRED = new Date("2001-06-15T09:00:00Z");
const LIVE = new Date("2001-06-15T11:00:00Z");

/** A rental with an offer expiring at [expiresAt], and the offer's addresses. */
async function rentalWithOffer(expiresAt: Date, status = "REQUESTED"): Promise<DocumentReference> {
  const ref = db.collection("rentals").doc(randomUUID());
  await ref.set({
    status,
    offer: { amount: 180, authorId: "lender", isCounter: false, expiresAt: Timestamp.fromDate(expiresAt) },
  });
  await ref.collection("private").doc("addresses").set({ pickup: "A", dropOff: "B" });
  return ref;
}

async function addresses(ref: DocumentReference) {
  return (await ref.collection("private").doc("addresses").get()).data();
}

describe("expireOverdueOffers", () => {
  it("declines an expired offer, removes it and its addresses, and tells the chat", async () => {
    const ref = await rentalWithOffer(EXPIRED);

    const result = await expireOverdueOffers(db, NOW);

    expect(result.expired).toBeGreaterThanOrEqual(1);
    expect(result.failed).toBe(0);
    const data = (await ref.get()).data();
    expect(data).toMatchObject({
      status: "DECLINED",
      lastMessage: "Offer expired · request declined",
    });
    expect(data?.offer).toBeUndefined();
    expect(await addresses(ref)).toBeUndefined();
    const messages = await ref.collection("messages").get();
    expect(messages.docs.map((doc) => doc.get("type"))).toEqual(["SYSTEM"]);
  });

  it("expires an offer exactly at its expiry", async () => {
    const ref = await rentalWithOffer(NOW);

    await expireOverdueOffers(db, NOW);

    expect((await ref.get()).get("status")).toBe("DECLINED");
  });

  it("leaves an offer that has not expired alone", async () => {
    const ref = await rentalWithOffer(LIVE);

    await expireOverdueOffers(db, NOW);

    expect((await ref.get()).get("status")).toBe("REQUESTED");
    expect(await addresses(ref)).toBeDefined();
  });
});

describe("expireOffer", () => {
  it("declines an expired offer", async () => {
    const ref = await rentalWithOffer(EXPIRED);

    expect(await expireOffer(ref, NOW)).toBe(true);
    expect((await ref.get()).get("status")).toBe("DECLINED");
  });

  it("does nothing when the offer was answered after the query", async () => {
    const ref = await rentalWithOffer(EXPIRED, "ACCEPTED");

    expect(await expireOffer(ref, NOW)).toBe(false);
    expect((await ref.get()).get("status")).toBe("ACCEPTED");
  });

  it("does nothing when a counteroffer replaced the offer after the query", async () => {
    const ref = await rentalWithOffer(LIVE);

    expect(await expireOffer(ref, NOW)).toBe(false);
    expect((await ref.get()).get("status")).toBe("REQUESTED");
  });

  it("does nothing when the rental has no offer or does not exist", async () => {
    const noOffer = db.collection("rentals").doc(randomUUID());
    await noOffer.set({ status: "REQUESTED" });

    expect(await expireOffer(noOffer, NOW)).toBe(false);
    expect(await expireOffer(db.collection("rentals").doc(randomUUID()), NOW)).toBe(false);
    expect((await noOffer.get()).get("status")).toBe("REQUESTED");
  });
});
