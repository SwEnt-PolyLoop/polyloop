// Made with Claude.

import { randomUUID } from "node:crypto";
import { Firestore, getFirestore } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { declineRequest, expireOffers, makeOffer, respondToOffer } from "./index";

// Firebase loads this ES module to verify tokens, which Jest cannot load; `run` skips that step.
jest.mock("jose", () => ({}));

// The functions as index.ts exports them to Firebase, called with what the app sends: their sign-in
// check, their input check and their error codes. negotiation.test.ts covers the rules themselves.
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

const LENDER = "callable-lender";
const BORROWER = "callable-borrower";
const OTHER_BORROWER = "callable-other-borrower";
const ADDRESSES = { pickupAddress: "Route Cantonale 1", returnAddress: "Rue de Lausanne 10" };

/** The request of the signed-in, verified EPFL user [uid], sending [data]. */
function asUser(uid: string, data: object, email = `${uid}@epfl.ch`, verified = true) {
  return { auth: { uid, token: { email, email_verified: verified } }, data } as never;
}

async function listing(): Promise<string> {
  const id = randomUUID();
  await db.collection("listings").doc(id).set({ ownerId: LENDER, bookedRanges: [] });
  return id;
}

/** A request on [listingId], far enough in the future that an offer on it stays valid. */
async function request(
  listingId: string,
  start = "2099-01-10",
  end = "2099-01-12",
  borrowerId = BORROWER,
): Promise<string> {
  const id = randomUUID();
  await db.collection("rentals").doc(id).set({
    listingId,
    ownerId: LENDER,
    borrowerId,
    startDate: start,
    endDate: end,
    status: "REQUESTED",
    pickupAddress: "",
    returnAddress: "",
  });
  return id;
}

async function rental(id: string) {
  return (await db.collection("rentals").doc(id).get()).data();
}

describe("deployment", () => {
  it("runs every negotiation function in the app's region", () => {
    for (const fn of [makeOffer, respondToOffer, declineRequest, expireOffers]) {
      expect(fn.__endpoint.region).toEqual(["europe-west1"]);
    }
  });

  it("exposes the callables to the app, and expireOffers on a schedule", () => {
    for (const fn of [makeOffer, respondToOffer, declineRequest]) {
      expect(fn.__endpoint.callableTrigger).toBeDefined();
    }
    expect(expireOffers.__endpoint.scheduleTrigger).toBeDefined();
  });
});

describe("who can call", () => {
  it("refuses a caller who is not signed in", async () => {
    await expect(makeOffer.run({ data: {} } as never)).rejects.toMatchObject({
      code: "unauthenticated",
    });
  });

  it("refuses an address outside EPFL and an unverified one, writing nothing", async () => {
    const id = await request(await listing());
    const data = { rentalId: id, deposit: 100, ...ADDRESSES };

    await expect(makeOffer.run(asUser(LENDER, data, "lender@gmail.com"))).rejects.toMatchObject({
      code: "permission-denied",
    });
    await expect(
      makeOffer.run(asUser(LENDER, data, "lender@epfl.ch", false)),
    ).rejects.toMatchObject({ code: "permission-denied" });
    expect((await rental(id))?.offer).toBeUndefined();
  });

  it("gives the app an HttpsError with the refusal's code and message", async () => {
    const id = await request(await listing());

    const error = await declineRequest.run(asUser(BORROWER, { rentalId: id })).catch((e) => e);

    expect(error).toBeInstanceOf(HttpsError);
    expect(error).toMatchObject({
      code: "permission-denied",
      message: "Only the lender can do this.",
    });
  });
});

describe("what can be sent", () => {
  it("refuses a bad deposit, a blank address and an unknown action", async () => {
    const id = await request(await listing());

    await expect(
      makeOffer.run(asUser(LENDER, { rentalId: id, ...ADDRESSES, deposit: 1.5 })),
    ).rejects.toMatchObject({ code: "invalid-argument" });
    await expect(
      makeOffer.run(asUser(LENDER, { rentalId: id, deposit: 100, ...ADDRESSES, pickupAddress: " " })),
    ).rejects.toMatchObject({ code: "invalid-argument" });
    await expect(
      respondToOffer.run(asUser(BORROWER, { rentalId: id, action: "MAYBE" })),
    ).rejects.toMatchObject({ code: "invalid-argument" });
  });
});

describe("the whole negotiation", () => {
  it("goes from the offer to the counteroffer to the accepted rental", async () => {
    const listingId = await listing();
    const id = await request(listingId);
    const overlapping = await request(listingId, "2099-01-13", "2099-01-14", OTHER_BORROWER);

    await makeOffer.run(asUser(LENDER, { rentalId: id, deposit: 200, ...ADDRESSES }));
    await respondToOffer.run(asUser(BORROWER, { rentalId: id, action: "COUNTER", deposit: 150 }));
    const result = await respondToOffer.run(asUser(LENDER, { rentalId: id, action: "ACCEPT" }));

    expect(result).toEqual({ declinedRentalIds: [overlapping] });
    const accepted = await rental(id);
    expect(accepted).toMatchObject({
      status: "ACCEPTED",
      deposit: 150,
      pickupAddress: ADDRESSES.pickupAddress,
      returnAddress: ADDRESSES.returnAddress,
    });
    expect(accepted?.offer).toBeUndefined();
    expect((await rental(overlapping))?.status).toBe("DECLINED");
    const messages = await db
      .collection("rentals")
      .doc(id)
      .collection("messages")
      .orderBy("sentAt")
      .get();
    expect(messages.docs.map((doc) => doc.get("text"))).toEqual([
      "Offer sent · 200 PP deposit",
      "Counteroffer · 150 PP",
      "Accepted · 150 PP deposit",
    ]);
  });

  it("lets the lender decline a request through declineRequest", async () => {
    const id = await request(await listing());

    await declineRequest.run(asUser(LENDER, { rentalId: id }));

    expect((await rental(id))?.status).toBe("DECLINED");
  });

  it("accepts only one of two overlapping offers answered at the same time", async () => {
    const listingId = await listing();
    const first = await request(listingId, "2099-02-10", "2099-02-12");
    const second = await request(listingId, "2099-02-11", "2099-02-13", OTHER_BORROWER);
    await makeOffer.run(asUser(LENDER, { rentalId: first, deposit: 50, ...ADDRESSES }));
    await makeOffer.run(asUser(LENDER, { rentalId: second, deposit: 60, ...ADDRESSES }));

    await Promise.allSettled([
      respondToOffer.run(asUser(BORROWER, { rentalId: first, action: "ACCEPT" })),
      respondToOffer.run(asUser(OTHER_BORROWER, { rentalId: second, action: "ACCEPT" })),
    ]);

    const statuses = [(await rental(first))?.status, (await rental(second))?.status].sort();
    expect(statuses).toEqual(["ACCEPTED", "DECLINED"]);
    const booked = (await db.collection("listings").doc(listingId).get()).get("bookedRanges");
    expect(booked).toHaveLength(1);
  });
});
