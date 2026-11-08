// Made with Claude.

import { randomUUID } from "node:crypto";
import { Firestore, Timestamp, getFirestore } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { acceptSplit, escalateDispute, proposeSplit } from "./index";

// Firebase loads this ES module to verify tokens, which Jest cannot load; `run` skips that step.
jest.mock("jose", () => ({}));

// The functions as index.ts exports them to Firebase, called with what the app sends: their sign-in
// check, their input check and their error codes. dispute.test.ts covers the rules themselves.
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

const LENDER = "callable-dispute-lender";
const BORROWER = "callable-dispute-borrower";

/** The request of the signed-in, verified EPFL user [uid], sending [data]. */
function asUser(uid: string, data: object, email = `${uid}@epfl.ch`, verified = true) {
  return { auth: { uid, token: { email, email_verified: verified } }, data } as never;
}

/** A disputed rental with a 60 PP deposit and its dispute, still in direct settlement. */
async function disputed(): Promise<string> {
  const id = randomUUID();
  await db.collection("rentals").doc(id).set({
    ownerId: LENDER,
    borrowerId: BORROWER,
    participants: [LENDER, BORROWER],
    status: "DISPUTED",
    deposit: 60,
  });
  await db
    .collection("disputes")
    .doc(id)
    .set({
      stage: "SETTLE",
      proposals: [],
      escalatesAt: Timestamp.fromDate(new Date("2099-01-01T00:00:00Z")),
      escalatedBy: null,
      agreedAt: null,
    });
  return id;
}

async function dispute(id: string) {
  return (await db.collection("disputes").doc(id).get()).data();
}

describe("deployment", () => {
  it("exposes every dispute callable to the app, in the app's region", () => {
    for (const fn of [proposeSplit, acceptSplit, escalateDispute]) {
      expect(fn.__endpoint.region).toEqual(["europe-west1"]);
      expect(fn.__endpoint.callableTrigger).toBeDefined();
    }
  });
});

describe("who can call", () => {
  it("refuses a caller who is not signed in, and one who is not a verified EPFL user", async () => {
    const id = await disputed();

    await expect(escalateDispute.run({ data: { rentalId: id } } as never)).rejects.toMatchObject({
      code: "unauthenticated",
    });
    await expect(
      escalateDispute.run(asUser(LENDER, { rentalId: id }, "lender@gmail.com")),
    ).rejects.toMatchObject({ code: "permission-denied" });
    await expect(
      escalateDispute.run(asUser(LENDER, { rentalId: id }, undefined, false)),
    ).rejects.toMatchObject({ code: "permission-denied" });
    expect((await dispute(id))?.stage).toBe("SETTLE");
  });
});

describe("what can be sent", () => {
  it("refuses a percentage that is not a whole number from 0 to 100", async () => {
    const id = await disputed();

    for (const lenderPercent of [101, 12.5]) {
      await expect(
        proposeSplit.run(asUser(LENDER, { rentalId: id, lenderPercent })),
      ).rejects.toMatchObject({ code: "invalid-argument" });
    }
    await expect(acceptSplit.run(asUser(BORROWER, { rentalId: id }))).rejects.toMatchObject({
      code: "invalid-argument",
    });
  });

  it("gives the app the reason of a lost race in the HttpsError's details", async () => {
    const id = await disputed();
    await escalateDispute.run(asUser(LENDER, { rentalId: id }));

    const error = await escalateDispute.run(asUser(BORROWER, { rentalId: id })).catch((e) => e);

    expect(error).toBeInstanceOf(HttpsError);
    expect(error).toMatchObject({
      code: "failed-precondition",
      details: { reason: "STAGE_CHANGED" },
    });
  });
});

describe("the whole direct settlement", () => {
  it("goes from a proposal and a counter to an agreement that closes the rental", async () => {
    const id = await disputed();

    await proposeSplit.run(asUser(LENDER, { rentalId: id, lenderPercent: 80 }));
    await proposeSplit.run(asUser(BORROWER, { rentalId: id, lenderPercent: 50 }));
    await proposeSplit.run(asUser(LENDER, { rentalId: id, lenderPercent: 70 }));
    // The borrower accepts the lender's first split, seen before the lender changed it.
    const stale = await acceptSplit
      .run(asUser(BORROWER, { rentalId: id, lenderPercent: 80 }))
      .catch((e) => e);
    const result = await acceptSplit.run(asUser(BORROWER, { rentalId: id, lenderPercent: 70 }));

    expect(stale).toMatchObject({ details: { reason: "PROPOSAL_CHANGED" } });
    expect(result).toEqual({});
    expect((await dispute(id))?.agreedAt).not.toBeNull();
    const rental = (await db.collection("rentals").doc(id).get()).data();
    expect(rental).toMatchObject({
      status: "CLOSED",
      lastMessage: "Dispute settled · 42 PP to the lender, 18 PP back to the borrower",
    });
  });
});
