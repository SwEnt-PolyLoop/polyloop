// Made with Claude.

import { DocumentReference, Firestore, Timestamp } from "firebase-admin/firestore";
import { decline } from "./negotiation";

/** What a sweep did: the offers it expired, and those it could not, which the next sweep retries. */
export interface SweepResult {
  expired: number;
  failed: number;
}

/**
 * Declines every rental whose offer has expired at [now]. One failure does not stop the others. A
 * finished negotiation has no `offer` left, so the query, on one field and needing no composite
 * index, only finds offers still waiting.
 *
 * @param db the Firestore database.
 * @param now the current time, passed so that tests can set it.
 * @returns how many offers were expired and how many could not be.
 */
export async function expireOverdueOffers(db: Firestore, now: Date): Promise<SweepResult> {
  const overdue = await db
    .collection("rentals")
    .where("offer.expiresAt", "<=", Timestamp.fromDate(now))
    .get();
  const results = await Promise.allSettled(overdue.docs.map((doc) => expireOffer(doc.ref, now)));
  return {
    expired: results.filter((result) => result.status === "fulfilled" && result.value).length,
    failed: results.filter((result) => result.status === "rejected").length,
  };
}

/**
 * Declines one rental if its offer has still expired at [now], in one transaction. The check is
 * made again because the offer may have been answered or countered since the query.
 *
 * @param rentalRef the rental to check.
 * @param now the current time.
 * @returns true if the rental was declined, false if there was nothing to do.
 */
export async function expireOffer(rentalRef: DocumentReference, now: Date): Promise<boolean> {
  return rentalRef.firestore.runTransaction(async (tx) => {
    const rental = (await tx.get(rentalRef)).data();
    const expiresAt: Timestamp | undefined = rental?.offer?.expiresAt;
    if (rental?.status !== "REQUESTED" || !expiresAt || expiresAt.toMillis() > now.getTime()) {
      return false;
    }
    decline(tx, rentalRef, "Offer expired · request declined");
    return true;
  });
}
