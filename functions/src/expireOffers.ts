// Made with Claude.

import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { expireOverdueOffers } from "./negotiation";

/** Runs the sweep every hour; `respondToOffer` also refuses an expired offer between two runs. */
export const expireOffers = onSchedule(
  { schedule: "every 60 minutes", timeZone: "Europe/Zurich" },
  async () => {
    const { expired, failed } = await expireOverdueOffers(getFirestore(), new Date());
    if (failed > 0) {
      logger.warn(`Expired ${expired} offers; ${failed} could not be expired.`);
    } else {
      logger.info(`Expired ${expired} offers.`);
    }
  },
);
