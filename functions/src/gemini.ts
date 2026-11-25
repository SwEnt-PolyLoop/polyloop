// Made with Claude.

/**
 * The Vertex AI [SplitAdvisor] (docs/adr/0001): Gemini, called with the Functions service account.
 * The only file that imports `@google/genai`.
 */
import {
  ApiError,
  FinishReason,
  GenerateContentParameters,
  GenerateContentResponse,
  GoogleGenAI,
  Models,
  Part,
} from "@google/genai";
import { AdvisorRequest, PermanentAdvisorError, PromptPart, SplitAdvisor } from "./splitAdvisor";

/** The model asked for the split, stored with each LLM proposal. */
export const GEMINI_MODEL = "gemini-3.8-flash";

/**
 * Where Vertex AI runs the model: the global endpoint, which does not keep processing in the EU.
 * No EU location serves a Gemini model to this project (docs/adr/0001).
 */
export const GEMINI_LOCATION = "global";

/**
 * How long one call may take before it fails as a timeout, which may pass: well within the
 * trigger's 300 s, leaving time for the Firestore reads and the write.
 */
export const GEMINI_TIMEOUT_MS = 120_000;

/**
 * The most tokens the model may spend thinking before it answers. Gemini Flash thinks with no
 * limit by default, which adds time against [GEMINI_TIMEOUT_MS], and each timeout is retried and
 * billed again. There is no `maxOutputTokens`: it counts the thinking too, and an
 * answer cut short (`MAX_TOKENS`) fails for good.
 */
export const GEMINI_THINKING_BUDGET = 2048;

/** Condition photos are JPEGs (docs/ARCHITECTURE.md). */
const PHOTO_MIME_TYPE = "image/jpeg";

/** The one API status that asking again would repeat: the request itself is invalid. */
const BAD_REQUEST = 400;

/** The part of the Gemini client the advisor uses, so that tests can pass a fake. */
export type GeminiModels = Pick<Models, "generateContent">;

/**
 * Asks Gemini through [models] for a split, reading the photos from [bucket].
 *
 * Failures that asking again would repeat reject with [PermanentAdvisorError]: a 400, a blocked
 * request, an answer that did not finish normally (`STOP`), an empty answer, or one that is not
 * JSON. At temperature 0 the same evidence would get the same result. Every other error, from the
 * API (e.g. 403, 404, 429, 5xx) or the network (including the timeout), is rethrown as it is, so
 * that the event is delivered again. Errors never include the answer nor the evidence, so they
 * can be logged.
 */
export class GeminiAdvisor implements SplitAdvisor {
  readonly model = GEMINI_MODEL;

  constructor(
    private readonly models: GeminiModels,
    private readonly bucket: string,
  ) {}

  async advise(request: AdvisorRequest): Promise<unknown> {
    let response: GenerateContentResponse;
    try {
      response = await this.models.generateContent(toGeminiRequest(request, this.bucket));
    } catch (error) {
      if (error instanceof ApiError && error.status === BAD_REQUEST) {
        throw new PermanentAdvisorError(`Gemini refused the request (${BAD_REQUEST})`);
      }
      throw error;
    }
    return readAnswer(response);
  }
}

/**
 * The Gemini request for [request]: its instructions as the system instructions, its parts in
 * order as one user message, each photo read by Vertex AI from [bucket] in Storage, and a JSON
 * answer following its schema, at temperature 0 so that the same evidence gets the same split,
 * after at most [GEMINI_THINKING_BUDGET] tokens of thinking.
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
      thinkingConfig: { thinkingBudget: GEMINI_THINKING_BUDGET },
    },
  };
}

function toGeminiPart(part: PromptPart, bucket: string): Part {
  if ("text" in part) return { text: part.text };
  return { fileData: { fileUri: `gs://${bucket}/${part.photoPath}`, mimeType: PHOTO_MIME_TYPE } };
}

/**
 * The answer in [response], parsed from JSON but not checked yet. Throws
 * [PermanentAdvisorError] when the request was blocked, the first answer did not finish normally
 * (`STOP`; e.g. `SAFETY` or `MAX_TOKENS`), or its text is empty or not JSON.
 */
export function readAnswer(response: GenerateContentResponse): unknown {
  const blockReason = response.promptFeedback?.blockReason;
  if (blockReason !== undefined) {
    throw new PermanentAdvisorError(`Gemini blocked the request: ${blockReason}`);
  }
  const finishReason = response.candidates?.[0]?.finishReason;
  if (finishReason !== FinishReason.STOP) {
    throw new PermanentAdvisorError(`Gemini stopped with ${finishReason ?? "no answer"}`);
  }
  const text = response.text;
  if (text === undefined || text.trim() === "") {
    throw new PermanentAdvisorError("Gemini's answer is empty");
  }
  try {
    return JSON.parse(text);
  } catch {
    throw new PermanentAdvisorError("Gemini's answer is not JSON");
  }
}

/**
 * The [GeminiAdvisor] of the deployed functions: Vertex AI in [project], at [GEMINI_LOCATION],
 * signed in as the Functions service account (no API key), each call failing after
 * [GEMINI_TIMEOUT_MS]. Photos are read from [bucket]. Building it makes no call.
 */
export function createGeminiAdvisor(project: string, bucket: string): GeminiAdvisor {
  const client = new GoogleGenAI({
    vertexai: true,
    project,
    location: GEMINI_LOCATION,
    httpOptions: { timeout: GEMINI_TIMEOUT_MS },
  });
  return new GeminiAdvisor(client.models, bucket);
}
