// Edited with Claude.
// Cloud Functions for PolyLoop. Each function lives in its own file and is exported here.
import { initializeApp } from "firebase-admin/app";
import { setGlobalOptions } from "firebase-functions/v2";

// Run next to the Firestore database (eur3, Belgium and the Netherlands).
setGlobalOptions({ region: "europe-west1" });

// Lets the functions use the Admin SDK (e.g. Firebase Auth) with the project's own credentials.
initializeApp();

export { deleteExpiredSignUpsEvery5Minutes } from "./deleteExpiredSignUps";
export { geocodeListingAddresses } from "./geocodeListingAddresses";
