// Made with Claude.

import { getFirestore } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { cancelInTransaction } from "./cancel";
import { toCallableError } from "./callable";
import { parseRentalId, requireVerifiedEpfl } from "./rentalRules";

/**
 * Callable `cancelRental` (see docs/ARCHITECTURE.md): either participant cancels a request, or an
 * accepted rental until the day before pickup, which gives the deposit back. Input: `{rentalId}`.
 * A refusal the app tells apart carries `details.reason`: `RENTAL_CHANGED` or `TOO_LATE`.
 * Returns {}.
 */
export const cancelRental = onCall(async (request) => {
  try {
    const uid = requireVerifiedEpfl(request.auth);
    await cancelInTransaction(getFirestore(), uid, parseRentalId(request.data));
    return {};
  } catch (error) {
    throw toCallableError(error);
  }
});
