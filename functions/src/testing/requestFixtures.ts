// Made with Claude.

import { randomUUID } from "node:crypto";
import { getFirestore } from "firebase-admin/firestore";
import { RentalRequestInput } from "../rentalRequest";

/** 10 March 2030 in Zurich (11:00 there). */
export const NOW = new Date("2030-03-10T10:00:00Z");

/** The balance [people] gives a borrower: enough for any request of these tests. */
export const RICH = 1000;

/**
 * A fresh lender and borrower, each with a profile; the borrower's wallet holds [available] PP,
 * [RICH] unless a test sets less.
 */
export async function people(
  available = RICH,
): Promise<{ lender: string; borrower: string }> {
  const db = getFirestore();
  const lender = `lender-${randomUUID()}`;
  const borrower = `borrower-${randomUUID()}`;
  await db.collection("users").doc(lender).set({ name: " Alice Martin " });
  await db.collection("users").doc(borrower).set({ name: "Bob Keller" });
  await db.collection("wallets").doc(borrower).set({ available });
  return { lender, borrower };
}

/**
 * A published listing of [ownerId] at 12 PP a day, available in March and April 2030, with
 * [fields] on top.
 */
export async function listing(ownerId: string, fields: object = {}): Promise<string> {
  const id = randomUUID();
  await getFirestore()
    .collection("listings")
    .doc(id)
    .set({
      ownerId,
      title: "Camping tent",
      photoUrls: ["https://photos/0.jpg", "https://photos/1.jpg"],
      pricePerDay: 12,
      itemValue: 200,
      availableFrom: "2030-03-01",
      availableTo: "2030-04-30",
      status: "PUBLISHED",
      pickupArea: "Ecublens",
      dropOffArea: "Renens",
      bookedRanges: [],
      ...fields,
    });
  return id;
}

/** A request of [listingId], by default for 3 days, so 36 PP at 12 PP a day. */
export function input(
  listingId: string,
  startDate = "2030-03-20",
  endDate = "2030-03-22",
): RentalRequestInput {
  return { listingId, startDate, endDate };
}

/** How many rentals exist for [listingId]. */
export async function rentalsOf(listingId: string): Promise<number> {
  const rentals = getFirestore().collection("rentals").where("listingId", "==", listingId);
  return (await rentals.get()).size;
}
