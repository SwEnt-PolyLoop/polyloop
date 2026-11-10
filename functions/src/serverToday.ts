// Made with Claude.

import { onCall } from "firebase-functions/v2/https";
import { toCallableError } from "./callable";
import { requireVerifiedEpfl } from "./rentalRules";

// Rental dates are Swiss days, so today is the day in Zurich, not in UTC: between midnight and
// 1 a.m. (2 a.m. in summer) in Zurich, UTC is still on the day before.
const zurichDate = new Intl.DateTimeFormat("en-US", {
  timeZone: "Europe/Zurich",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

/**
 * The day in Zurich at [now], as `YYYY-MM-DD`. It takes the time as a parameter so that tests can
 * pass fixed instants.
 */
export function zurichDay(now: Date): string {
  // Built from the parts, as each locale orders and separates them its own way.
  const parts = Object.fromEntries(
    zurichDate.formatToParts(now).map((part) => [part.type, part.value]),
  );
  return `${parts.year}-${parts.month}-${parts.day}`;
}

/**
 * Callable `serverToday`: today's date in Zurich, as `{today: "YYYY-MM-DD"}`. No input. The app asks
 * it instead of reading the phone's date, which the user can change in the phone's settings.
 */
export const serverToday = onCall(async (request) => {
  try {
    requireVerifiedEpfl(request.auth);
    return { today: zurichDay(new Date()) };
  } catch (error) {
    throw toCallableError(error);
  }
});
