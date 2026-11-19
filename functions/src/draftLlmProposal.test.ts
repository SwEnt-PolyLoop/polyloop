// Made with Claude.

import { getApps, initializeApp } from "firebase-admin/app";
import { draftLlmProposal } from "./draftLlmProposal";
import { draftLlmProposal as draft } from "./llmProposal";

// The Firestore logic is tested on the emulator in llmProposal.test.ts; here only the wiring.
// Imported from its own file, not index.ts: index also loads Firebase Auth, which Jest cannot load.
jest.mock("./llmProposal");

const drafted = jest.mocked(draft);

// index.ts does this in the deployed functions; the trigger needs it to call getFirestore().
beforeAll(() => {
  if (getApps().length === 0) initializeApp({ projectId: "demo-polyloop" });
});

beforeEach(() => drafted.mockClear());

type UpdateEvent = Parameters<typeof draftLlmProposal.run>[0];

/** The event of an update of disputes/{rentalId} from [before] to [after], made at [time]. */
function disputeUpdated(
  rentalId: string,
  before: object,
  after: object,
  time = new Date(),
): UpdateEvent {
  const snapshot = (data: object) => ({ data: () => data });
  return {
    params: { rentalId },
    time: time.toISOString(),
    data: { before: snapshot(before), after: snapshot(after) },
  } as unknown as UpdateEvent;
}

describe("draftLlmProposal", () => {
  it("listens to updates of every dispute, is retried, and has time for the model", () => {
    const endpoint = draftLlmProposal.__endpoint;

    expect(endpoint.eventTrigger?.eventType).toBe("google.cloud.firestore.document.v1.updated");
    expect(endpoint.eventTrigger?.eventFilterPathPatterns).toEqual({
      document: "disputes/{rentalId}",
    });
    expect(endpoint.eventTrigger?.retry).toBe(true);
    expect(endpoint.timeoutSeconds).toBe(300);
  });

  it("passes the dispute before and after the update, with no model configured yet", async () => {
    const before = { stage: "SETTLE" };
    const after = { stage: "LLM_PROPOSAL", llmProposal: { status: "PENDING", attempt: 1 } };

    await draftLlmProposal.run(disputeUpdated("rental-1", before, after));

    expect(drafted).toHaveBeenCalledWith(expect.anything(), "rental-1", before, after, {
      advisor: null,
      expired: false,
    });
  });

  it("marks an event older than a day as expired", async () => {
    const dayAndAMinuteAgo = new Date(Date.now() - (24 * 60 + 1) * 60 * 1000);

    await draftLlmProposal.run(disputeUpdated("rental-1", {}, {}, dayAndAMinuteAgo));

    expect(drafted.mock.calls[0][4].expired).toBe(true);
  });
});
