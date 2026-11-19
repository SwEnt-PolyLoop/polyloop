// Made with Claude.

import { getFirestore } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { toCallableError } from "./callable";
import { createRentalRequest, parseRentalRequestInput } from "./rentalRequest";
import { requireVerifiedEpfl } from "./rentalRules";

/**
 * Callable `requestRental` (see docs/ARCHITECTURE.md): the borrower asks to rent a listing for some
 * days. Input: `{listingId, startDate, endDate}` (`YYYY-MM-DD`). Returns `{rentalId}`.
 */
export const requestRental = onCall(async (request) => {
  try {
    const uid = requireVerifiedEpfl(request.auth);
    const rentalId = await createRentalRequest(
      getFirestore(),
      uid,
      parseRentalRequestInput(request.data),
    );
    return { rentalId };
  } catch (error) {
    throw toCallableError(error);
  }
});
