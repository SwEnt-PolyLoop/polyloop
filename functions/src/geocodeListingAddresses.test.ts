// Made with Claude.

import { getApps, initializeApp } from "firebase-admin/app";
import * as logger from "firebase-functions/logger";
import { geocodeListingAddresses } from "./geocodeListingAddresses";
import { updateListingAreas } from "./listingAreas";

// The Firestore logic is tested on the emulator in listingAreas.test.ts; here only the wiring.
// Imported from its own file, not index.ts: index also loads Firebase Auth, which Jest cannot load.
jest.mock("./listingAreas");
jest.mock("firebase-functions/logger");

const update = jest.mocked(updateListingAreas);

// index.ts does this in the deployed functions; the trigger needs it to call getFirestore().
beforeAll(() => {
  if (getApps().length === 0) initializeApp({ projectId: "demo-polyloop" });
});

type WriteEvent = Parameters<typeof geocodeListingAddresses.run>[0];

/**
 * The event of a write to listings/{listingId}/private/addresses, from [before] to [after], made at
 * [time] (now by default).
 */
function addressesWritten(
  listingId: string,
  before?: object,
  after?: object,
  time = new Date(),
): WriteEvent {
  const snapshot = (data?: object) => ({ data: () => data });
  return {
    params: { listingId },
    time: time.toISOString(),
    data: { before: snapshot(before), after: snapshot(after) },
  } as unknown as WriteEvent;
}

beforeEach(() => update.mockClear());

describe("geocodeListingAddresses", () => {
  it("listens to the private addresses of every listing, with the Geocoding key", () => {
    const endpoint = geocodeListingAddresses.__endpoint;

    expect(endpoint.eventTrigger?.eventFilterPathPatterns).toEqual({
      document: "listings/{listingId}/private/addresses",
    });
    expect(endpoint.secretEnvironmentVariables).toEqual([{ key: "GEOCODING_API_KEY" }]);
  });

  it("is retried when a run fails", () => {
    expect(geocodeListingAddresses.__endpoint.eventTrigger?.retry).toBe(true);
  });

  it("gives up on an event older than a day, without geocoding it", async () => {
    const dayAndAMinuteAgo = new Date(Date.now() - (24 * 60 + 1) * 60 * 1000);

    await geocodeListingAddresses.run(
      addressesWritten("listing-1", undefined, { pickup: "New" }, dayAndAMinuteAgo),
    );

    expect(update).not.toHaveBeenCalled();
    expect(jest.mocked(logger.error)).toHaveBeenCalledWith(
      "Gave up geocoding the addresses of listing listing-1",
    );
  });

  it("passes the listing id and the addresses before and after the write", async () => {
    await geocodeListingAddresses.run(
      addressesWritten("listing-1", { pickup: "Old" }, { pickup: "New" }),
    );

    expect(update).toHaveBeenCalledTimes(1);
    expect(update).toHaveBeenCalledWith(
      expect.anything(),
      "listing-1",
      { pickup: "Old" },
      { pickup: "New" },
      expect.any(Function),
    );
  });

  it("geocodes with the key from Secret Manager", async () => {
    process.env.GEOCODING_API_KEY = "test-key";
    const fetchSpy = jest
      .spyOn(global, "fetch")
      .mockResolvedValue(new Response(JSON.stringify({ status: "ZERO_RESULTS", results: [] })));

    await geocodeListingAddresses.run(addressesWritten("listing-1", undefined, { pickup: "New" }));
    const geocode = update.mock.calls[0][4];
    await geocode("Route Cantonale 1, 1015 Ecublens");

    const url = new URL(String(fetchSpy.mock.calls[0][0]));
    expect(url.searchParams.get("key")).toBe("test-key");
    fetchSpy.mockRestore();
    delete process.env.GEOCODING_API_KEY;
  });
});
