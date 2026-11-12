// Made with Claude.

import { Firestore, getFirestore } from "firebase-admin/firestore";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { Wallet, writeWallet } from "./wallet";

/** The PolyPoints every new user starts with (see "Money" in docs/ARCHITECTURE.md). */
export const STARTING_BALANCE = 120;

/** The id of the history row of the starting balance: a fixed id, so it is never added twice. */
export const STARTING_BALANCE_ROW_ID = "starting_balance";

/**
 * Creates the wallet of the new user [uid], in one transaction: [STARTING_BALANCE] available,
 * nothing held, and one `RECHARGE` row of +[STARTING_BALANCE] PP with no item, so the history adds
 * up to the balance. If the wallet already exists (made by hand before this function, or the
 * profile was deleted and created again), nothing is written, so nobody gets the PolyPoints twice.
 *
 * @param db the Firestore database.
 * @param uid the new user.
 * @returns true if the wallet was created, false if it already existed.
 */
export async function createStartingWallet(db: Firestore, uid: string): Promise<boolean> {
  return db.runTransaction(async (tx) => {
    if ((await tx.get(db.collection("wallets").doc(uid))).exists) return false;
    const wallet: Wallet = { available: STARTING_BALANCE, heldAsDeposits: 0, heldForRentals: 0 };
    writeWallet(tx, db, uid, wallet, {
      id: STARTING_BALANCE_ROW_ID,
      type: "RECHARGE",
      amount: STARTING_BALANCE,
      itemTitle: "",
    });
    return true;
  });
}

/**
 * When a profile is created, on the user's first verified log-in, creates their wallet with the
 * starting balance (see [createStartingWallet]). An account that is never verified gets no profile,
 * so no wallet. A run that fails is retried; running twice is harmless.
 */
export const createWalletOnProfile = onDocumentCreated(
  { document: "users/{uid}", retry: true },
  async (event) => {
    await createStartingWallet(getFirestore(), event.params.uid);
  },
);
