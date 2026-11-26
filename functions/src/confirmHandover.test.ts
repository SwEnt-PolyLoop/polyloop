// Made with Claude.

import { Firestore, getFirestore } from "firebase-admin/firestore";
import { confirmHandover } from "./index";
import { zurichDay } from "./serverToday";
import { asUser, fund, heldRental, people, rental, wallet } from "./testing/walletFixtures";

// Firebase loads this ES module to verify tokens, which Jest cannot load; `run` skips that step.
jest.mock("jose", () => ({}));

// confirmHandover as index.ts exports it, called with what the app sends, on the Firestore
// emulator. The checks themselves are tested on confirmPickup, in handover.test.ts.
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

const CODE = "Zk3_q-9AbCdEfGhIjKlMnOpQrStUvWxYz0123456789";

/** An ACCEPTED rental whose pickup is today (the callable uses the real clock), ready to scan. */
async function readyToScan() {
  const who = people();
  const id = await heldRental(who, "ACCEPTED", { startDate: zurichDay(new Date()) });
  await db.doc(`rentals/${id}/handoverCodes/PICKUP`).set({ code: CODE });
  await fund(who.borrower, { available: 100, heldForRentals: 15 });
  return { who, id };
}

const status = async (id: string) => (await db.doc(`rentals/${id}`).get()).get("status");

describe("confirmHandover", () => {
  it("confirms the pickup the borrower scanned and pays the lender", async () => {
    const { who, id } = await readyToScan();

    const result = await confirmHandover.run(
      asUser(who.borrower, { rentalId: id, phase: "PICKUP", code: CODE }),
    );

    expect(result).toEqual({});
    expect(await status(id)).toBe("ACTIVE");
    expect(await wallet(who.lender)).toMatchObject({ available: 15 });
  });

  it("confirms the return the lender scanned", async () => {
    const who = people();
    const id = await rental(who, "ACTIVE", { endDate: zurichDay(new Date()) });
    await db.doc(`rentals/${id}/handoverCodes/RETURN`).set({ code: CODE });

    const result = await confirmHandover.run(
      asUser(who.lender, { rentalId: id, phase: "RETURN", code: CODE }),
    );

    expect(result).toEqual({});
    expect(await status(id)).toBe("RETURNED");
  });

  it("lets only the lender confirm a return", async () => {
    const who = people();
    const id = await rental(who, "ACTIVE", { endDate: zurichDay(new Date()) });
    await db.doc(`rentals/${id}/handoverCodes/RETURN`).set({ code: CODE });

    await expect(
      confirmHandover.run(asUser(who.borrower, { rentalId: id, phase: "RETURN", code: CODE })),
    ).rejects.toMatchObject({ code: "permission-denied" });
    expect(await status(id)).toBe("ACTIVE");
  });

  it("gives the app the reason of a refusal it tells apart", async () => {
    const { who, id } = await readyToScan();

    await expect(
      confirmHandover.run(asUser(who.borrower, { rentalId: id, phase: "PICKUP", code: "nope" })),
    ).rejects.toMatchObject({ code: "failed-precondition", details: { reason: "WRONG_CODE" } });

    await db.doc(`rentals/${id}`).update({ status: "CANCELLED" });
    await expect(
      confirmHandover.run(asUser(who.borrower, { rentalId: id, phase: "PICKUP", code: CODE })),
    ).rejects.toMatchObject({ code: "failed-precondition", details: { reason: "RENTAL_CHANGED" } });
  });

  it("refuses an input that is not a handover scan, changing nothing", async () => {
    const { who, id } = await readyToScan();
    const inputs = [
      { phase: "PICKUP", code: CODE }, // no rentalId
      { rentalId: " ", phase: "PICKUP", code: CODE },
      { rentalId: id, code: CODE }, // no phase
      { rentalId: id, phase: "pickup", code: CODE },
      { rentalId: id, phase: "return", code: CODE },
      { rentalId: id, phase: "PICKUP" }, // no code
      { rentalId: id, phase: "RETURN" }, // no code
      { rentalId: id, phase: "PICKUP", code: "" },
      { rentalId: id, phase: "PICKUP", code: 42 },
      { rentalId: id, phase: "PICKUP", code: "x".repeat(201) },
      null,
    ];

    for (const data of inputs) {
      await expect(
        confirmHandover.run(asUser(who.borrower, data as object)),
      ).rejects.toMatchObject({ code: "invalid-argument" });
    }
    expect(await status(id)).toBe("ACCEPTED");
  });

  it("keeps the code exactly as scanned: spaces around it make it wrong", async () => {
    const { who, id } = await readyToScan();

    const data = { rentalId: id, phase: "PICKUP", code: ` ${CODE}` };

    await expect(confirmHandover.run(asUser(who.borrower, data))).rejects.toMatchObject({
      details: { reason: "WRONG_CODE" },
    });
  });

  it("refuses a caller who is not signed in, not verified or not from EPFL", async () => {
    const { who, id } = await readyToScan();
    const data = { rentalId: id, phase: "PICKUP", code: CODE };

    await expect(confirmHandover.run({ data } as never)).rejects.toMatchObject({
      code: "unauthenticated",
    });
    await expect(
      confirmHandover.run(asUser(who.borrower, data, "borrower@epfl.ch", false)),
    ).rejects.toMatchObject({ code: "permission-denied" });
    await expect(
      confirmHandover.run(asUser(who.borrower, data, "borrower@gmail.com")),
    ).rejects.toMatchObject({ code: "permission-denied" });
    expect(await status(id)).toBe("ACCEPTED");
  });

  it("is a callable in the app's region", () => {
    expect(confirmHandover.__endpoint.region).toEqual(["europe-west1"]);
    expect(confirmHandover.__endpoint.callableTrigger).toBeDefined();
  });
});
