// Made with Claude.

import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { escalateOverdueDisputes } from "./dispute";

/**
 * Runs the 30-day escalation every hour: a dispute still in direct settlement past its
 * `escalatesAt` moves on to the LLM proposal by itself.
 */
export const escalateDisputes = onSchedule(
  { schedule: "every 60 minutes", timeZone: "Europe/Zurich" },
  async () => {
    const { done: escalated, failed } = await escalateOverdueDisputes(getFirestore(), new Date());
    if (failed > 0) {
      logger.warn(`Escalated ${escalated} disputes; ${failed} could not be escalated.`);
    } else {
      logger.info(`Escalated ${escalated} disputes.`);
    }
  },
);
