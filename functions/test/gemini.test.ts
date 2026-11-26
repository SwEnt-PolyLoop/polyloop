// Made with Claude.

import { GEMINI_MODEL, toGeminiRequest } from "../src/gemini";
import { ANSWER_SCHEMA, INSTRUCTIONS, evidenceParts } from "../src/llmProposalRules";
import { AdvisorRequest } from "../src/splitAdvisor";

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

describe("toGeminiRequest", () => {
  it("asks the pinned model for JSON following the schema, at temperature 0", () => {
    const gemini = toGeminiRequest(request, BUCKET);

    expect(gemini.model).toBe(GEMINI_MODEL);
    expect(gemini.config).toEqual({
      systemInstruction: INSTRUCTIONS,
      responseMimeType: "application/json",
      responseJsonSchema: ANSWER_SCHEMA,
      temperature: 0,
    });
  });

  it("sends the evidence in order, each photo from Storage after its label", () => {
    const gemini = toGeminiRequest(request, BUCKET);

    const parts = request.parts.map((part) =>
      "text" in part
        ? { text: part.text }
        : { fileData: { fileUri: `gs://${BUCKET}/${part.photoPath}`, mimeType: "image/jpeg" } },
    );
    expect(gemini.contents).toEqual([{ role: "user", parts }]);
    expect(parts[2]).toEqual({ text: expect.stringContaining("pickup · taken by the lender") });
    expect(parts[3]).toEqual({
      fileData: {
        fileUri: "gs://test-bucket/rentals/rental1/photos/lender_PICKUP_0.jpg",
        mimeType: "image/jpeg",
      },
    });
  });
});
