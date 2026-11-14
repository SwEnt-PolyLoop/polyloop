// Made with Claude.

import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { DocumentReference, Firestore, Timestamp, getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import {
  expireOffer,
  expireOverdueOffers,
  expireRequest,
  expireUnansweredRequests,
  makeOffer,
  respondToOffer,
} from "./negotiation";
import { hasSeenLastMessage } from "./testing/readTimes";
import { fund, people, wallet } from "./testing/walletFixtures";

jest.mock("firebase-functions/logger");

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

// Long before every date of the other test files, which share the emulator and leave their rentals
// there: a sweep at NOW never reaches their offers. Rentals of this file are deleted after each
// test, so that a sweep finds only those of its own test and its counts are exact.
const BEFORE_EXPIRY = new Date("2001-06-15T08:00:00Z");
const EXPIRED = new Date("2001-06-15T09:00:00Z");
const NOW = new Date("2001-06-15T10:00:00Z");
const LIVE = new Date("2001-06-15T11:00:00Z");
const LENDER = "expiry-lender";

const created: DocumentReference[] = [];

afterEach(async () => {
  await Promise.all(created.splice(0).map((ref) => ref.delete()));
  jest.clearAllMocks();
});

/** A rental with an offer expiring at [expiresAt], and the offer's addresses. */
async function rentalWithOffer(
  expiresAt: Date,
  fields: object = { status: "REQUESTED" },
): Promise<DocumentReference> {
  const ref = db.collection("rentals").doc(randomUUID());
  created.push(ref);
  await ref.set({
    ...fields,
    offer: {
      amount: 180,
      authorId: LENDER,
      isCounter: false,
      expiresAt: Timestamp.fromDate(expiresAt),
    },
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

    expect(await expireOverdueOffers(db, NOW)).toEqual({ expired: 1, failed: 0 });

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

  it("leaves the expiry unseen by both participants, who both read the chat before", async () => {
    const { borrower } = people();
    const read = { [LENDER]: Timestamp.fromDate(NOW), [borrower]: Timestamp.fromDate(NOW) };
    const ref = await rentalWithOffer(EXPIRED, { status: "REQUESTED", lastReadAt: read });

    await expireOverdueOffers(db, NOW);

    const data = (await ref.get()).data();
    expect(hasSeenLastMessage(data, LENDER)).toBe(false);
    expect(hasSeenLastMessage(data, borrower)).toBe(false);
  });

  it("expires an offer exactly at its expiry", async () => {
    const ref = await rentalWithOffer(NOW);

    expect(await expireOverdueOffers(db, NOW)).toEqual({ expired: 1, failed: 0 });

    expect((await ref.get()).get("status")).toBe("DECLINED");
  });

  it("leaves an offer that has not expired alone", async () => {
    const ref = await rentalWithOffer(LIVE);

    expect(await expireOverdueOffers(db, NOW)).toEqual({ expired: 0, failed: 0 });

    expect((await ref.get()).get("status")).toBe("REQUESTED");
    expect(await addresses(ref)).toBeDefined();
  });

  it("logs a failure and goes on, counting only the rentals really expired", async () => {
    const failing = await rentalWithOffer(EXPIRED);
    const answeredMeanwhile = await rentalWithOffer(EXPIRED);
    const expiring = await rentalWithOffer(EXPIRED);
    const error = new Error("Firestore is unavailable");

    const result = await expireOverdueOffers(db, NOW, async (ref, now) => {
      if (ref.id === failing.id) throw error;
      if (ref.id === answeredMeanwhile.id) return false;
      return expireOffer(ref, now);
    });

    expect(result).toEqual({ expired: 1, failed: 1 });
    expect((await expiring.get()).get("status")).toBe("DECLINED");
    expect((await failing.get()).get("status")).toBe("REQUESTED");
    expect(logger.error).toHaveBeenCalledTimes(1);
    expect(logger.error).toHaveBeenCalledWith(
      `Could not expire the offer of rental ${failing.id}`,
      error,
    );
  });
});

describe("expireOffer", () => {
  it("declines an expired offer", async () => {
    const ref = await rentalWithOffer(EXPIRED);

    expect(await expireOffer(ref, NOW)).toBe(true);
    expect((await ref.get()).get("status")).toBe("DECLINED");
  });

  it("does nothing when a counteroffer replaced the offer after the query", async () => {
    const ref = await rentalWithOffer(LIVE);

    expect(await expireOffer(ref, NOW)).toBe(false);
    expect((await ref.get()).get("status")).toBe("REQUESTED");
  });

  it("does nothing when the offer was answered, or the rental does not exist", async () => {
    const answered = db.collection("rentals").doc(randomUUID());
    created.push(answered);
    await answered.set({ status: "ACCEPTED" });

    expect(await expireOffer(answered, NOW)).toBe(false);
    expect(await expireOffer(db.collection("rentals").doc(randomUUID()), NOW)).toBe(false);
    expect((await answered.get()).get("status")).toBe("ACCEPTED");
  });

  it("does nothing to a request with no offer", async () => {
    const noOffer = db.collection("rentals").doc(randomUUID());
    created.push(noOffer);
    await noOffer.set({ status: "REQUESTED" });

    expect(await expireOffer(noOffer, NOW)).toBe(false);
    expect((await noOffer.get()).get("status")).toBe("REQUESTED");
  });
});

describe("an answer racing the expiry", () => {
  it("lets exactly one win: accepted and booked, or declined and not booked", async () => {
    const listingRef = db.collection("listings").doc(randomUUID());
    await listingRef.set({ ownerId: LENDER, bookedRanges: [] });
    // Funded, so that the answer can win: the acceptance holds the deposit.
    const { borrower } = people();
    await fund(borrower, { available: 500, heldAsDeposits: 0, heldForRentals: 0 });
    const ref = await rentalWithOffer(EXPIRED, {
      status: "REQUESTED",
      listingId: listingRef.id,
      ownerId: LENDER,
      borrowerId: borrower,
      startDate: "2001-07-01",
      endDate: "2001-07-02",
    });

    const [answer, sweep] = await Promise.allSettled([
      // The borrower's answer left before the expiry; the sweep runs after it.
      respondToOffer(db, borrower, { rentalId: ref.id, action: "ACCEPT" }, BEFORE_EXPIRY),
      expireOffer(ref, NOW),
    ]);

    const status = (await ref.get()).get("status");
    const booked = (await listingRef.get()).get("bookedRanges");
    const messages = await ref.collection("messages").get();
    expect(messages.size).toBe(1);
    expect(await addresses(ref)).toBeUndefined();
    if (status === "ACCEPTED") {
      expect((await wallet(borrower))?.heldAsDeposits).toBe(180);
      expect(answer.status).toBe("fulfilled");
      expect(sweep).toMatchObject({ status: "fulfilled", value: false });
      expect(booked).toEqual([{ start: "2001-07-01", end: "2001-07-02" }]);
    } else {
      expect(status).toBe("DECLINED");
      expect(await wallet(borrower)).toEqual({
        available: 500,
        heldAsDeposits: 0,
        heldForRentals: 0,
      });
      expect(answer).toMatchObject({
        status: "rejected",
        reason: { code: "failed-precondition" },
      });
      expect(sweep).toMatchObject({ status: "fulfilled", value: true });
      expect(booked).toEqual([]);
    }
    await listingRef.delete();
  });
});

// NOW is on 2001-06-15, so these requests start before, on and after NOW's day.
const TOMORROW = "2001-06-16";
const TODAY = "2001-06-15";
const LAST_WEEK = "2001-06-08";

/** A request with no offer, starting on [startDate]. */
async function requestStarting(
  startDate: unknown,
  fields: object = { status: "REQUESTED" },
): Promise<DocumentReference> {
  const ref = db.collection("rentals").doc(randomUUID());
  created.push(ref);
  await ref.set({ ...fields, startDate, endDate: "2001-06-20" });
  return ref;
}

describe("expireUnansweredRequests", () => {
  it("declines a request with no offer on its first day, and tells the chat", async () => {
    const ref = await requestStarting(TODAY);

    expect(await expireUnansweredRequests(db, NOW)).toEqual({ expired: 1, failed: 0 });

    const data = (await ref.get()).data();
    expect(data).toMatchObject({
      status: "DECLINED",
      lastMessage: "Request expired · no offer sent",
    });
    const messages = await ref.collection("messages").get();
    expect(messages.docs.map((doc) => doc.get("type"))).toEqual(["SYSTEM"]);
  });

  it("declines it from the very start of its first day", async () => {
    const ref = await requestStarting(TODAY);

    const midnight = new Date(`${TODAY}T00:00:00Z`);
    expect(await expireUnansweredRequests(db, midnight)).toEqual({ expired: 1, failed: 0 });

    expect((await ref.get()).get("status")).toBe("DECLINED");
  });

  it("declines a request whose first day has passed", async () => {
    const ref = await requestStarting(LAST_WEEK);

    expect(await expireUnansweredRequests(db, NOW)).toEqual({ expired: 1, failed: 0 });

    expect((await ref.get()).get("status")).toBe("DECLINED");
  });

  it("leaves a request whose first day has not come alone", async () => {
    const ref = await requestStarting(TOMORROW);

    expect(await expireUnansweredRequests(db, NOW)).toEqual({ expired: 0, failed: 0 });

    expect((await ref.get()).get("status")).toBe("REQUESTED");
  });

  it("leaves the expiry unseen by both participants, who both read the chat before", async () => {
    const { borrower } = people();
    const read = { [LENDER]: Timestamp.fromDate(NOW), [borrower]: Timestamp.fromDate(NOW) };
    const ref = await requestStarting(TODAY, { status: "REQUESTED", lastReadAt: read });

    await expireUnansweredRequests(db, NOW);

    const data = (await ref.get()).data();
    expect(hasSeenLastMessage(data, LENDER)).toBe(false);
    expect(hasSeenLastMessage(data, borrower)).toBe(false);
  });

  it("leaves a request with an offer to the offer's own expiry", async () => {
    const ref = await rentalWithOffer(LIVE, { status: "REQUESTED", startDate: TODAY });

    expect(await expireUnansweredRequests(db, NOW)).toEqual({ expired: 0, failed: 0 });

    expect((await ref.get()).get("status")).toBe("REQUESTED");
    expect(await addresses(ref)).toBeDefined();
  });

  it("leaves answered rentals and requests with invalid dates alone", async () => {
    const accepted = await requestStarting(LAST_WEEK, { status: "ACCEPTED" });
    const noDate = await requestStarting(null);
    const badDate = await requestStarting("2001-02-30");

    expect(await expireUnansweredRequests(db, NOW)).toEqual({ expired: 0, failed: 0 });

    expect((await accepted.get()).get("status")).toBe("ACCEPTED");
    expect((await noDate.get()).get("status")).toBe("REQUESTED");
    expect((await badDate.get()).get("status")).toBe("REQUESTED");
  });

  it("logs a failure and goes on, counting only the requests really expired", async () => {
    const failing = await requestStarting(TODAY);
    const answeredMeanwhile = await requestStarting(TODAY);
    const expiring = await requestStarting(TODAY);
    const error = new Error("Firestore is unavailable");

    const result = await expireUnansweredRequests(db, NOW, async (ref, now) => {
      if (ref.id === failing.id) throw error;
      if (ref.id === answeredMeanwhile.id) return false;
      return expireRequest(ref, now);
    });

    expect(result).toEqual({ expired: 1, failed: 1 });
    expect((await expiring.get()).get("status")).toBe("DECLINED");
    expect((await failing.get()).get("status")).toBe("REQUESTED");
    expect(logger.error).toHaveBeenCalledTimes(1);
    expect(logger.error).toHaveBeenCalledWith(`Could not expire the request ${failing.id}`, error);
  });
});

describe("expireRequest", () => {
  it("does nothing when an offer was made after the query", async () => {
    const ref = await rentalWithOffer(LIVE, { status: "REQUESTED", startDate: TODAY });

    expect(await expireRequest(ref, NOW)).toBe(false);
    expect((await ref.get()).get("status")).toBe("REQUESTED");
  });

  it("does nothing when the request was declined, or the rental does not exist", async () => {
    const declined = await requestStarting(TODAY, { status: "DECLINED" });

    expect(await expireRequest(declined, NOW)).toBe(false);
    expect(await expireRequest(db.collection("rentals").doc(randomUUID()), NOW)).toBe(false);
    expect((await declined.get()).get("lastMessage")).toBeUndefined();
  });
});

describe("an offer racing the request's expiry", () => {
  it("lets exactly one win: the offer is sent, or the request is declined", async () => {
    const listingRef = db.collection("listings").doc(randomUUID());
    await listingRef.set({ ownerId: LENDER, bookedRanges: [] });
    const { borrower } = people();
    const ref = await requestStarting(TODAY, {
      status: "REQUESTED",
      listingId: listingRef.id,
      ownerId: LENDER,
      borrowerId: borrower,
    });
    const offer = { rentalId: ref.id, deposit: 180, pickupAddress: "A", returnAddress: "B" };

    const [sent, sweep] = await Promise.allSettled([
      // The lender's offer left the day before; the sweep runs on the first day.
      makeOffer(db, LENDER, offer, new Date("2001-06-14T20:00:00Z")),
      expireRequest(ref, NOW),
    ]);

    const data = (await ref.get()).data();
    const messages = await ref.collection("messages").get();
    expect(messages.size).toBe(1);
    if (data?.status === "REQUESTED") {
      expect(data.offer.amount).toBe(180);
      expect(await addresses(ref)).toEqual({ pickup: "A", dropOff: "B" });
      expect(sent.status).toBe("fulfilled");
      expect(sweep).toMatchObject({ status: "fulfilled", value: false });
    } else {
      expect(data?.status).toBe("DECLINED");
      expect(data?.offer).toBeUndefined();
      expect(await addresses(ref)).toBeUndefined();
      expect(sent).toMatchObject({ status: "rejected", reason: { code: "failed-precondition" } });
      expect(sweep).toMatchObject({ status: "fulfilled", value: true });
    }
    await listingRef.delete();
  });
});
