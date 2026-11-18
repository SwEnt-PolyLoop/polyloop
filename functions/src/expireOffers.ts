// Made with Claude.

import { Firestore, getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { SweepResult, expireOverdueOffers, expireUnansweredRequests } from "./negotiation";

/**
 * Runs both sweeps every hour: offers that have expired, and requests that got no offer before
 * their first day. `respondToOffer` also refuses an expired offer between two runs.
 */
export const expireOffers = onSchedule(
  { schedule: "every 60 minutes", timeZone: "Europe/Zurich" },
  async () => runExpirySweeps(getFirestore(), new Date()),
);

/** The two sweeps of [runExpirySweeps]; tests replace them. */
export interface ExpirySweeps {
  offers: (db: Firestore, now: Date) => Promise<SweepResult>;
  requests: (db: Firestore, now: Date) => Promise<SweepResult>;
}

const SWEEPS: ExpirySweeps = { offers: expireOverdueOffers, requests: expireUnansweredRequests };

/**
 * Runs both sweeps side by side, so that one whose query fails (Firestore unavailable, deadline
 * exceeded) does not stop the other. Logs each result, then throws the first failure, so the run
 * still shows as failed; the next hourly run tries again.
 */
export async function runExpirySweeps(
  db: Firestore,
  now: Date,
  sweeps: ExpirySweeps = SWEEPS,
): Promise<void> {
  const [offers, requests] = await Promise.allSettled([
    sweeps.offers(db, now),
    sweeps.requests(db, now),
  ]);
  log("offers", offers);
  log("requests with no offer", requests);
  for (const result of [offers, requests]) {
    if (result.status === "rejected") throw result.reason;
  }
}

function log(what: string, result: PromiseSettledResult<SweepResult>): void {
  if (result.status === "rejected") {
    logger.error(`Could not expire ${what}.`, result.reason);
    return;
  }
  const { expired, failed } = result.value;
  if (failed > 0) {
    logger.warn(`Expired ${expired} ${what}; ${failed} could not be expired.`);
  } else {
    logger.info(`Expired ${expired} ${what}.`);
  }
}
