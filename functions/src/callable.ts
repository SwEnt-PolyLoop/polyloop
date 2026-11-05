// Made with Claude.

import { HttpsError } from "firebase-functions/v2/https";
import { RequestError } from "./rentalRules";

/**
 * Turns a refusal of the rules (a RequestError) into the HttpsError the app receives. Any other
 * error is returned unchanged, so Firebase reports it as an internal error.
 */
export function toCallableError(error: unknown): unknown {
  return error instanceof RequestError ? new HttpsError(error.code, error.message) : error;
}
