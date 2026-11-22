// Made with Claude.

import { getApps, initializeApp } from "firebase-admin/app";
import { Firestore, getFirestore } from "firebase-admin/firestore";
import { codeFingerprint } from "./handoverCode";
import {
  RENTAL_CHANGED,
  RETURNED_STATUSES,
  WRONG_CODE,
  confirmPickup,
  confirmReturn,
} from "./handover";
import { hasSeenLastMessage } from "./testing/readTimes";
import {
  People,
  fund,
  people,
  rental as newRental,
  rows,
  wallet,
} from "./testing/walletFixtures";

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

/**
 * An ACCEPTED rental starting today, overridden by [fields], with its pickup code, whose 15 PP
 * price the borrower's wallet holds since the request.
 */
async function accepted(who: People, fields: object = {}): Promise<string> {
  await fund(who.borrower, { available: 100, heldForRentals: 15 });
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
const codeOf = async (id: string, phase: string) =>
  (await db.doc(`rentals/${id}/handoverCodes/${phase}`).get()).get("code");
const codeExists = async (id: string, phase = "PICKUP") =>
  (await db.doc(`rentals/${id}/handoverCodes/${phase}`).get()).exists;
const messages = async (id: string) =>
  (await db.collection(`rentals/${id}/messages`).get()).docs.map((doc) => doc.data());

/** Expects [id] untouched: still ACCEPTED, with its code, and no message. */
async function expectUnchanged(id: string): Promise<void> {
  expect((await rentalData(id))?.status).toBe("ACCEPTED");
  expect(await codeExists(id)).toBe(true);
  expect(await messages(id)).toEqual([]);
}

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

  it("makes the return code of a rental accepted before it existed", async () => {
    const who = people();
    const id = await accepted(who);

    await confirmPickup(db, who.borrower, scan(id), NOW);

    const code = await codeOf(id, "RETURN");
    expect(code).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect((await rentalData(id))?.returnCodeHash).toBe(codeFingerprint(code));
  });

  it("keeps the return code made at acceptance", async () => {
    const who = people();
    const id = await accepted(who, { returnCodeHash: "return-fingerprint" });
    await db.doc(`rentals/${id}/handoverCodes/RETURN`).set({ code: "return-code" });

    await confirmPickup(db, who.borrower, scan(id), NOW);

    expect(await codeOf(id, "RETURN")).toBe("return-code");
    expect((await rentalData(id))?.returnCodeHash).toBe("return-fingerprint");
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

describe("paying the lender at pickup", () => {
  it("takes a price that is not held from available until it is held at request", async () => {
    const who = people();
    const id = await accepted(who);
    await fund(who.borrower, { available: 100, heldForRentals: 0 });

    await confirmPickup(db, who.borrower, scan(id), NOW);

    expect((await rentalData(id))?.status).toBe("ACTIVE");
    expect(await wallet(who.borrower)).toMatchObject({ available: 85, heldForRentals: 0 });
    expect(await wallet(who.lender)).toMatchObject({ available: 15 });
    expect(await rows(who.borrower)).toEqual([
      expect.objectContaining({ id: `rental_payment_${id}`, type: "RENTAL_PAYMENT", amount: -15 }),
    ]);
  });

  it("pays the held price to the lender in the same transaction", async () => {
    const who = people();
    const id = await accepted(who);

    await confirmPickup(db, who.borrower, scan(id), NOW);

    expect(await wallet(who.borrower)).toMatchObject({ available: 100, heldForRentals: 0 });
    expect(await wallet(who.lender)).toMatchObject({ available: 15 });
    expect(await rows(who.borrower)).toEqual([]);
    expect(await rows(who.lender)).toEqual([
      expect.objectContaining({ id: `rental_payment_${id}`, type: "RENTAL_PAYMENT", amount: 15 }),
    ]);
  });

  it("does not pay a lender who was already paid, but still confirms the pickup", async () => {
    const who = people();
    const id = await accepted(who);
    await db
      .doc(`wallets/${who.lender}/transactions/rental_payment_${id}`)
      .set({ type: "RENTAL_PAYMENT", amount: 15, itemTitle: "Camping tent" });

    await confirmPickup(db, who.borrower, scan(id), NOW);

    expect((await rentalData(id))?.status).toBe("ACTIVE");
    expect(await wallet(who.borrower)).toMatchObject({ heldForRentals: 15 });
    expect(await wallet(who.lender)).toBeUndefined();
  });

  it("pays once when the same scan is sent again", async () => {
    const who = people();
    const id = await accepted(who);
    await fund(who.borrower, { heldForRentals: 30 }); // enough for a second payment

    await confirmPickup(db, who.borrower, scan(id), NOW);
    await confirmPickup(db, who.borrower, scan(id), NOW);

    expect(await wallet(who.borrower)).toMatchObject({ heldForRentals: 15 });
    expect(await wallet(who.lender)).toMatchObject({ available: 15 });
    expect(await messages(id)).toHaveLength(1);
  });

  it("confirms and pays once when two scans arrive at the same time", async () => {
    const who = people();
    const id = await accepted(who);
    await fund(who.borrower, { heldForRentals: 30 });

    await Promise.all([
      confirmPickup(db, who.borrower, scan(id), NOW),
      confirmPickup(db, who.borrower, scan(id), NOW),
    ]);

    expect((await rentalData(id))?.status).toBe("ACTIVE");
    expect(await wallet(who.borrower)).toMatchObject({ heldForRentals: 15 });
    expect(await wallet(who.lender)).toMatchObject({ available: 15 });
    expect(await rows(who.lender)).toHaveLength(1);
    expect(await messages(id)).toHaveLength(1);
  }, 15000);
});

describe("refusing a pickup", () => {
  it("refuses a pickup whose price neither the held price nor available covers", async () => {
    const who = people();
    const id = await accepted(who);
    await fund(who.borrower, { available: 14, heldForRentals: 0 });

    const error = await confirmPickup(db, who.borrower, scan(id), NOW).catch((e) => e);

    expect(error).toMatchObject({ code: "failed-precondition" });
    expect(error.details).toBeUndefined();
    await expectUnchanged(id);
    expect(await wallet(who.borrower)).toEqual({ available: 14, heldForRentals: 0 });
    expect(await wallet(who.lender)).toBeUndefined();
  });

  it("refuses a wrong code with WRONG_CODE, changing nothing", async () => {
    const who = people();
    const id = await accepted(who);

    for (const code of ["", "wrong", CODE.slice(1), `${CODE} `, CODE.toLowerCase()]) {
      await expect(confirmPickup(db, who.borrower, scan(id, code), NOW)).rejects.toMatchObject({
        code: "failed-precondition",
        details: { reason: WRONG_CODE },
      });
    }
    await expectUnchanged(id);
  });

  it("refuses any code when the rental has none", async () => {
    const who = people();
    const id = await accepted(who);
    await db.doc(`rentals/${id}/handoverCodes/PICKUP`).delete();

    await expect(confirmPickup(db, who.borrower, scan(id), NOW)).rejects.toMatchObject({
      details: { reason: WRONG_CODE },
    });
    expect((await rentalData(id))?.status).toBe("ACCEPTED");
  });

  it("refuses a rental that is no longer accepted with RENTAL_CHANGED", async () => {
    const who = people();
    for (const status of ["REQUESTED", "DECLINED", "CANCELLED"]) {
      const id = await accepted(who, { status });

      await expect(confirmPickup(db, who.borrower, scan(id), NOW)).rejects.toMatchObject({
        code: "failed-precondition",
        details: { reason: RENTAL_CHANGED },
      });
      expect((await rentalData(id))?.status).toBe(status);
      expect(await codeExists(id)).toBe(true);
    }
  });

  it("refuses a scan outside the pickup day, with no reason", async () => {
    const who = people();
    const id = await accepted(who);

    for (const day of ["2026-11-30T12:00:00Z", "2026-12-02T12:00:00Z"]) {
      const error = await confirmPickup(db, who.borrower, scan(id), new Date(day)).catch((e) => e);
      expect(error).toMatchObject({ code: "failed-precondition" });
      expect(error.details).toBeUndefined();
    }
    await expectUnchanged(id);
  });

  it("lets only the borrower confirm, not the lender or a stranger", async () => {
    const who = people();
    const id = await accepted(who);

    for (const caller of [who.lender, "stranger-uid"]) {
      await expect(confirmPickup(db, caller, scan(id), NOW)).rejects.toMatchObject({
        code: "permission-denied",
      });
    }
    await expectUnchanged(id);
  });

  it("refuses a rental that does not exist", async () => {
    await expect(
      confirmPickup(db, "borrower-uid", scan("no-such-rental"), NOW),
    ).rejects.toMatchObject({ code: "not-found" });
  });
});

// 10:00 in Zurich on the rental's last day, the first return day.
const RETURN_NOW = new Date("2026-12-03T09:00:00Z");
const LAST_DAY = "2026-12-03";
const RETURN_CODE = "Rt7_w-1AbCdEfGhIjKlMnOpQrStUvWxYz9876543210";

/**
 * An ACTIVE rental whose last day is today, overridden by [fields], with its return code, and the
 * borrower's 40 PP deposit held.
 */
async function out(who: People, fields: object = {}): Promise<string> {
  await fund(who.borrower, { available: 60, heldAsDeposits: 40 });
  const id = await newRental(who, "ACTIVE", {
    startDate: PICKUP_DAY,
    endDate: LAST_DAY,
    returnCodeHash: "return-fingerprint",
    ...fields,
  });
  await db.doc(`rentals/${id}/handoverCodes/RETURN`).set({ code: RETURN_CODE });
  return id;
}

const returnScan = (rentalId: string, code = RETURN_CODE) => ({ rentalId, code });

/** Expects [id] untouched: still [status], with its return code, and no message. */
async function expectNotReturned(id: string, status = "ACTIVE"): Promise<void> {
  const data = await rentalData(id);
  expect(data?.status).toBe(status);
  expect(data?.returnedAt).toBeUndefined();
  expect(data?.returnCodeHash).toBe("return-fingerprint");
  expect(await codeExists(id, "RETURN")).toBe(true);
  expect(await messages(id)).toEqual([]);
}

describe("confirming the return", () => {
  it("makes the rental returned, says so in the chat and uses the code up", async () => {
    const who = people();
    const id = await out(who);

    await confirmReturn(db, who.lender, returnScan(id), RETURN_NOW);

    const data = await rentalData(id);
    expect(data?.status).toBe("RETURNED");
    expect(data?.returnedAt.toMillis()).toBeGreaterThan(0);
    expect(data?.returnCodeHash).toBeUndefined();
    expect(data?.lastMessage).toBe("Returned · waiting for the lender's check");
    expect(await codeExists(id, "RETURN")).toBe(false);
    expect(await messages(id)).toEqual([
      expect.objectContaining({
        text: "Returned · waiting for the lender's check",
        type: "SYSTEM",
        senderId: "",
      }),
    ]);
    // The lender scanned: no red dot for them; the borrower has news.
    expect(hasSeenLastMessage(data, who.lender)).toBe(true);
    expect(hasSeenLastMessage(data, who.borrower)).toBe(false);
  });

  it("moves no money: the deposit stays held for the lender's check", async () => {
    const who = people();
    const id = await out(who);

    await confirmReturn(db, who.lender, returnScan(id), RETURN_NOW);

    expect(await wallet(who.borrower)).toEqual({ available: 60, heldAsDeposits: 40 });
    expect(await wallet(who.lender)).toBeUndefined();
  });

  it("takes back a late item, on a later day", async () => {
    const who = people();
    for (const status of ["ACTIVE", "OVERDUE"]) {
      const id = await out(who, { status });

      await confirmReturn(db, who.lender, returnScan(id), new Date("2026-12-05T12:00:00Z"));

      expect((await rentalData(id))?.status).toBe("RETURNED");
    }
  });

  it("is on the last day in Zurich, even when it is still the day before in UTC", async () => {
    const who = people();
    const id = await out(who);

    // 00:30 in Zurich on 3 December is 23:30 UTC on 2 December.
    await confirmReturn(db, who.lender, returnScan(id), new Date("2026-12-02T23:30:00Z"));

    expect((await rentalData(id))?.status).toBe("RETURNED");
  });

  it("succeeds and changes nothing once the rental was returned", async () => {
    const who = people();
    for (const status of RETURNED_STATUSES) {
      const id = await newRental(who, status, { endDate: LAST_DAY });

      // Even with no code left, or a wrong one: the return already happened.
      await confirmReturn(db, who.lender, returnScan(id, "anything"), RETURN_NOW);

      expect((await rentalData(id))?.status).toBe(status);
      expect(await messages(id)).toEqual([]);
    }
  });

  it("confirms once when the same scan is sent again", async () => {
    const who = people();
    const id = await out(who);

    await confirmReturn(db, who.lender, returnScan(id), RETURN_NOW);
    await confirmReturn(db, who.lender, returnScan(id), RETURN_NOW);

    expect((await rentalData(id))?.status).toBe("RETURNED");
    expect(await messages(id)).toHaveLength(1);
  });

  it("confirms once when two scans arrive at the same time", async () => {
    const who = people();
    const id = await out(who);

    await Promise.all([
      confirmReturn(db, who.lender, returnScan(id), RETURN_NOW),
      confirmReturn(db, who.lender, returnScan(id), RETURN_NOW),
    ]);

    expect((await rentalData(id))?.status).toBe("RETURNED");
    expect(await messages(id)).toHaveLength(1);
  }, 15000);
});

describe("refusing a return", () => {
  it("refuses a wrong code with WRONG_CODE, changing nothing", async () => {
    const who = people();
    const id = await out(who);

    for (const code of ["wrong", CODE, RETURN_CODE.slice(1), `${RETURN_CODE} `]) {
      await expect(
        confirmReturn(db, who.lender, returnScan(id, code), RETURN_NOW),
      ).rejects.toMatchObject({ code: "failed-precondition", details: { reason: WRONG_CODE } });
    }
    await expectNotReturned(id);
  });

  it("refuses any code when the rental has none", async () => {
    const who = people();
    const id = await out(who);
    await db.doc(`rentals/${id}/handoverCodes/RETURN`).delete();

    await expect(
      confirmReturn(db, who.lender, returnScan(id), RETURN_NOW),
    ).rejects.toMatchObject({ details: { reason: WRONG_CODE } });
    expect((await rentalData(id))?.status).toBe("ACTIVE");
  });

  it("refuses a rental that is not out with RENTAL_CHANGED", async () => {
    const who = people();
    for (const status of ["ACCEPTED", "LOST", "CANCELLED"]) {
      const id = await out(who, { status });

      await expect(
        confirmReturn(db, who.lender, returnScan(id), RETURN_NOW),
      ).rejects.toMatchObject({ code: "failed-precondition", details: { reason: RENTAL_CHANGED } });
      await expectNotReturned(id, status);
    }
  });
});
