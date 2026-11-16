// Made with Claude.

import { getFirestore } from "firebase-admin/firestore";
import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import { draftLlmProposal as draft } from "./llmProposal";

/**
 * When a dispute starts a new attempt at its LLM proposal (it escalated, or a party asked to try
 * again), writes that attempt's result (see "Disputes" in docs/ARCHITECTURE.md). A run that fails
 * (e.g. Firestore is unavailable) is delivered again.
 */
export const draftLlmProposal = onDocumentUpdated(
  { document: "disputes/{rentalId}", retry: true },
  async (event) => {
    await draft(
      getFirestore(),
      event.params.rentalId,
      event.data?.before.data(),
      event.data?.after.data(),
    );
  },
);
