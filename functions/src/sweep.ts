// Made with Claude.

import { DocumentReference } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";

/** What a sweep did: the documents it changed, and those it could not handle. */
export interface SweepCounts {
  done: number;
  failed: number;
}

/**
 * Runs [handle] on every document a scheduled sweep found, all at once. A failure is logged with
 * [failureMessage] and does not stop the others; the next sweep retries it.
 *
 * @param refs the documents the sweep's query found.
 * @param handle checks and changes one document in its own transaction; true if it changed it.
 * @param failureMessage the logged text for a document that failed, from its id.
 * @returns how many documents were changed and how many failed.
 */
export async function runSweep(
  refs: DocumentReference[],
  handle: (ref: DocumentReference) => Promise<boolean>,
  failureMessage: (id: string) => string,
): Promise<SweepCounts> {
  const results = await Promise.allSettled(
    refs.map((ref) =>
      handle(ref).catch((error) => {
        logger.error(failureMessage(ref.id), error);
        throw error;
      }),
    ),
  );
  return {
    done: results.filter((result) => result.status === "fulfilled" && result.value).length,
    failed: results.filter((result) => result.status === "rejected").length,
  };
}
