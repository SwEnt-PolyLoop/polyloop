// Made with Claude.

import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { Firestore, getFirestore } from "firebase-admin/firestore";
import { declineRequest, makeOffer, respondToOffer } from "./negotiation";
import { reserveRentalDeposit } from "./reserveDeposit";
import { fund, people, rows, wallet } from "./testing/walletFixtures";

// reserveDeposit.ts loads firebase-functions, which loads an ES module that Jest cannot load.
jest.mock("jose", () => ({}));

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

/** A wallet with [available] PP and nothing held. */
const funds = (available: number) => ({ available, heldAsDeposits: 0, heldForRentals: 0 });

/** The borrower of the rental [id]. */
async function borrowerOf(id: string): Promise<string> {
  return (await rental(id))?.borrowerId;
}

/**
 * A request on [listingId] for [start] to [end], as the app writes it, from a fresh borrower whose
 * wallet holds 1000 PP, enough for every deposit of these tests.
 */
async function request(
  listingId: string,
  start = "2026-12-01",
  end = "2026-12-03",
  status = "REQUESTED",
): Promise<string> {
  const id = randomUUID();
  const { borrower } = people();
  await fund(borrower, funds(1000));
  await db.collection("rentals").doc(id).set({
    listingId,
    listingTitle: "Camping tent",
    ownerId: LENDER,
    borrowerId: borrower,
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
  await respondToOffer(
    db,
    await borrowerOf(id),
    { rentalId: id, action: "COUNTER", deposit: 120 },
    NOW,
  );
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

  it("refuses dates already booked, buffer day included, and writes nothing", async () => {
    const id = await request(await listing([{ start: "2026-12-04", end: "2026-12-05" }]));

    await expect(
      makeOffer(db, LENDER, { rentalId: id, deposit: 180, ...ADDRESSES }, NOW),
    ).rejects.toMatchObject({ code: "failed-precondition" });
    expect((await rental(id))?.offer).toBeUndefined();
    expect(await addresses(id)).toBeUndefined();
    expect(await systemMessages(id)).toEqual([]);
  });

  it("accepts dates with one free day before the next booking", async () => {
    const id = await request(await listing([{ start: "2026-12-05", end: "2026-12-06" }]));

    await makeOffer(db, LENDER, { rentalId: id, deposit: 180, ...ADDRESSES }, NOW);

    expect((await rental(id))?.offer.amount).toBe(180);
  });

  it("refuses a request whose listing is gone, and writes nothing", async () => {
    const id = await request(randomUUID());

    await expect(
      makeOffer(db, LENDER, { rentalId: id, deposit: 180, ...ADDRESSES }, NOW),
    ).rejects.toMatchObject({ code: "not-found" });
    expect((await rental(id))?.offer).toBeUndefined();
  });

  it("refuses the borrower", async () => {
    const id = await request(await listing());

    await expect(
      makeOffer(db, await borrowerOf(id), { rentalId: id, deposit: 180, ...ADDRESSES }, NOW),
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

    await respondToOffer(db, await borrowerOf(id), { rentalId: id, action: "ACCEPT" }, NOW);

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

  it("holds the deposit in the borrower's wallet, with one history row", async () => {
    const id = await offered(await listing());
    const borrower = await borrowerOf(id);

    await respondToOffer(db, borrower, { rentalId: id, action: "ACCEPT" }, NOW);

    expect(await wallet(borrower)).toEqual({
      available: 820,
      heldAsDeposits: 180,
      heldForRentals: 0,
    });
    const history = await rows(borrower);
    expect(history).toHaveLength(1);
    expect(history[0]).toMatchObject({
      id: `deposit_held_${id}`,
      type: "DEPOSIT_HELD",
      amount: -180,
      itemTitle: "Camping tent",
    });
    expect((history[0] as { date?: unknown }).date).toBeDefined();
  });

  it("accepts when the balance exactly covers the deposit", async () => {
    const id = await offered(await listing());
    const borrower = await borrowerOf(id);
    await fund(borrower, funds(180));

    await respondToOffer(db, borrower, { rentalId: id, action: "ACCEPT" }, NOW);

    expect((await rental(id))?.status).toBe("ACCEPTED");
    expect(await wallet(borrower)).toEqual({
      available: 0,
      heldAsDeposits: 180,
      heldForRentals: 0,
    });
  });

  it("refuses when the balance is too low, and changes nothing", async () => {
    const listingId = await listing();
    const id = await offered(listingId, "2026-12-10", "2026-12-12");
    const overlapping = await request(listingId, "2026-12-11", "2026-12-13");
    const borrower = await borrowerOf(id);
    await fund(borrower, funds(179));

    await expect(
      respondToOffer(db, borrower, { rentalId: id, action: "ACCEPT" }, NOW),
    ).rejects.toMatchObject({
      code: "failed-precondition",
      message: "Insufficient funds to hold the deposit.",
    });

    const data = await rental(id);
    expect(data).toMatchObject({ status: "REQUESTED", pickupAddress: "" });
    expect(data?.deposit).toBeUndefined();
    expect(data?.offer).toMatchObject({ amount: 180 });
    expect(await addresses(id)).toBeDefined();
    expect((await rental(overlapping))?.status).toBe("REQUESTED");
    const listingData = (await db.collection("listings").doc(listingId).get()).data();
    expect(listingData?.bookedRanges).toEqual([]);
    expect(await wallet(borrower)).toEqual(funds(179));
    expect(await rows(borrower)).toEqual([]);
    expect(await systemMessages(id)).toEqual(["Offer sent · 180 PP deposit"]);
  });

  it("refuses a stored offer with an invalid amount, and writes nothing", async () => {
    const listingId = await listing();
    const id = await offered(listingId);
    const borrower = await borrowerOf(id);
    await db.collection("rentals").doc(id).update({ "offer.amount": 0 });

    await expect(
      respondToOffer(db, borrower, { rentalId: id, action: "ACCEPT" }, NOW),
    ).rejects.toMatchObject({ code: "internal" });

    const data = await rental(id);
    expect(data?.status).toBe("REQUESTED");
    expect(data?.offer).toBeDefined();
    expect(await addresses(id)).toBeDefined();
    const listingData = (await db.collection("listings").doc(listingId).get()).data();
    expect(listingData?.bookedRanges).toEqual([]);
    expect(await wallet(borrower)).toEqual(funds(1000));
    expect(await rows(borrower)).toEqual([]);
  });

  it("lets the lender accept a counteroffer, charging the borrower its amount", async () => {
    const id = await countered(await listing());
    const borrower = await borrowerOf(id);

    await respondToOffer(db, LENDER, { rentalId: id, action: "ACCEPT" }, NOW);

    expect(await rental(id)).toMatchObject({ status: "ACCEPTED", deposit: 120 });
    expect(await systemMessages(id)).toEqual([
      "Offer sent · 180 PP deposit",
      "Counteroffer · 120 PP",
      "Accepted · 120 PP deposit",
    ]);
    expect(await wallet(borrower)).toEqual({
      available: 880,
      heldAsDeposits: 120,
      heldForRentals: 0,
    });
    expect(await rows(borrower)).toMatchObject([{ id: `deposit_held_${id}`, amount: -120 }]);
    expect(await wallet(LENDER)).toBeUndefined();
  });

  it("accepts only one of two offers a borrower cannot afford together", async () => {
    const first = await offered(await listing());
    const second = await offered(await listing());
    const borrower = await borrowerOf(first);
    await db.collection("rentals").doc(second).update({ borrowerId: borrower });
    await fund(borrower, funds(200));

    const results = await Promise.allSettled([
      respondToOffer(db, borrower, { rentalId: first, action: "ACCEPT" }, NOW),
      respondToOffer(db, borrower, { rentalId: second, action: "ACCEPT" }, NOW),
    ]);

    expect(results.map((result) => result.status).sort()).toEqual(["fulfilled", "rejected"]);
    const statuses = [(await rental(first))?.status, (await rental(second))?.status].sort();
    expect(statuses).toEqual(["ACCEPTED", "REQUESTED"]);
    expect(await wallet(borrower)).toEqual({
      available: 20,
      heldAsDeposits: 180,
      heldForRentals: 0,
    });
    expect(await rows(borrower)).toHaveLength(1);
  });

  it("leaves a later reserveDeposit call a success that charges nothing more", async () => {
    const id = await offered(await listing());
    const borrower = await borrowerOf(id);
    await respondToOffer(db, borrower, { rentalId: id, action: "ACCEPT" }, NOW);

    await reserveRentalDeposit(db, LENDER, id);

    expect(await wallet(borrower)).toEqual({
      available: 820,
      heldAsDeposits: 180,
      heldForRentals: 0,
    });
    expect(await rows(borrower)).toHaveLength(1);
  });

  it("declines overlapping requests, buffer day included, with or without an offer", async () => {
    const listingId = await listing();
    const accepted = await offered(listingId, "2026-12-10", "2026-12-12");
    const touching = await request(listingId, "2026-12-13", "2026-12-14");
    const beforeBuffer = await offered(listingId, "2026-12-08", "2026-12-09");
    const free = await request(listingId, "2026-12-14", "2026-12-15");

    await respondToOffer(
      db,
      await borrowerOf(accepted),
      { rentalId: accepted, action: "ACCEPT" },
      NOW,
    );

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

    await respondToOffer(db, await borrowerOf(id), { rentalId: id, action: "ACCEPT" }, NOW);

    expect((await rental(elsewhere))?.status).toBe("REQUESTED");
  });

  it("refuses dates booked since the offer, and writes nothing", async () => {
    const listingId = await listing();
    const id = await offered(listingId);
    await db
      .collection("listings")
      .doc(listingId)
      .update({ bookedRanges: [{ start: "2026-12-04", end: "2026-12-05" }] });

    await expect(
      respondToOffer(db, await borrowerOf(id), { rentalId: id, action: "ACCEPT" }, NOW),
    ).rejects.toMatchObject({ code: "failed-precondition" });
    expect((await rental(id))?.status).toBe("REQUESTED");
  });

  it("refuses an offer at its expiry, even if expireOffers has not run yet", async () => {
    const id = await offered(await listing());

    await expect(
      respondToOffer(db, await borrowerOf(id), { rentalId: id, action: "ACCEPT" }, ONE_WEEK_LATER),
    ).rejects.toMatchObject({ code: "failed-precondition" });
    expect((await rental(id))?.status).toBe("REQUESTED");
  });

  it("refuses an offer that expired on the rental's first day", async () => {
    const id = await offered(await listing(), "2026-11-08", "2026-11-09");

    await expect(
      respondToOffer(
        db,
        await borrowerOf(id),
        { rentalId: id, action: "ACCEPT" },
        new Date("2026-11-08T00:00:00Z"),
      ),
    ).rejects.toMatchObject({ code: "failed-precondition" });
  });
});

describe("respondToOffer: declining", () => {
  it("makes the rental declined and removes the offer and the addresses", async () => {
    const id = await offered(await listing());

    await respondToOffer(db, await borrowerOf(id), { rentalId: id, action: "DECLINE" }, NOW);

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

    await respondToOffer(
      db,
      await borrowerOf(id),
      { rentalId: id, action: "COUNTER", deposit: 120 },
      later,
    );

    const data = await rental(id);
    expect(data).toMatchObject({ status: "REQUESTED", lastMessage: "Counteroffer · 120 PP" });
    expect(data?.offer).toMatchObject({
      amount: 120,
      previousAmount: 180,
      authorId: await borrowerOf(id),
      isCounter: true,
    });
    expect(data?.offer.expiresAt.toDate()).toEqual(new Date("2026-11-13T10:00:00Z"));
    expect(await addresses(id)).toBeDefined();
  });

  it("refuses a counteroffer the balance cannot cover, and changes nothing", async () => {
    const id = await offered(await listing());
    const borrower = await borrowerOf(id);
    await fund(borrower, funds(119));

    await expect(
      respondToOffer(db, borrower, { rentalId: id, action: "COUNTER", deposit: 120 }, NOW),
    ).rejects.toMatchObject({
      code: "failed-precondition",
      message: "Insufficient funds to hold the deposit.",
    });

    const data = await rental(id);
    expect(data).toMatchObject({
      status: "REQUESTED",
      lastMessage: "Offer sent · 180 PP deposit",
    });
    expect(data?.offer).toMatchObject({ amount: 180, authorId: LENDER, isCounter: false });
    expect(data?.offer.previousAmount).toBeUndefined();
    expect(await systemMessages(id)).toEqual(["Offer sent · 180 PP deposit"]);
    expect(await wallet(borrower)).toEqual(funds(119));
    expect(await rows(borrower)).toEqual([]);
  });

  it("accepts a counteroffer the balance exactly covers", async () => {
    const id = await offered(await listing());
    const borrower = await borrowerOf(id);
    await fund(borrower, funds(120));

    await respondToOffer(db, borrower, { rentalId: id, action: "COUNTER", deposit: 120 }, NOW);

    expect((await rental(id))?.offer).toMatchObject({ amount: 120, isCounter: true });
    expect(await wallet(borrower)).toEqual(funds(120));
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
      respondToOffer(db, await borrowerOf(counter), { rentalId: counter, action: "ACCEPT" }, NOW),
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
    await respondToOffer(
      db,
      await borrowerOf(answered),
      { rentalId: answered, action: "DECLINE" },
      NOW,
    );

    await expect(
      respondToOffer(db, await borrowerOf(noOffer), { rentalId: noOffer, action: "ACCEPT" }, NOW),
    ).rejects.toMatchObject({ code: "failed-precondition" });
    await expect(
      respondToOffer(db, await borrowerOf(answered), { rentalId: answered, action: "ACCEPT" }, NOW),
    ).rejects.toMatchObject({ code: "failed-precondition" });
    await expect(
      respondToOffer(db, people().borrower, { rentalId: randomUUID(), action: "ACCEPT" }, NOW),
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

    await expect(declineRequest(db, await borrowerOf(id), id)).rejects.toMatchObject({
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

/**
 * Reserves [price] PP for the request [id], as `requestRental` does: the price on the rental, moved
 * from `available` to `heldForRentals` in the borrower's wallet, and the `rental_held_<id>` row.
 */
async function hold(id: string, price: number): Promise<void> {
  const borrower = await borrowerOf(id);
  const current = (await wallet(borrower)) ?? {};
  await db
    .collection("wallets")
    .doc(borrower)
    .set({
      available: (current.available ?? 0) - price,
      heldAsDeposits: current.heldAsDeposits ?? 0,
      heldForRentals: (current.heldForRentals ?? 0) + price,
    });
  await db.collection("rentals").doc(id).update({ totalPrice: price });
  await db
    .collection("wallets")
    .doc(borrower)
    .collection("transactions")
    .doc(`rental_held_${id}`)
    .set({ type: "RENTAL_HELD", amount: -price, itemTitle: "Camping tent" });
}

/** The RENTAL_RELEASED rows of [uid], by amount. */
async function releases(uid: string): Promise<number[]> {
  return (await rows(uid))
    .filter((row) => row.type === "RENTAL_RELEASED")
    .map((row) => row.amount as number)
    .sort((a, b) => a - b);
}

describe("giving the rental price back", () => {
  const BACK = { available: 1000, heldAsDeposits: 0, heldForRentals: 0 };

  it("returns it when the borrower declines the offer, with one RENTAL_RELEASED row", async () => {
    const id = await offered(await listing());
    await hold(id, 30);
    const borrower = await borrowerOf(id);

    await respondToOffer(db, borrower, { rentalId: id, action: "DECLINE" }, NOW);

    expect(await wallet(borrower)).toEqual(BACK);
    expect(await rows(borrower)).toContainEqual(
      expect.objectContaining({
        id: `rental_released_${id}`,
        type: "RENTAL_RELEASED",
        amount: 30,
        itemTitle: "Camping tent",
      }),
    );
  });

  it("returns it when the lender declines a request or a counteroffer", async () => {
    const asked = await request(await listing());
    await hold(asked, 30);
    const counter = await countered(await listing());
    await hold(counter, 45);

    await declineRequest(db, LENDER, asked);
    await respondToOffer(db, LENDER, { rentalId: counter, action: "DECLINE" }, NOW);

    expect(await wallet(await borrowerOf(asked))).toEqual(BACK);
    expect(await wallet(await borrowerOf(counter))).toEqual(BACK);
    expect(await releases(await borrowerOf(asked))).toEqual([30]);
    expect(await releases(await borrowerOf(counter))).toEqual([45]);
  });

  it("gives back nothing for a request sent before prices were reserved", async () => {
    const id = await request(await listing());
    await db.collection("rentals").doc(id).update({ totalPrice: 30 });
    const borrower = await borrowerOf(id);

    await declineRequest(db, LENDER, id);

    expect((await rental(id))?.status).toBe("DECLINED");
    expect(await wallet(borrower)).toEqual(funds(1000));
    expect(await releases(borrower)).toEqual([]);
  });

  it("gives back the amount of the held row, even if the rental's totalPrice differs", async () => {
    const id = await request(await listing());
    await hold(id, 30);
    await db.collection("rentals").doc(id).update({ totalPrice: 99 });
    const borrower = await borrowerOf(id);

    await declineRequest(db, LENDER, id);

    expect(await wallet(borrower)).toEqual(BACK);
    expect(await releases(borrower)).toEqual([30]);
  });

  it.each([0, 5, 1.5, "30", null])(
    "refuses to decline when the held row's amount is %p, and changes nothing",
    async (amount) => {
      const id = await request(await listing());
      await hold(id, 30);
      const borrower = await borrowerOf(id);
      const held = db
        .collection("wallets")
        .doc(borrower)
        .collection("transactions")
        .doc(`rental_held_${id}`);
      await held.set({ type: "RENTAL_HELD", amount, itemTitle: "Camping tent" });
      const before = await wallet(borrower);

      await expect(declineRequest(db, LENDER, id)).rejects.toMatchObject({ code: "internal" });

      expect((await rental(id))?.status).toBe("REQUESTED");
      expect(await wallet(borrower)).toEqual(before);
      expect(await releases(borrower)).toEqual([]);
    },
  );

  it("refuses when the held row has no amount", async () => {
    const id = await request(await listing());
    await hold(id, 30);
    const borrower = await borrowerOf(id);
    await db
      .collection("wallets")
      .doc(borrower)
      .collection("transactions")
      .doc(`rental_held_${id}`)
      .set({ type: "RENTAL_HELD", itemTitle: "Camping tent" });

    await expect(declineRequest(db, LENDER, id)).rejects.toMatchObject({ code: "internal" });

    expect((await rental(id))?.status).toBe("REQUESTED");
  });

  it("does not look at a malformed held row once the price was already released", async () => {
    const id = await request(await listing());
    await hold(id, 30);
    const borrower = await borrowerOf(id);
    await declineRequest(db, LENDER, id);
    await db
      .collection("wallets")
      .doc(borrower)
      .collection("transactions")
      .doc(`rental_held_${id}`)
      .update({ amount: 0 });
    await db.collection("rentals").doc(id).update({ status: "REQUESTED" });

    await declineRequest(db, LENDER, id);

    expect(await releases(borrower)).toEqual([30]);
  });

  it("gives back at most what the wallet holds", async () => {
    const id = await request(await listing());
    await hold(id, 30);
    const borrower = await borrowerOf(id);
    await db.collection("wallets").doc(borrower).update({ heldForRentals: 10 });

    await declineRequest(db, LENDER, id);

    expect(await wallet(borrower)).toMatchObject({ available: 980, heldForRentals: 0 });
    expect(await releases(borrower)).toEqual([10]);
  });

  it("does not give it back twice", async () => {
    const id = await request(await listing());
    await hold(id, 30);
    const borrower = await borrowerOf(id);
    await declineRequest(db, LENDER, id);
    // The rental is REQUESTED again, as after a replayed write: the release row is still there.
    await db.collection("rentals").doc(id).update({ status: "REQUESTED" });

    await declineRequest(db, LENDER, id);

    expect(await wallet(borrower)).toEqual(BACK);
    expect(await releases(borrower)).toEqual([30]);
  });

  it("returns the price of a competing request when another is accepted", async () => {
    const listingId = await listing();
    const accepted = await offered(listingId);
    const competing = await request(listingId, "2026-12-02", "2026-12-04");
    await hold(accepted, 30);
    await hold(competing, 50);
    const winner = await borrowerOf(accepted);
    const loser = await borrowerOf(competing);

    await respondToOffer(db, winner, { rentalId: accepted, action: "ACCEPT" }, NOW);

    expect((await rental(competing))?.status).toBe("DECLINED");
    expect(await wallet(loser)).toEqual(BACK);
    expect(await releases(loser)).toEqual([50]);
    // The accepted rental keeps its price reserved, and its deposit is held on top.
    expect(await wallet(winner)).toEqual({
      available: 790,
      heldAsDeposits: 180,
      heldForRentals: 30,
    });
    expect(await releases(winner)).toEqual([]);
  });

  it("returns both prices, one after the other, to a borrower with two competing requests", async () => {
    const listingId = await listing();
    const accepted = await offered(listingId);
    const first = await request(listingId, "2026-12-02", "2026-12-04");
    const second = await request(listingId, "2026-12-03", "2026-12-05");
    const other = await borrowerOf(first);
    await db.collection("rentals").doc(second).update({ borrowerId: other });
    await hold(first, 30);
    await hold(second, 50);
    expect(await wallet(other)).toEqual({ available: 920, heldAsDeposits: 0, heldForRentals: 80 });

    await respondToOffer(db, await borrowerOf(accepted), { rentalId: accepted, action: "ACCEPT" }, NOW);

    expect((await rental(first))?.status).toBe("DECLINED");
    expect((await rental(second))?.status).toBe("DECLINED");
    expect(await wallet(other)).toEqual(BACK);
    expect(await releases(other)).toEqual([30, 50]);
    expect(await rows(other)).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ id: `rental_released_${first}`, amount: 30 }),
        expect.objectContaining({ id: `rental_released_${second}`, amount: 50 }),
      ]),
    );
  });

  it("starts the deposit from the wallet the releases left, when the accepter has a competing request", async () => {
    const listingId = await listing();
    const accepted = await offered(listingId);
    const competing = await request(listingId, "2026-12-02", "2026-12-04");
    const borrower = await borrowerOf(accepted);
    await db.collection("rentals").doc(competing).update({ borrowerId: borrower });
    await hold(accepted, 30);
    await hold(competing, 50);

    await respondToOffer(db, borrower, { rentalId: accepted, action: "ACCEPT" }, NOW);

    expect(await wallet(borrower)).toEqual({
      available: 790,
      heldAsDeposits: 180,
      heldForRentals: 30,
    });
    expect(await releases(borrower)).toEqual([50]);
    // The two seeded RENTAL_HELD rows, the release and the deposit.
    expect(await rows(borrower)).toHaveLength(4);
  });

  it("returns nothing for a competing request when the accept is refused for lack of funds", async () => {
    const listingId = await listing();
    const accepted = await offered(listingId);
    const competing = await request(listingId, "2026-12-02", "2026-12-04");
    await hold(competing, 50);
    const winner = await borrowerOf(accepted);
    await fund(winner, funds(100));

    await expect(
      respondToOffer(db, winner, { rentalId: accepted, action: "ACCEPT" }, NOW),
    ).rejects.toMatchObject({ code: "failed-precondition" });

    expect((await rental(competing))?.status).toBe("REQUESTED");
    expect(await wallet(await borrowerOf(competing))).toMatchObject({ heldForRentals: 50 });
    expect(await releases(await borrowerOf(competing))).toEqual([]);
  });

  it("leaves requests on other dates untouched", async () => {
    const listingId = await listing();
    const accepted = await offered(listingId);
    const apart = await request(listingId, "2026-12-20", "2026-12-22");
    await hold(apart, 50);

    await respondToOffer(db, await borrowerOf(accepted), { rentalId: accepted, action: "ACCEPT" }, NOW);

    expect((await rental(apart))?.status).toBe("REQUESTED");
    expect(await wallet(await borrowerOf(apart))).toMatchObject({ heldForRentals: 50 });
  });
});
