// Made with Claude.

import { getAuth } from "firebase-admin/auth";
import { logger } from "firebase-functions/v2";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { deleteExpiredSignUps } from "./expiredSignUps";

/** Runs the sweep every 5 minutes, so an unverified account is gone 5 to 10 minutes after sign-up. */
export const deleteExpiredSignUpsEvery5Minutes = onSchedule(
  { schedule: "every 5 minutes", timeZone: "Europe/Zurich" },
  async () => {
    const { deleted, failed } = await deleteExpiredSignUps(getAuth(), Date.now());
    if (failed > 0) {
      logger.warn(`Deleted ${deleted} expired sign-ups; ${failed} could not be deleted.`);
    } else {
      logger.info(`Deleted ${deleted} expired sign-ups.`);
    }
  },
);
