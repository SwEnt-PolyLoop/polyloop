// Made with Claude.

import {
  ApiError,
  BlockedReason,
  FinishReason,
  GenerateContentParameters,
  GenerateContentResponse,
  Part,
} from "@google/genai";
import { GEMINI_MODEL, GeminiAdvisor, GeminiModels, createGeminiAdvisor } from "./gemini";
import { ANSWER_SCHEMA, INSTRUCTIONS, evidenceParts } from "./llmProposalRules";
import { AdvisorRequest, PermanentAdvisorError } from "./splitAdvisor";

const BUCKET = "test-bucket";

const request: AdvisorRequest = {
  instructions: INSTRUCTIONS,
  parts: evidenceParts(
    "rental1",
    {
      itemTitle: "Drill",
      itemDescription: "Cordless drill",
      itemValue: 120,
      deposit: 60,
      startDate: "2026-11-01",
      endDate: "2026-11-03",
    },
    [
      { id: "lender_PICKUP_0", phase: "PICKUP", role: "lender", takenAt: null },
      { id: "borrower_RETURN_0", phase: "RETURN", role: "borrower", takenAt: null },
    ],
  ),
  schema: ANSWER_SCHEMA,
};

/** A Gemini response whose first answer has [parts] and stopped normally. */
function responseOf(parts: Part[]): GenerateContentResponse {
  return stoppedWith(parts, FinishReason.STOP);
}

/** A Gemini response whose first answer has [parts] and stopped for [finishReason]. */
function stoppedWith(parts: Part[], finishReason: FinishReason | undefined) {
  return Object.assign(new GenerateContentResponse(), {
    candidates: [{ content: { role: "model", parts }, finishReason }],
  });
}

const validAnswer = responseOf([{ text: '{"lenderPercent": 40, "reasoning": "A scratch."}' }]);

/** A client that answers [answer] and records each request. */
function fakeModels(answer: GenerateContentResponse) {
  const requests: GenerateContentParameters[] = [];
  const models: GeminiModels = {
    generateContent: async (params) => {
      requests.push(params);
      return answer;
    },
  };
  return { models, requests };
}

/** A client whose every call rejects with [error]. */
function failingModels(error: unknown): GeminiModels {
  return {
    generateContent: async () => {
      throw error;
    },
  };
}

describe("GeminiAdvisor", () => {
  it("names the pinned model", () => {
    expect(new GeminiAdvisor(fakeModels(validAnswer).models, BUCKET).model).toBe(GEMINI_MODEL);
  });

  it("asks the pinned model for JSON following the schema, at temperature 0", async () => {
    const { models, requests } = fakeModels(validAnswer);

    await new GeminiAdvisor(models, BUCKET).advise(request);

    expect(requests).toHaveLength(1);
    expect(requests[0].model).toBe(GEMINI_MODEL);
    expect(requests[0].config).toEqual({
      systemInstruction: INSTRUCTIONS,
      responseMimeType: "application/json",
      responseJsonSchema: ANSWER_SCHEMA,
      temperature: 0,
    });
  });

  it("sends the evidence in order, each photo from Storage after its label", async () => {
    const { models, requests } = fakeModels(validAnswer);

    await new GeminiAdvisor(models, BUCKET).advise(request);

    const parts = request.parts.map((part) =>
      "text" in part
        ? { text: part.text }
        : { fileData: { fileUri: `gs://${BUCKET}/${part.photoPath}`, mimeType: "image/jpeg" } },
    );
    expect(requests[0].contents).toEqual([{ role: "user", parts }]);
    expect(parts[3]).toEqual({
      fileData: {
        fileUri: "gs://test-bucket/rentals/rental1/photos/lender_PICKUP_0.jpg",
        mimeType: "image/jpeg",
      },
    });
    expect(parts[2]).toEqual({ text: expect.stringContaining("pickup · taken by the lender") });
  });

  it("returns the answer parsed from JSON, unchecked", async () => {
    const answer = responseOf([{ text: '{"lenderPercent": 140}' }]);

    const parsed = await new GeminiAdvisor(fakeModels(answer).models, BUCKET).advise(request);

    expect(parsed).toEqual({ lenderPercent: 140 });
  });

  it("leaves the model's thoughts out of the answer", async () => {
    const answer = responseOf([
      { text: "Looking at the photos…", thought: true },
      { text: '{"lenderPercent": 0, "reasoning": "No damage."}' },
    ]);

    const parsed = await new GeminiAdvisor(fakeModels(answer).models, BUCKET).advise(request);

    expect(parsed).toEqual({ lenderPercent: 0, reasoning: "No damage." });
  });

  it("fails for good on a 400, without the API's message", async () => {
    const error = new ApiError({ message: "secret details", status: 400 });

    const advice = new GeminiAdvisor(failingModels(error), BUCKET).advise(request);

    await expect(advice).rejects.toThrow(PermanentAdvisorError);
    await expect(advice).rejects.not.toThrow("secret details");
  });

  it.each([403, 404, 429, 500, 503])("rethrows a %i as it is, to be retried", async (status) => {
    const error = new ApiError({ message: "failed", status });

    const advice = new GeminiAdvisor(failingModels(error), BUCKET).advise(request);

    await expect(advice).rejects.toBe(error);
  });

  it.each([
    ["a network error", new TypeError("fetch failed")],
    ["the timeout", new DOMException("The operation timed out.", "TimeoutError")],
  ])("rethrows %s as it is, to be retried", async (_, error) => {
    const advice = new GeminiAdvisor(failingModels(error), BUCKET).advise(request);

    await expect(advice).rejects.toBe(error);
  });

  it("fails for good when the request is blocked", async () => {
    const blocked = Object.assign(new GenerateContentResponse(), {
      promptFeedback: { blockReason: BlockedReason.IMAGE_SAFETY },
    });

    const advice = new GeminiAdvisor(fakeModels(blocked).models, BUCKET).advise(request);

    await expect(advice).rejects.toThrow(PermanentAdvisorError);
  });

  it.each([
    FinishReason.SAFETY,
    FinishReason.MAX_TOKENS,
    FinishReason.RECITATION,
    FinishReason.OTHER,
    undefined,
  ])("fails for good when the answer stopped with %s", async (finishReason) => {
    const answer = stoppedWith([{ text: '{"lenderPercent": 40, "reasoning": "x"}' }], finishReason);

    const advice = new GeminiAdvisor(fakeModels(answer).models, BUCKET).advise(request);

    await expect(advice).rejects.toThrow(PermanentAdvisorError);
  });

  it("fails for good when there is no answer", async () => {
    const empty = new GenerateContentResponse();

    const advice = new GeminiAdvisor(fakeModels(empty).models, BUCKET).advise(request);

    await expect(advice).rejects.toThrow(PermanentAdvisorError);
  });

  it.each([
    ["no text", []],
    ["a blank text", [{ text: "  " }]],
    ["only thoughts", [{ text: '{"lenderPercent": 40}', thought: true }]],
    ["text that is not JSON", [{ text: "40% to the lender" }]],
  ])("fails for good on an answer with %s", async (_, parts: Part[]) => {
    const advice = new GeminiAdvisor(fakeModels(responseOf(parts)).models, BUCKET).advise(request);

    await expect(advice).rejects.toThrow(PermanentAdvisorError);
  });
});

describe("createGeminiAdvisor", () => {
  it("builds a Gemini advisor for the pinned model without calling it", () => {
    const advisor = createGeminiAdvisor("demo-polyloop", BUCKET);

    expect(advisor).toBeInstanceOf(GeminiAdvisor);
    expect(advisor.model).toBe(GEMINI_MODEL);
  });
});
