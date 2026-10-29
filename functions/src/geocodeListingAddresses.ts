// Made with Claude.

import { getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { defineSecret } from "firebase-functions/params";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { googleGeocoder } from "./geocoder";
import { updateListingAreas } from "./listingAreas";

/** The Geocoding API key, kept in Secret Manager (see "Cloud Functions" in README.md). */
const geocodingApiKey = defineSecret("GEOCODING_API_KEY");

/**
 * How long a failed event keeps being retried: about as long as Google keeps delivering it, made
 * explicit so that a lasting failure (e.g. a wrong key) never retries for longer.
 */
const RETRY_FOR_MS = 24 * 60 * 60 * 1000;

/**
 * When a listing's exact addresses are written, puts their area names and blurred points on the
 * public listing (see "Firestore schema" in docs/ARCHITECTURE.md). Triggered by the private
 * document, so its own write to the listing never triggers it again. A run that fails (e.g. the
 * Geocoding API is down) is retried with backoff, until the event is a day old.
 */
export const geocodeListingAddresses = onDocumentWritten(
  { document: "listings/{listingId}/private/addresses", secrets: [geocodingApiKey], retry: true },
  async (event) => {
    if (Date.now() - Date.parse(event.time) > RETRY_FOR_MS) {
      logger.error(`Gave up geocoding the addresses of listing ${event.params.listingId}`);
      return;
    }
    await updateListingAreas(
      getFirestore(),
      event.params.listingId,
      event.data?.before.data(),
      event.data?.after.data(),
      googleGeocoder(geocodingApiKey.value()),
    );
  },
);
