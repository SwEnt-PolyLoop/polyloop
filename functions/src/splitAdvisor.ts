// Made with Claude.

/**
 * The seam between the LLM proposal and the model that writes it (docs/adr/0001). The prompt, the
 * answer's schema and its checks live in llmProposalRules.ts and do not depend on the provider; an
 * advisor only sends a request and returns the model's answer. `gemini.ts` will be the Vertex AI
 * one; tests use a fake.
 */

/**
 * One part of the request, in order: a text, or a JPEG photo given by its path in the project's
 * default Storage bucket (e.g. `rentals/{rentalId}/photos/{photoId}.jpg`), after the text naming
 * it. The advisor sends each photo the way its provider prefers: Vertex AI reads it from Storage
 * itself, another provider may download it. Only the path is given, never a photo's `url`, which
 * the rules do not check.
 */
export type PromptPart = { text: string } | { photoPath: string };

/** A JSON schema, in the subset that structured-output APIs accept (object, integer, string). */
export type JsonSchema = Record<string, unknown>;

/** Everything a model is sent to propose a split. */
export interface AdvisorRequest {
  /** What the model must do and how to decide, sent as its system instructions. */
  instructions: string;
  /** The evidence: the rental's details, then each photo after its label. */
  parts: PromptPart[];
  /** The shape the answer must follow. */
  schema: JsonSchema;
}

/**
 * Asks a model for a split. [advise] resolves to the model's answer parsed from JSON, not checked
 * yet, and rejects with [PermanentAdvisorError] when asking again would not help (e.g. a safety
 * block or an answer that is not JSON), or with any other error when it might (e.g. the model is
 * down, slow or rate-limited).
 */
export interface SplitAdvisor {
  /** The model's name, stored with each LLM proposal for the admin review. */
  readonly model: string;
  advise(request: AdvisorRequest): Promise<unknown>;
}

/** A failure that asking the model again would repeat, so the proposal fails without a retry. */
export class PermanentAdvisorError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "PermanentAdvisorError";
  }
}
