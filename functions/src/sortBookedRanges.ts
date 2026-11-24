// Made with Claude.

import { initializeApp } from "firebase-admin/app";
import { Firestore, getFirestore } from "firebase-admin/firestore";
import { bookedRanges, dayNumber, sortedRanges } from "./rentalRules";

/**
 * One-off backfill for the listing lock (#341): stores the `bookedRanges` of every listing as Cloud
 * Functions now keep them, its valid ranges sorted by first day (`sortedRanges`). Until then,
 * `respondToOffer` appended each accepted range, so a listing that accepted a booking earlier than
 * one it already had stored them out of order, and the Security Rules, which read only the last
 * range, would leave it unlocked. Lists already stored that way are not written, and each listing
 * is checked again and rewritten in a transaction, so a booking accepted meanwhile is kept. Returns
 * the number of listings rewritten; a second run rewrites none.
 *
 * Run it once against the project, after the Cloud Functions that keep the order are deployed. In
 * functions/: `npm run build`, then `node lib/sortBookedRanges.js`, with GOOGLE_CLOUD_PROJECT set to
 * the project id and GOOGLE_APPLICATION_CREDENTIALS to a service account key of the project (never
 * committed).
 */
export async function sortBookedRanges(db: Firestore): Promise<number> {
  const listings = await db.collection("listings").get();
  let rewritten = 0;
  for (const listing of listings.docs) {
    if (isStoredInOrder(listing.get("bookedRanges"))) continue;
    const written = await db.runTransaction(async (tx) => {
      const stored = (await tx.get(listing.ref)).get("bookedRanges");
      if (isStoredInOrder(stored)) return false;
      tx.update(listing.ref, { bookedRanges: sortedRanges(stored) });
      return true;
    });
    if (written) rewritten++;
  }
  return rewritten;
}

/**
 * True if [stored] needs no rewrite: not a list (a listing without bookings), or a list of valid
 * ranges sorted by first day.
 */
function isStoredInOrder(stored: unknown): boolean {
  if (!Array.isArray(stored)) return true;
  const ranges = bookedRanges(stored);
  return (
    ranges.length === stored.length &&
    ranges.every((range, i) => i === 0 || dayNumber(ranges[i - 1].start) <= dayNumber(range.start))
  );
}

// Run from the command line only, never when imported (by the tests).
if (require.main === module) {
  initializeApp();
  sortBookedRanges(getFirestore()).then(
    (rewritten) => console.log(`Sorted the bookedRanges of ${rewritten} listing(s).`),
    (error) => {
      console.error(error);
      process.exitCode = 1;
    },
  );
}
