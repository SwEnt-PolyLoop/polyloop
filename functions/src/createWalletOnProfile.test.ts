// Made with Claude.

import { randomUUID } from "node:crypto";
import { Firestore, Timestamp, getFirestore } from "firebase-admin/firestore";
import { createWalletOnProfile } from "./index";
import { STARTING_BALANCE_ROW_ID, createStartingWallet } from "./createWalletOnProfile";
import { fund, rows, wallet } from "./testing/walletFixtures";

// Firebase loads this ES module to verify tokens, which Jest cannot load.
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
  db = getFirestore();
});

/** A new user's id, so tests do not share wallets. */
const newUser = () => `user-${randomUUID()}`;

const STARTING_WALLET = { available: 120, heldAsDeposits: 0, heldForRentals: 0 };

describe("createStartingWallet", () => {
  it("gives a new user 120 PP, nothing held, and one RECHARGE row explaining it", async () => {
    const uid = newUser();

    expect(await createStartingWallet(db, uid)).toBe(true);

    expect(await wallet(uid)).toEqual(STARTING_WALLET);
    const history = await rows(uid);
    expect(history).toEqual([
      {
        id: STARTING_BALANCE_ROW_ID,
        type: "RECHARGE",
        amount: 120,
        itemTitle: "",
        date: expect.any(Timestamp),
      },
    ]);
  });

  it("leaves a wallet made by hand as it is, without adding a row", async () => {
    const uid = newUser();
    await fund(uid, { available: 45, heldAsDeposits: 30 });

    expect(await createStartingWallet(db, uid)).toBe(false);

    expect(await wallet(uid)).toEqual({ available: 45, heldAsDeposits: 30 });
    expect(await rows(uid)).toEqual([]);
  });

  it("leaves even an empty wallet alone, which reads as 0 PP", async () => {
    const uid = newUser();
    await fund(uid, {});

    expect(await createStartingWallet(db, uid)).toBe(false);

    expect(await wallet(uid)).toEqual({});
    expect(await rows(uid)).toEqual([]);
  });

  it("gives the PolyPoints once when it runs again, e.g. for a profile created again", async () => {
    const uid = newUser();
    await createStartingWallet(db, uid);

    expect(await createStartingWallet(db, uid)).toBe(false);

    expect(await wallet(uid)).toEqual(STARTING_WALLET);
    expect(await rows(uid)).toHaveLength(1);
  });

  it("gives the PolyPoints once when two runs race", async () => {
    const uid = newUser();

    const created = await Promise.all([
      createStartingWallet(db, uid),
      createStartingWallet(db, uid),
    ]);

    expect(created.filter(Boolean)).toHaveLength(1);
    expect(await wallet(uid)).toEqual(STARTING_WALLET);
    expect(await rows(uid)).toHaveLength(1);
  });

  it("gives each user their own wallet", async () => {
    const [ana, ben] = [newUser(), newUser()];
    await fund(ben, { available: 7 });

    await createStartingWallet(db, ana);
    await createStartingWallet(db, ben);

    expect(await wallet(ana)).toEqual(STARTING_WALLET);
    expect(await wallet(ben)).toEqual({ available: 7 });
  });
});

describe("createWalletOnProfile", () => {
  it("creates the wallet of the user whose profile was created", async () => {
    const uid = newUser();

    await createWalletOnProfile.run({ params: { uid } } as never);

    expect(await wallet(uid)).toEqual(STARTING_WALLET);
    expect(await rows(uid)).toHaveLength(1);
  });
});
