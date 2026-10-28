// Made with Claude.

import { getFirestore } from "firebase-admin/firestore";
import { defineSecret } from "firebase-functions/params";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { googleGeocoder } from "./geocoder";
import { updateListingAreas } from "./listingAreas";

/** The Geocoding API key, kept in Secret Manager (see "Cloud Functions" in README.md). */
const geocodingApiKey = defineSecret("GEOCODING_API_KEY");

/**
 * When a listing's exact addresses are written, puts their area names and blurred points on the
 * public listing (see "Firestore schema" in docs/ARCHITECTURE.md). Triggered by the private
 * document, so its own write to the listing never triggers it again.
 */
export const geocodeListingAddresses = onDocumentWritten(
  { document: "listings/{listingId}/private/addresses", secrets: [geocodingApiKey] },
  (event) =>
    updateListingAreas(
      getFirestore(),
      event.params.listingId,
      event.data?.before.data(),
      event.data?.after.data(),
      googleGeocoder(geocodingApiKey.value()),
    ),
);
