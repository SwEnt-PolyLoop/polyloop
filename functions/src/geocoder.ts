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
 * A [Geocoder] that calls Google's Geocoding API with [apiKey], preferring Swiss results and
 * English names. Errors never include the key or the address, so they can be logged.
 */
export function googleGeocoder(apiKey: string, fetchUrl: typeof fetch = fetch): Geocoder {
  return async (address) => {
    const query = new URLSearchParams({ address, region: "ch", language: "en", key: apiKey });
    const response = await fetchUrl(`${GEOCODING_URL}?${query}`);
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
    return {
      point: { latitude: result.geometry.location.lat, longitude: result.geometry.location.lng },
      area: area ?? "",
    };
  };
}
