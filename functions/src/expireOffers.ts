// Made with Claude.

import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { SweepResult, expireOverdueOffers, expireUnansweredRequests } from "./negotiation";

/**
 * Runs both sweeps every hour: offers that have expired, and requests that got no offer before
 * their first day. `respondToOffer` also refuses an expired offer between two runs.
 */
export const expireOffers = onSchedule(
  { schedule: "every 60 minutes", timeZone: "Europe/Zurich" },
  async () => {
    const db = getFirestore();
    const now = new Date();
    log("offers", await expireOverdueOffers(db, now));
    log("requests with no offer", await expireUnansweredRequests(db, now));
  },
);

function log(what: string, { expired, failed }: SweepResult): void {
  if (failed > 0) {
    logger.warn(`Expired ${expired} ${what}; ${failed} could not be expired.`);
  } else {
    logger.info(`Expired ${expired} ${what}.`);
  }
}
