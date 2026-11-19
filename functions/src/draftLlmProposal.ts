// Made with Claude.

import { getFirestore } from "firebase-admin/firestore";
import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import { draftLlmProposal as draft } from "./llmProposal";
import { SplitAdvisor } from "./splitAdvisor";

/**
 * How long a failed event keeps being retried, as for `geocodeListingAddresses`: about as long as
 * Google keeps delivering it, made explicit so that a lasting failure never retries for longer.
 * Past it, the proposal fails and the parties can try again or reject.
 */
const RETRY_FOR_MS = 24 * 60 * 60 * 1000;

/**
 * How long one run may take: the model gets up to 40 photos per phase, which can take a minute or
 * more, well past the default 60 s.
 */
const TIMEOUT_SECONDS = 300;

/**
 * The model asked for the split. None until the Vertex AI client exists (#452): every proposal
 * then fails at once, so the parties can already try again or reject.
 */
const advisor: SplitAdvisor | null = null;

/**
 * When a dispute starts a new attempt at its LLM proposal (it escalated, or a party asked to try
 * again), asks the model for a split and stores it (see "Disputes" in docs/ARCHITECTURE.md). A run
 * that fails in a way that may pass (e.g. the model is down) is retried with backoff, until the
 * event is a day old; the proposal then fails.
 */
export const draftLlmProposal = onDocumentUpdated(
  { document: "disputes/{rentalId}", retry: true, timeoutSeconds: TIMEOUT_SECONDS },
  async (event) => {
    await draft(
      getFirestore(),
      event.params.rentalId,
      event.data?.before.data(),
      event.data?.after.data(),
      { advisor, expired: Date.now() - Date.parse(event.time) > RETRY_FOR_MS },
    );
  },
);
