// Made with Claude.

import { DocumentData, FieldValue, Firestore, GeoPoint } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { blurPoint } from "./blur";
import { Geocoder } from "./geocoder";

/** Firestore names, as in "Firestore schema" in docs/ARCHITECTURE.md. */
export const LISTINGS = "listings";

/**
 * The exact addresses in listings/{listingId}/private/addresses, and the public listing fields
 * that each one fills.
 */
const ADDRESSES = [
  { field: "pickup", area: "pickupArea", point: "pickupPoint" },
  { field: "dropOff", area: "dropOffArea", point: "dropOffPoint" },
] as const;

/** The gRPC code Firestore uses when an updated document does not exist. */
const NOT_FOUND = 5;

/**
 * Puts the area name and the blurred point of each changed address of [listingId] on the public
 * listing, given the private addresses document [before] and [after] the write (undefined when it
 * did not exist). The exact addresses and points are never written there.
 *
 * Only addresses whose text changed are geocoded. An address that is blank, not found or cannot be
 * geocoded removes its area and point, so the app shows its placeholder instead of an old area.
 * Nothing happens when the addresses were deleted, and a deleted listing is not recreated.
 */
export async function updateListingAreas(
  db: Firestore,
  listingId: string,
  before: DocumentData | undefined,
  after: DocumentData | undefined,
  geocode: Geocoder,
): Promise<void> {
  if (after === undefined) return;
  const changes: Record<string, unknown> = {};
  for (const address of ADDRESSES) {
    const text = textOf(after[address.field]);
    if (before !== undefined && textOf(before[address.field]) === text) continue;
    const found = await locate(text, geocode, listingId, address.field);
    if (found === null) {
      changes[address.area] = FieldValue.delete();
      changes[address.point] = FieldValue.delete();
    } else {
      const blurred = blurPoint(found.point);
      changes[address.area] = found.area;
      changes[address.point] = new GeoPoint(blurred.latitude, blurred.longitude);
    }
  }
  if (Object.keys(changes).length === 0) return;
  try {
    // update(), not set(): it fails instead of recreating a listing deleted in the meantime.
    await db.collection(LISTINGS).doc(listingId).update(changes);
  } catch (error) {
    if ((error as { code?: unknown }).code !== NOT_FOUND) throw error;
  }
}

function textOf(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

/** Geocodes [text], or null when it is blank, not found or the lookup fails (which is logged). */
async function locate(text: string, geocode: Geocoder, listingId: string, field: string) {
  if (text === "") return null;
  try {
    return await geocode(text);
  } catch (error) {
    // The listing id, not the address: logs must not hold exact addresses.
    logger.error(`Could not geocode the ${field} address of listing ${listingId}`, error);
    return null;
  }
}
