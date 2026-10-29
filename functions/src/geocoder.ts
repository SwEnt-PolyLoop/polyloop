// Made with Claude.

import { LatLng } from "./blur";

/** Where an address is: its exact point and the name of its area (e.g. "Ecublens"). */
export interface GeocodedAddress {
  point: LatLng;
  area: string;
}

/** Finds an address; resolves to null when it does not exist, rejects when the lookup fails. */
export type Geocoder = (address: string) => Promise<GeocodedAddress | null>;

const GEOCODING_URL = "https://maps.googleapis.com/maps/api/geocode/json";

/** How long a lookup may take before it fails, well within the function's own 60 s limit. */
const TIMEOUT_MS = 10_000;

/**
 * Address parts that can name the area, most precise first: the town, or the closest larger area
 * when an address has no town.
 */
const AREA_TYPES = [
  "locality",
  "postal_town",
  "administrative_area_level_3",
  "administrative_area_level_2",
  "administrative_area_level_1",
];

interface GeocodingResponse {
  status: string;
  results?: {
    geometry: { location: { lat: number; lng: number } };
    address_components: { long_name: string; types: string[] }[];
  }[];
}

/**
 * A [Geocoder] that calls Google's Geocoding API with [apiKey], looking only in Switzerland so that
 * a mistyped address cannot land abroad, with English names. A lookup that takes longer than
 * [timeoutMs] fails. Errors never include the key or the address, so they can be logged.
 *
 * A partial match (Google matched only part of the address, e.g. a mistyped street) is kept: only
 * its town and a point rounded to about 1 km become public, which such a match usually gets right.
 * A result that names no town or larger area, such as Switzerland as a whole, counts as not found.
 */
export function googleGeocoder(
  apiKey: string,
  fetchUrl: typeof fetch = fetch,
  timeoutMs: number = TIMEOUT_MS,
): Geocoder {
  return async (address) => {
    const query = new URLSearchParams({
      address,
      components: "country:CH",
      language: "en",
      key: apiKey,
    });
    let response: Response;
    try {
      response = await fetchUrl(`${GEOCODING_URL}?${query}`, {
        signal: AbortSignal.timeout(timeoutMs),
      });
    } catch (error) {
      // fetch's own errors (no connection, timeout) are replaced: they could name the URL, so the key.
      throw new Error(`Geocoding API request failed (${(error as Error).name})`);
    }
    if (!response.ok) {
      throw new Error(`Geocoding API answered HTTP ${response.status}`);
    }
    const body = (await response.json()) as GeocodingResponse;
    if (body.status === "ZERO_RESULTS") return null;
    const result = body.results?.[0];
    if (body.status !== "OK" || result === undefined) {
      throw new Error(`Geocoding API answered ${body.status}`);
    }
    const area = AREA_TYPES.map(
      (type) => result.address_components.find((part) => part.types.includes(type))?.long_name,
    ).find((name) => name !== undefined);
    if (area === undefined) return null;
    return {
      point: { latitude: result.geometry.location.lat, longitude: result.geometry.location.lng },
      area,
    };
  };
}
