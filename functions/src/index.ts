// Cloud Functions for PolyLoop. Each function lives in its own file and is exported here.
import { setGlobalOptions } from "firebase-functions/v2";

// Run next to the Firestore database (eur3, Belgium and the Netherlands).
setGlobalOptions({ region: "europe-west1" });
