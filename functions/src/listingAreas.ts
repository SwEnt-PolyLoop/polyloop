// Made with Claude.

import { DocumentData, FieldValue, Firestore, GeoPoint } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { blurPoint } from "./blur";
import { GeocodedAddress, Geocoder } from "./geocoder";

/** Firestore names, as in "Firestore schema" in docs/ARCHITECTURE.md. */
export const LISTINGS = "listings";
const PRIVATE = "private";
const ADDRESSES_DOC = "addresses";

/**
 * The exact addresses in listings/{listingId}/private/addresses, and the public listing fields
 * that each one fills.
 */
const ADDRESSES = [
  { field: "pickup", area: "pickupArea", point: "pickupPoint" },
  { field: "dropOff", area: "dropOffArea", point: "dropOffPoint" },
] as const;

/** One address looked up: its text as it was then, and what the geocoder found (null if nothing). */
interface Lookup {
  address: (typeof ADDRESSES)[number];
  text: string;
  found: GeocodedAddress | null;
}

/**
 * Puts the area name and the blurred point of each changed address of [listingId] on the public
 * listing, given the private addresses document [before] and [after] the write (undefined when it
 * did not exist). The exact addresses and points are never written there.
 *
 * Only addresses whose text changed are geocoded. An address that is blank, not found or cannot be
 * geocoded removes its area and point, so the app shows its placeholder instead of an old area.
 * When a lookup fails, the promise then rejects (after writing the other address), so that the
 * event is delivered again. Nothing happens when the addresses were deleted, and a deleted listing
 * is not recreated. A run that finishes after a newer edit of the addresses writes nothing for the
 * ones changed since.
 */
export async function updateListingAreas(
  db: Firestore,
  listingId: string,
  before: DocumentData | undefined,
  after: DocumentData | undefined,
  geocode: Geocoder,
): Promise<void> {
  if (after === undefined) return;
  const lookups: Lookup[] = [];
  let failed = false;
  for (const address of ADDRESSES) {
    const text = textOf(after[address.field]);
    if (before !== undefined && textOf(before[address.field]) === text) continue;
    let found: GeocodedAddress | null = null;
    try {
      found = text === "" ? null : await geocode(text);
    } catch (error) {
      // The listing id, not the address: logs must not hold exact addresses.
      logger.error(`Could not geocode the ${address.field} address of listing ${listingId}`, error);
      failed = true;
    }
    lookups.push({ address, text, found });
  }
  if (lookups.length > 0) await writeAreas(db, listingId, lookups);
  if (failed) throw new Error(`Could not geocode every changed address of listing ${listingId}`);
}

/**
 * Writes the area and point of each of [lookups] on the listing, in one transaction, and only while
 * its address still has the text that was looked up. Triggers can run in any order, so the run for
 * an older version of the addresses may finish last: it must not overwrite the newer areas. A
 * listing deleted in the meantime is not recreated.
 */
async function writeAreas(db: Firestore, listingId: string, lookups: Lookup[]): Promise<void> {
  const listingRef = db.collection(LISTINGS).doc(listingId);
  const addressesRef = listingRef.collection(PRIVATE).doc(ADDRESSES_DOC);
  await db.runTransaction(async (transaction) => {
    const [listing, addresses] = await transaction.getAll(listingRef, addressesRef);
    if (!listing.exists) return;
    const changes: Record<string, unknown> = {};
    for (const { address, text, found } of lookups) {
      if (textOf(addresses.get(address.field)) !== text) continue;
      if (found === null) {
        changes[address.area] = FieldValue.delete();
        changes[address.point] = FieldValue.delete();
      } else {
        const blurred = blurPoint(found.point);
        changes[address.area] = found.area;
        changes[address.point] = new GeoPoint(blurred.latitude, blurred.longitude);
      }
    }
    if (Object.keys(changes).length > 0) transaction.update(listingRef, changes);
  });
}

function textOf(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}
