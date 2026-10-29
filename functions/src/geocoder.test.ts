// Made with Claude.

import { googleGeocoder } from "./geocoder";

/** A fetch that answers [body] with [status] and records the requested URL. */
function fakeFetch(body: unknown, status = 200) {
  const urls: URL[] = [];
  const fetchUrl = (async (input: string | URL | Request) => {
    urls.push(new URL(input.toString()));
    return new Response(JSON.stringify(body), { status });
  }) as typeof fetch;
  return { fetchUrl, urls };
}

const rolexCenter = {
  status: "OK",
  results: [
    {
      geometry: { location: { lat: 46.51843, lng: 6.56826 } },
      address_components: [
        { long_name: "Route Cantonale", types: ["route"] },
        { long_name: "Ecublens", types: ["locality", "political"] },
        { long_name: "Vaud", types: ["administrative_area_level_1", "political"] },
      ],
    },
  ],
};

describe("googleGeocoder", () => {
  it("returns the exact point and the town of the first result", async () => {
    const { fetchUrl } = fakeFetch(rolexCenter);

    const found = await googleGeocoder("test-key", fetchUrl)("Route Cantonale, Ecublens");

    expect(found).toEqual({ point: { latitude: 46.51843, longitude: 6.56826 }, area: "Ecublens" });
  });

  it("asks for the address in Switzerland, in English, with the key", async () => {
    const { fetchUrl, urls } = fakeFetch(rolexCenter);

    await googleGeocoder("test-key", fetchUrl)("Route Cantonale, Ecublens");

    expect(urls).toHaveLength(1);
    expect(urls[0].origin + urls[0].pathname).toBe(
      "https://maps.googleapis.com/maps/api/geocode/json",
    );
    expect(urls[0].searchParams.get("address")).toBe("Route Cantonale, Ecublens");
    expect(urls[0].searchParams.get("region")).toBe("ch");
    expect(urls[0].searchParams.get("language")).toBe("en");
    expect(urls[0].searchParams.get("key")).toBe("test-key");
  });

  it("names the area after the closest larger area when there is no town", async () => {
    const withoutTown = structuredClone(rolexCenter);
    withoutTown.results[0].address_components = [
      { long_name: "Vaud", types: ["administrative_area_level_1", "political"] },
      { long_name: "Ouest lausannois", types: ["administrative_area_level_2", "political"] },
    ];
    const { fetchUrl } = fakeFetch(withoutTown);

    const found = await googleGeocoder("test-key", fetchUrl)("Somewhere");

    expect(found?.area).toBe("Ouest lausannois");
  });

  it("leaves the area empty when no part names one", async () => {
    const noArea = structuredClone(rolexCenter);
    noArea.results[0].address_components = [{ long_name: "Route Cantonale", types: ["route"] }];
    const { fetchUrl } = fakeFetch(noArea);

    const found = await googleGeocoder("test-key", fetchUrl)("Route Cantonale");

    expect(found?.area).toBe("");
  });

  it("resolves to null when the address is not found", async () => {
    const { fetchUrl } = fakeFetch({ status: "ZERO_RESULTS", results: [] });

    expect(await googleGeocoder("test-key", fetchUrl)("Nowhere 99")).toBeNull();
  });

  it("rejects without the key or the address when the API refuses", async () => {
    const { fetchUrl } = fakeFetch({ status: "REQUEST_DENIED", error_message: "test-key is bad" });

    const error = await googleGeocoder("test-key", fetchUrl)("Route Cantonale").catch((e) => e);

    expect(error).toBeInstanceOf(Error);
    expect(error.message).toBe("Geocoding API answered REQUEST_DENIED");
  });

  it("rejects when the API answers an HTTP error", async () => {
    const { fetchUrl } = fakeFetch({}, 503);

    await expect(googleGeocoder("test-key", fetchUrl)("Route Cantonale")).rejects.toThrow(
      "Geocoding API answered HTTP 503",
    );
  });

  it("gives up when the API does not answer in time", async () => {
    // Never answers: it only rejects once the request is aborted, as fetch does.
    const silent = ((_input: string | URL | Request, init?: RequestInit) =>
      new Promise((_resolve, reject) => {
        init?.signal?.addEventListener("abort", () => reject(init.signal?.reason));
      })) as typeof fetch;

    await expect(googleGeocoder("test-key", silent, 10)("Route Cantonale")).rejects.toThrow(
      "Geocoding API request failed (TimeoutError)",
    );
  });

  it("rejects without the key or the address when the request itself fails", async () => {
    // The worst case: an error from fetch that repeats the whole URL.
    const offline = (async (input: string | URL | Request) => {
      throw new TypeError(`fetch failed: ${input.toString()}`);
    }) as typeof fetch;

    const error = await googleGeocoder("test-key", offline)("Route Cantonale").catch((e) => e);

    expect(error.message).toBe("Geocoding API request failed (TypeError)");
    expect(error.message).not.toContain("test-key");
    expect(error.message).not.toContain("Cantonale");
  });
});
