// Made with Claude.

/**
 * The Vertex AI [SplitAdvisor] (docs/adr/0001): Gemini, called with the Functions service account,
 * in the EU. The only file that imports `@google/genai`.
 */
import { GenerateContentParameters, Part } from "@google/genai";
import { AdvisorRequest, PromptPart } from "./splitAdvisor";

/** The model asked for the split, stored with each LLM proposal. */
export const GEMINI_MODEL = "gemini-2.5-flash";

/** Where Vertex AI runs the model: next to the Functions and the Firestore database. */
export const GEMINI_LOCATION = "europe-west1";

/** Condition photos are JPEGs (docs/ARCHITECTURE.md). */
const PHOTO_MIME_TYPE = "image/jpeg";

/**
 * The Gemini request for [request]: its instructions as the system instructions, its parts in
 * order as one user message, each photo read by Vertex AI from [bucket] in Storage, and a JSON
 * answer following its schema, at temperature 0 so that the same evidence gets the same split.
 */
export function toGeminiRequest(
  request: AdvisorRequest,
  bucket: string,
): GenerateContentParameters {
  return {
    model: GEMINI_MODEL,
    contents: [{ role: "user", parts: request.parts.map((part) => toGeminiPart(part, bucket)) }],
    config: {
      systemInstruction: request.instructions,
      responseMimeType: "application/json",
      responseJsonSchema: request.schema,
      temperature: 0,
    },
  };
}

function toGeminiPart(part: PromptPart, bucket: string): Part {
  if ("text" in part) return { text: part.text };
  return { fileData: { fileUri: `gs://${bucket}/${part.photoPath}`, mimeType: PHOTO_MIME_TYPE } };
}
