// Made with Claude.

import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { Firestore, getFirestore } from "firebase-admin/firestore";
import { declineRequest, makeOffer, respondToOffer } from "./negotiation";

// The transactions are tested on the emulator; the pure rules are in rentalRules.test.ts.
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
const STRANGER = "stranger-uid";
const NOW = new Date("2026-11-04T10:00:00Z");
const ONE_WEEK_LATER = new Date("2026-11-11T10:00:00Z");
const ADDRESSES = {
  pickupAddress: "Route Cantonale 1, Ecublens",
  returnAddress: "Rue de Lausanne 10, Renens",
};

/** A fresh listing with [bookedRanges] (accepted rentals only), owned by LENDER. */
async function listing(bookedRanges: object[] = []): Promise<string> {
  const id = randomUUID();
  await db.collection("listings").doc(id).set({ ownerId: LENDER, bookedRanges });
  return id;
}

/** A request on [listingId] for [start] to [end], as the app writes it. */
async function request(
  listingId: string,
  start = "2026-12-01",
  end = "2026-12-03",
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
    pickupAddress: "",
    returnAddress: "",
  });
  return id;
}

/** A request with the lender's offer of 180 PP already sent. */
async function offered(listingId: string, start?: string, end?: string): Promise<string> {
  const id = await request(listingId, start, end);
  await makeOffer(db, LENDER, { rentalId: id, deposit: 180, ...ADDRESSES }, NOW);
  return id;
}

/** A request with the lender's offer and the borrower's counteroffer of 120 PP. */
async function countered(listingId: string): Promise<string> {
  const id = await offered(listingId);
  await respondToOffer(db, BORROWER, { rentalId: id, action: "COUNTER", deposit: 120 }, NOW);
  return id;
}

async function rental(id: string) {
  return (await db.collection("rentals").doc(id).get()).data();
}

async function addresses(id: string) {
  return (await db.doc(`rentals/${id}/private/addresses`).get()).data();
}

/** The chat's messages, oldest first, all of which must be SYSTEM messages. */
async function systemMessages(id: string): Promise<string[]> {
  const snap = await db.collection(`rentals/${id}/messages`).orderBy("sentAt").get();
  expect(snap.docs.every((doc) => doc.get("type") === "SYSTEM")).toBe(true);
  return snap.docs.map((doc) => doc.get("text"));
}

describe("makeOffer", () => {
  it("stores the offer on the rental and the addresses out of the borrower's reach", async () => {
    const id = await request(await listing());

    await makeOffer(db, LENDER, { rentalId: id, deposit: 180, ...ADDRESSES }, NOW);

    const data = await rental(id);
    expect(data).toMatchObject({
      status: "REQUESTED",
      pickupAddress: "",
      returnAddress: "",
      lastMessage: "Offer sent · 180 PP deposit",
    });
    expect(data?.offer).toMatchObject({ amount: 180, authorId: LENDER, isCounter: false });
    expect(data?.offer.expiresAt.toDate()).toEqual(ONE_WEEK_LATER);
    expect(await addresses(id)).toEqual({
      pickup: ADDRESSES.pickupAddress,
      dropOff: ADDRESSES.returnAddress,
    });
    expect(await systemMessages(id)).toEqual(["Offer sent · 180 PP deposit"]);
  });

  it("expires on the rental's first day when that comes before one week", async () => {
    const id = await request(await listing(), "2026-11-08", "2026-11-09");

    await makeOffer(db, LENDER, { rentalId: id, deposit: 180, ...ADDRESSES }, NOW);

    expect((await rental(id))?.offer.expiresAt.toDate()).toEqual(new Date("2026-11-08T00:00:00Z"));
  });

  it("refuses a rental whose first day has come, and writes nothing", async () => {
    const id = await request(await listing(), "2026-11-04", "2026-11-05");

    await expect(
      makeOffer(db, LENDER, { rentalId: id, deposit: 180, ...ADDRESSES }, NOW),
    ).rejects.toMatchObject({ code: "failed-precondition" });
    expect((await rental(id))?.offer).toBeUndefined();
    expect(await addresses(id)).toBeUndefined();
  });

  it("refuses the borrower", async () => {
    const id = await request(await listing());

    await expect(
      makeOffer(db, BORROWER, { rentalId: id, deposit: 180, ...ADDRESSES }, NOW),
    ).rejects.toMatchObject({ code: "permission-denied" });
    expect((await rental(id))?.offer).toBeUndefined();
  });

  it("refuses a second offer, keeping the first", async () => {
    const id = await offered(await listing());

    await expect(
      makeOffer(db, LENDER, { rentalId: id, deposit: 50, ...ADDRESSES }, NOW),
    ).rejects.toMatchObject({ code: "failed-precondition" });
    expect((await rental(id))?.offer.amount).toBe(180);
  });

  it("refuses a rental that is not requested, or a missing one", async () => {
    const declined = await request(await listing(), "2026-12-01", "2026-12-02", "DECLINED");

    await expect(
      makeOffer(db, LENDER, { rentalId: declined, deposit: 180, ...ADDRESSES }, NOW),
    ).rejects.toMatchObject({ code: "failed-precondition" });
    await expect(
      makeOffer(db, LENDER, { rentalId: randomUUID(), deposit: 180, ...ADDRESSES }, NOW),
    ).rejects.toMatchObject({ code: "not-found" });
  });
});

describe("respondToOffer: accepting", () => {
  it("makes the rental accepted, with the deposit and addresses, and books the dates", async () => {
    const listingId = await listing();
    const id = await offered(listingId);

    const result = await respondToOffer(db, BORROWER, { rentalId: id, action: "ACCEPT" }, NOW);

    expect(result.declinedRentalIds).toEqual([]);
    const data = await rental(id);
    expect(data).toMatchObject({
      status: "ACCEPTED",
      deposit: 180,
      ...ADDRESSES,
      lastMessage: "Accepted · 180 PP deposit",
    });
    expect(data?.offer).toBeUndefined();
    expect(await addresses(id)).toBeUndefined();
    const listingData = (await db.collection("listings").doc(listingId).get()).data();
    expect(listingData?.bookedRanges).toEqual([{ start: "2026-12-01", end: "2026-12-03" }]);
  });

  it("lets the lender accept a counteroffer, at the countered amount", async () => {
    const id = await countered(await listing());

    await respondToOffer(db, LENDER, { rentalId: id, action: "ACCEPT" }, NOW);

    expect(await rental(id)).toMatchObject({ status: "ACCEPTED", deposit: 120 });
    expect(await systemMessages(id)).toEqual([
      "Offer sent · 180 PP deposit",
      "Counteroffer · 120 PP",
      "Accepted · 120 PP deposit",
    ]);
  });

  it("declines overlapping requests, buffer day included, with or without an offer", async () => {
    const listingId = await listing();
    const accepted = await offered(listingId, "2026-12-10", "2026-12-12");
    const touching = await request(listingId, "2026-12-13", "2026-12-14");
    const beforeBuffer = await offered(listingId, "2026-12-08", "2026-12-09");
    const free = await request(listingId, "2026-12-14", "2026-12-15");

    const result = await respondToOffer(
      db,
      BORROWER,
      { rentalId: accepted, action: "ACCEPT" },
      NOW,
    );

    expect(result.declinedRentalIds.sort()).toEqual([touching, beforeBuffer].sort());
    expect((await rental(touching))?.status).toBe("DECLINED");
    expect(await rental(beforeBuffer)).toMatchObject({
      status: "DECLINED",
      lastMessage: "Dates booked · request declined",
    });
    expect((await rental(beforeBuffer))?.offer).toBeUndefined();
    expect(await addresses(beforeBuffer)).toBeUndefined();
    expect((await rental(free))?.status).toBe("REQUESTED");
  });

  it("leaves requests on other listings alone", async () => {
    const id = await offered(await listing());
    const elsewhere = await request(await listing());

    await respondToOffer(db, BORROWER, { rentalId: id, action: "ACCEPT" }, NOW);

    expect((await rental(elsewhere))?.status).toBe("REQUESTED");
  });

  it("refuses dates already booked, and writes nothing", async () => {
    const id = await offered(await listing([{ start: "2026-12-04", end: "2026-12-05" }]));

    await expect(
      respondToOffer(db, BORROWER, { rentalId: id, action: "ACCEPT" }, NOW),
    ).rejects.toMatchObject({ code: "failed-precondition" });
    expect((await rental(id))?.status).toBe("REQUESTED");
  });

  it("refuses an offer at its expiry, even if expireOffers has not run yet", async () => {
    const id = await offered(await listing());

    await expect(
      respondToOffer(db, BORROWER, { rentalId: id, action: "ACCEPT" }, ONE_WEEK_LATER),
    ).rejects.toMatchObject({ code: "failed-precondition" });
    expect((await rental(id))?.status).toBe("REQUESTED");
  });

  it("refuses an offer that expired on the rental's first day", async () => {
    const id = await offered(await listing(), "2026-11-08", "2026-11-09");

    await expect(
      respondToOffer(
        db,
        BORROWER,
        { rentalId: id, action: "ACCEPT" },
        new Date("2026-11-08T00:00:00Z"),
      ),
    ).rejects.toMatchObject({ code: "failed-precondition" });
  });
});

describe("respondToOffer: declining", () => {
  it("makes the rental declined and removes the offer and the addresses", async () => {
    const id = await offered(await listing());

    await respondToOffer(db, BORROWER, { rentalId: id, action: "DECLINE" }, NOW);

    const data = await rental(id);
    expect(data).toMatchObject({
      status: "DECLINED",
      pickupAddress: "",
      lastMessage: "Offer declined",
    });
    expect(data?.offer).toBeUndefined();
    expect(await addresses(id)).toBeUndefined();
  });

  it("lets the lender decline a counteroffer", async () => {
    const id = await countered(await listing());

    await respondToOffer(db, LENDER, { rentalId: id, action: "DECLINE" }, NOW);

    expect((await rental(id))?.status).toBe("DECLINED");
  });
});

describe("respondToOffer: countering", () => {
  it("replaces the offer with the borrower's, restarting the week", async () => {
    const id = await offered(await listing());
    const later = new Date("2026-11-06T10:00:00Z");

    await respondToOffer(db, BORROWER, { rentalId: id, action: "COUNTER", deposit: 120 }, later);

    const data = await rental(id);
    expect(data).toMatchObject({ status: "REQUESTED", lastMessage: "Counteroffer · 120 PP" });
    expect(data?.offer).toMatchObject({
      amount: 120,
      previousAmount: 180,
      authorId: BORROWER,
      isCounter: true,
    });
    expect(data?.offer.expiresAt.toDate()).toEqual(new Date("2026-11-13T10:00:00Z"));
    expect(await addresses(id)).toBeDefined();
  });

  it("refuses a further round", async () => {
    const id = await countered(await listing());

    await expect(
      respondToOffer(db, LENDER, { rentalId: id, action: "COUNTER", deposit: 150 }, NOW),
    ).rejects.toMatchObject({ code: "failed-precondition" });
    expect((await rental(id))?.offer.amount).toBe(120);
  });
});

describe("respondToOffer: who and when", () => {
  it("refuses the offer's author, on the first offer and on a counteroffer", async () => {
    const first = await offered(await listing());
    const counter = await countered(await listing());

    await expect(
      respondToOffer(db, LENDER, { rentalId: first, action: "ACCEPT" }, NOW),
    ).rejects.toMatchObject({ code: "permission-denied" });
    await expect(
      respondToOffer(db, BORROWER, { rentalId: counter, action: "ACCEPT" }, NOW),
    ).rejects.toMatchObject({ code: "permission-denied" });
  });

  it("refuses someone who takes no part in the rental", async () => {
    const id = await offered(await listing());

    await expect(
      respondToOffer(db, STRANGER, { rentalId: id, action: "DECLINE" }, NOW),
    ).rejects.toMatchObject({ code: "permission-denied" });
  });

  it("refuses a request without an offer, an answered offer, and a missing rental", async () => {
    const noOffer = await request(await listing());
    const answered = await offered(await listing());
    await respondToOffer(db, BORROWER, { rentalId: answered, action: "DECLINE" }, NOW);

    await expect(
      respondToOffer(db, BORROWER, { rentalId: noOffer, action: "ACCEPT" }, NOW),
    ).rejects.toMatchObject({ code: "failed-precondition" });
    await expect(
      respondToOffer(db, BORROWER, { rentalId: answered, action: "ACCEPT" }, NOW),
    ).rejects.toMatchObject({ code: "failed-precondition" });
    await expect(
      respondToOffer(db, BORROWER, { rentalId: randomUUID(), action: "ACCEPT" }, NOW),
    ).rejects.toMatchObject({ code: "not-found" });
  });
});

describe("declineRequest", () => {
  it("declines a request without an offer", async () => {
    const id = await request(await listing());

    await declineRequest(db, LENDER, id);

    expect(await rental(id)).toMatchObject({
      status: "DECLINED",
      lastMessage: "Request declined",
    });
    expect(await systemMessages(id)).toEqual(["Request declined"]);
  });

  it("refuses the borrower", async () => {
    const id = await request(await listing());

    await expect(declineRequest(db, BORROWER, id)).rejects.toMatchObject({
      code: "permission-denied",
    });
    expect((await rental(id))?.status).toBe("REQUESTED");
  });

  it("refuses a request with an offer waiting", async () => {
    const id = await offered(await listing());

    await expect(declineRequest(db, LENDER, id)).rejects.toMatchObject({
      code: "failed-precondition",
    });
    expect((await rental(id))?.status).toBe("REQUESTED");
  });

  it("refuses a rental that is not requested", async () => {
    const id = await request(await listing(), "2026-12-01", "2026-12-02", "ACCEPTED");

    await expect(declineRequest(db, LENDER, id)).rejects.toMatchObject({
      code: "failed-precondition",
    });
  });
});
