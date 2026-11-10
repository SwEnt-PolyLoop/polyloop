// Made with Claude.

import { randomUUID } from "node:crypto";
import { getFirestore } from "firebase-admin/firestore";

/** The people of a rental. */
export interface People {
  lender: string;
  borrower: string;
}

/** The request of the signed-in, verified EPFL user [uid], sending [data]. */
export function asUser(uid: string, data: object, email = `${uid}@epfl.ch`, verified = true) {
  return { auth: { uid, token: { email, email_verified: verified } }, data } as never;
}

/** A rental with its own people, so tests do not share wallets. */
export function people(): People {
  const id = randomUUID();
  return { lender: `lender-${id}`, borrower: `borrower-${id}` };
}

/** Writes a rental with [status] between [who], overridden by [fields]; returns its id. */
export async function rental(who: People, status: string, fields: object = {}): Promise<string> {
  const id = randomUUID();
  await getFirestore()
    .collection("rentals")
    .doc(id)
    .set({
      ownerId: who.lender,
      borrowerId: who.borrower,
      status,
      deposit: 40,
      totalPrice: 15,
      listingTitle: "Camping tent",
      ...fields,
    });
  return id;
}

export async function fund(uid: string, wallet: object): Promise<void> {
  await getFirestore().collection("wallets").doc(uid).set(wallet);
}

export async function wallet(uid: string) {
  return (await getFirestore().collection("wallets").doc(uid).get()).data();
}

export async function rows(uid: string) {
  const snapshot = await getFirestore()
    .collection("wallets")
    .doc(uid)
    .collection("transactions")
    .get();
  return snapshot.docs.map((doc) => ({ id: doc.id, ...doc.data() }));
}
