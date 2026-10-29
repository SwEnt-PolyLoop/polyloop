// Made with Claude.

import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { DocumentData, Firestore, GeoPoint, getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { GeocodedAddress, Geocoder } from "./geocoder";
import { LISTINGS, updateListingAreas } from "./listingAreas";

// Records what the function logs instead of printing it.
jest.mock("firebase-functions/logger");

const PICKUP = "Route Cantonale 1, 1015 Ecublens";
const DROP_OFF = "Rue de Lausanne 10, 1020 Renens";
const OTHER_DROP_OFF = "Avenue du 14-Avril 5, 1020 Renens";

const KNOWN: Record<string, GeocodedAddress> = {
  [PICKUP]: { point: { latitude: 46.51843, longitude: 6.56826 }, area: "Ecublens" },
  [DROP_OFF]: { point: { latitude: 46.53712, longitude: 6.58954 }, area: "Renens" },
  [OTHER_DROP_OFF]: { point: { latitude: 46.53421, longitude: 6.58012 }, area: "Renens" },
};

/** The fields the app writes when it creates a listing, before the areas are computed. */
const NEW_LISTING = { title: "Camping tent", pickupArea: "", dropOffArea: "" };

/** The listing once both addresses are geocoded: names and blurred points only. */
const GEOCODED_LISTING = {
  title: "Camping tent",
  pickupArea: "Ecublens",
  pickupPoint: new GeoPoint(46.52, 6.57),
  dropOffArea: "Renens",
  dropOffPoint: new GeoPoint(46.54, 6.59),
};

let db: Firestore;

beforeAll(() => {
  // Without the emulator, the Admin SDK would write to the real project.
  if (!process.env.FIRESTORE_EMULATOR_HOST) {
    throw new Error(
      "These tests need the Firestore emulator. In functions/, run: " +
        'firebase emulators:exec --only firestore "npm test"',
    );
  }
  if (getApps().length === 0) initializeApp({ projectId: process.env.GCLOUD_PROJECT });
  db = getFirestore();
});

/** A geocoder that knows [KNOWN] (anything else is not found) and records each lookup. */
function fakeGeocoder(failing: string[] = []) {
  const lookups: string[] = [];
  const geocode: Geocoder = async (address) => {
    lookups.push(address);
    if (failing.includes(address)) throw new Error("Geocoding API answered OVER_QUERY_LIMIT");
    return KNOWN[address] ?? null;
  };
  return { geocode, lookups };
}

/** Writes a listing with a new id, as the app does, and returns that id. */
async function createListing(fields: DocumentData = NEW_LISTING): Promise<string> {
  const id = `test-${randomUUID()}`;
  await db.collection(LISTINGS).doc(id).set(fields);
  return id;
}

async function readListing(id: string): Promise<DocumentData | undefined> {
  return (await db.collection(LISTINGS).doc(id).get()).data();
}

/**
 * Runs updateListingAreas as the trigger does after [id]'s addresses were written from [before] to
 * [after]: the addresses document already holds [after], or is gone when it is undefined.
 */
async function addressesWritten(
  id: string,
  before: DocumentData | undefined,
  after: DocumentData | undefined,
  geocode: Geocoder,
): Promise<void> {
  const addresses = db.collection(LISTINGS).doc(id).collection("private").doc("addresses");
  await (after === undefined ? addresses.delete() : addresses.set(after));
  await updateListingAreas(db, id, before, after, geocode);
}

describe("updateListingAreas", () => {
  it("puts the area names and blurred points of new addresses on the listing", async () => {
    const id = await createListing();
    const { geocode, lookups } = fakeGeocoder();

    await addressesWritten(id, undefined, { pickup: PICKUP, dropOff: DROP_OFF }, geocode);

    expect(await readListing(id)).toEqual(GEOCODED_LISTING);
    expect(lookups).toEqual([PICKUP, DROP_OFF]);
  });

  it("never writes the exact addresses or points on the listing", async () => {
    const id = await createListing();

    await addressesWritten(
      id,
      undefined,
      { pickup: PICKUP, dropOff: DROP_OFF },
      fakeGeocoder().geocode,
    );

    const stored = JSON.stringify(await readListing(id));
    for (const exact of [PICKUP, DROP_OFF, "46.51843", "6.56826", "46.53712", "6.58954"]) {
      expect(stored).not.toContain(exact);
    }
  });

  it("does nothing when the addresses did not change", async () => {
    const id = await createListing(GEOCODED_LISTING);
    const addresses = { pickup: PICKUP, dropOff: DROP_OFF };
    const { geocode, lookups } = fakeGeocoder();

    await addressesWritten(id, addresses, { ...addresses }, geocode);

    expect(lookups).toEqual([]);
    expect(await readListing(id)).toEqual(GEOCODED_LISTING);
  });

  it("geocodes only the address that changed", async () => {
    const id = await createListing(GEOCODED_LISTING);
    const { geocode, lookups } = fakeGeocoder();

    await addressesWritten(
      id,
      { pickup: PICKUP, dropOff: DROP_OFF },
      { pickup: PICKUP, dropOff: OTHER_DROP_OFF },
      geocode,
    );

    expect(lookups).toEqual([OTHER_DROP_OFF]);
    expect(await readListing(id)).toEqual({
      ...GEOCODED_LISTING,
      dropOffPoint: new GeoPoint(46.53, 6.58),
    });
  });

  it("removes the area and point of an address that is not found", async () => {
    const id = await createListing(GEOCODED_LISTING);

    await addressesWritten(
      id,
      { pickup: PICKUP, dropOff: DROP_OFF },
      { pickup: PICKUP, dropOff: "Nowhere 99" },
      fakeGeocoder().geocode,
    );

    const { dropOffArea, dropOffPoint, ...rest } = GEOCODED_LISTING;
    expect(await readListing(id)).toEqual(rest);
  });

  it("removes the area and point when geocoding fails, and logs it without the address", async () => {
    const logged = jest.mocked(logger.error);
    logged.mockClear();
    const id = await createListing();

    await addressesWritten(
      id,
      undefined,
      { pickup: PICKUP, dropOff: DROP_OFF },
      fakeGeocoder([PICKUP]).geocode,
    );

    const { pickupArea, pickupPoint, ...rest } = GEOCODED_LISTING;
    expect(await readListing(id)).toEqual(rest);
    expect(logged).toHaveBeenCalledTimes(1);
    expect(JSON.stringify(logged.mock.calls)).not.toContain(PICKUP);
  });

  it("treats a blank address like a missing one, without looking it up", async () => {
    const id = await createListing();
    const { geocode, lookups } = fakeGeocoder();

    await addressesWritten(id, undefined, { pickup: "   ", dropOff: DROP_OFF }, geocode);

    const { pickupArea, pickupPoint, ...rest } = GEOCODED_LISTING;
    expect(await readListing(id)).toEqual(rest);
    expect(lookups).toEqual([DROP_OFF]);
  });

  it("does not recreate a listing deleted in the meantime", async () => {
    const id = `test-${randomUUID()}`;

    await addressesWritten(
      id,
      undefined,
      { pickup: PICKUP, dropOff: DROP_OFF },
      fakeGeocoder().geocode,
    );

    expect(await readListing(id)).toBeUndefined();
  });

  it("does nothing when the addresses are deleted", async () => {
    const id = await createListing(GEOCODED_LISTING);
    const { geocode, lookups } = fakeGeocoder();

    await addressesWritten(id, { pickup: PICKUP, dropOff: DROP_OFF }, undefined, geocode);

    expect(lookups).toEqual([]);
    expect(await readListing(id)).toEqual(GEOCODED_LISTING);
  });

  it("keeps the newest drop-off when the run for an older one finishes last", async () => {
    const id = await createListing();
    const first = { pickup: PICKUP, dropOff: DROP_OFF };
    const second = { pickup: PICKUP, dropOff: OTHER_DROP_OFF };
    // The owner saved [first], then [second]; the run for [second] finishes first.
    await addressesWritten(id, first, second, fakeGeocoder().geocode);

    // The run for [first] finishes late, while the document already holds [second].
    await updateListingAreas(db, id, undefined, first, fakeGeocoder().geocode);

    expect(await readListing(id)).toEqual({
      ...GEOCODED_LISTING,
      dropOffPoint: new GeoPoint(46.53, 6.58),
    });
  });
});
