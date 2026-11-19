// Made with Claude.

import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { DocumentData, Firestore, Timestamp, getFirestore } from "firebase-admin/firestore";
import {
  DraftOptions,
  FAILED_MESSAGE,
  draftLlmProposal,
  readyMessage,
} from "./llmProposal";
import { ANSWER_SCHEMA, INSTRUCTIONS } from "./llmProposalRules";
import { AdvisorRequest, PermanentAdvisorError, SplitAdvisor } from "./splitAdvisor";

jest.mock("firebase-functions/logger");

// The transactions are tested on the emulator; the pure rules are in llmProposalRules.test.ts.
let db: Firestore;

beforeAll(() => {
  // Without the emulator, the Admin SDK would write to the real project.
  if (!process.env.FIRESTORE_EMULATOR_HOST) {
    throw new Error(
      "These tests need the Firestore emulator. In functions/, run: " +
        'firebase emulators:exec --only firestore "npm test"',
    );
  }
  if (getApps().length === 0) initializeApp({ projectId: process.env.GCLOUD_PROJECT });
  db = getFirestore();
});

const LENDER = "llm-lender";
const BORROWER = "llm-borrower";
const MODEL = "fake-model";

/** The dispute as it was in direct settlement, before it escalated. */
const SETTLING = { stage: "SETTLE", proposals: [], escalatedBy: null, agreedAt: null };

/** A model that answers [answer], or runs it when it is a function; it keeps every request. */
function fakeAdvisor(answer: unknown): SplitAdvisor & { requests: AdvisorRequest[] } {
  const requests: AdvisorRequest[] = [];
  return {
    model: MODEL,
    requests,
    async advise(request) {
      requests.push(request);
      return typeof answer === "function" ? answer() : answer;
    },
  };
}

const FAIR = { lenderPercent: 60, reasoning: "A deep scratch on the frame, about 120 PP to fix." };

/**
 * A disputed rental with its listing, its condition photos and a dispute just escalated to the LLM
 * proposal, as the Cloud Functions write them. [dispute] overrides the dispute's fields. Returns
 * the rental id and the dispute as stored.
 */
async function escalated(dispute: object = {}): Promise<{ id: string; after: DocumentData }> {
  const id = randomUUID();
  const listingId = randomUUID();
  await db.collection("listings").doc(listingId).set({
    title: "Road bike",
    description: "Carbon frame, size M",
    itemValue: 800,
  });
  const rentalRef = db.collection("rentals").doc(id);
  await rentalRef.set({
    listingId,
    listingTitle: "Road bike",
    ownerId: LENDER,
    borrowerId: BORROWER,
    participants: [LENDER, BORROWER],
    status: "DISPUTED",
    deposit: 200,
    startDate: "2026-11-01",
    endDate: "2026-11-03",
  });
  const photo = (phase: string, takenBy: string, minute: number) => ({
    phase,
    takenBy,
    takenAt: Timestamp.fromDate(new Date(Date.UTC(2026, 10, 1, 10, minute))),
    // Never used: a modified app could point it at any image.
    url: "https://example.com/not-this-photo.jpg",
  });
  await rentalRef.collection("photos").doc("p-lender").set(photo("PICKUP", LENDER, 0));
  await rentalRef.collection("photos").doc("p-borrower").set(photo("PICKUP", BORROWER, 1));
  await rentalRef.collection("photos").doc("p-stranger").set(photo("PICKUP", "stranger", 2));
  const after = {
    ...SETTLING,
    stage: "LLM_PROPOSAL",
    escalatedBy: LENDER,
    llmProposal: { status: "PENDING", attempt: 1 },
    ...dispute,
  };
  await db.collection("disputes").doc(id).set(after);
  return { id, after };
}

/** Runs a draft for the escalation of [id] to [after], with [options]. */
function draft(
  id: string,
  after: DocumentData,
  options: Partial<DraftOptions>,
  before: DocumentData = SETTLING,
): Promise<void> {
  return draftLlmProposal(db, id, before, after, { advisor: null, expired: false, ...options });
}

async function proposal(id: string) {
  return (await db.collection("disputes").doc(id).get()).get("llmProposal");
}

async function messages(id: string): Promise<string[]> {
  const snap = await db.collection(`rentals/${id}/messages`).orderBy("sentAt").get();
  expect(snap.docs.every((doc) => doc.get("type") === "SYSTEM")).toBe(true);
  return snap.docs.map((doc) => doc.get("text"));
}

describe("a proposal that is ready", () => {
  it("stores the split, its reasoning, the model and the photos sent, tells the chat", async () => {
    const { id, after } = await escalated();

    await draft(id, after, { advisor: fakeAdvisor(FAIR) });

    const stored = await proposal(id);
    expect(stored).toMatchObject({
      status: "READY",
      attempt: 1,
      lenderPercent: 60,
      reasoning: FAIR.reasoning,
      model: MODEL,
      photoIds: ["p-lender", "p-borrower"],
    });
    expect(stored.createdAt).toBeInstanceOf(Timestamp);
    expect(await messages(id)).toEqual([readyMessage(60)]);
    expect(readyMessage(60)).toBe("LLM proposal ready · 60% to the lender");
    // From nobody: neither participant's chat is marked as read.
    expect((await db.collection("rentals").doc(id).get()).get("lastReadAt")).toBeUndefined();
  });

  it("sends the instructions, the schema and each photo by Storage path, not url", async () => {
    const { id, after } = await escalated();
    const advisor = fakeAdvisor(FAIR);

    await draft(id, after, { advisor });

    const request = advisor.requests[0];
    expect(request.instructions).toBe(INSTRUCTIONS);
    expect(request.schema).toBe(ANSWER_SCHEMA);
    const paths = request.parts.flatMap((part) => ("photoPath" in part ? [part.photoPath] : []));
    expect(paths).toEqual([
      `rentals/${id}/photos/p-lender.jpg`,
      `rentals/${id}/photos/p-borrower.jpg`,
    ]);
    expect(JSON.stringify(request.parts)).not.toContain("example.com");
    const facts = (request.parts[0] as { text: string }).text;
    expect(facts).toContain("Description: Carbon frame, size M");
    expect(facts).toContain("Item value: 800 PP");
    expect(facts).toContain("Deposit: 200 PP");
  });

  it("still asks when the listing was deleted, with the title copied on the rental", async () => {
    const { id, after } = await escalated();
    const rental = await db.collection("rentals").doc(id).get();
    await db.collection("listings").doc(rental.get("listingId")).delete();
    const advisor = fakeAdvisor(FAIR);

    await draft(id, after, { advisor });

    const facts = (advisor.requests[0].parts[0] as { text: string }).text;
    expect(facts).toContain("Item: Road bike");
    expect(facts).toContain("Item value: unknown");
    expect((await proposal(id)).status).toBe("READY");
  });

  it("answers a new attempt after a failure", async () => {
    const { id, after } = await escalated({ llmProposal: { status: "PENDING", attempt: 2 } });
    const before = { ...after, llmProposal: { status: "FAILED", attempt: 1 } };

    await draft(id, after, { advisor: fakeAdvisor(FAIR) }, before);

    expect(await proposal(id)).toMatchObject({ status: "READY", attempt: 2 });
  });
});

describe("a proposal that fails", () => {
  it("fails at once, without reading the evidence, when no model is configured", async () => {
    const { id, after } = await escalated();

    await draft(id, after, { advisor: null });

    const stored = await proposal(id);
    expect(stored).toMatchObject({ status: "FAILED", attempt: 1 });
    expect(stored.createdAt).toBeInstanceOf(Timestamp);
    expect(stored.lenderPercent).toBeUndefined();
    expect(await messages(id)).toEqual([FAILED_MESSAGE]);
    expect(FAILED_MESSAGE).toBe("The LLM proposal failed · try again or reject");
  });

  it("fails without asking once the event was retried for a day", async () => {
    const { id, after } = await escalated();
    const advisor = fakeAdvisor(FAIR);

    await draft(id, after, { advisor, expired: true });

    expect(advisor.requests).toHaveLength(0);
    expect((await proposal(id)).status).toBe("FAILED");
  });

  it("fails when the model refuses for good", async () => {
    const { id, after } = await escalated();
    const advisor = fakeAdvisor(() => Promise.reject(new PermanentAdvisorError("Safety block")));

    await draft(id, after, { advisor });

    expect((await proposal(id)).status).toBe("FAILED");
    expect(await messages(id)).toEqual([FAILED_MESSAGE]);
  });

  it("fails when the answer breaks the rules", async () => {
    for (const answer of [
      { lenderPercent: 101, reasoning: "Too much." },
      { lenderPercent: 50, reasoning: "  " },
      "not an object",
    ]) {
      const { id, after } = await escalated();

      await draft(id, after, { advisor: fakeAdvisor(answer) });

      expect((await proposal(id)).status).toBe("FAILED");
    }
  });

  it("writes nothing and rejects when the model may answer later, to be retried", async () => {
    const { id, after } = await escalated();
    const down = fakeAdvisor(() => Promise.reject(new Error("503 Service Unavailable")));

    await expect(draft(id, after, { advisor: down })).rejects.toThrow("503");

    expect(await proposal(id)).toEqual({ status: "PENDING", attempt: 1 });
    expect(await messages(id)).toEqual([]);

    await draft(id, after, { advisor: fakeAdvisor(FAIR) });
    expect((await proposal(id)).status).toBe("READY");
  });
});

describe("when nothing is asked", () => {
  it("ignores an update that started no new attempt", async () => {
    const { id, after } = await escalated();
    const advisor = fakeAdvisor(FAIR);
    const pending = after.llmProposal;

    const updates: [DocumentData, DocumentData][] = [
      // Another field changed while the proposal waits: the attempt is the same.
      [{ ...after, escalatedBy: null }, after],
      // Its own result.
      [after, { ...after, llmProposal: { ...pending, status: "READY", lenderPercent: 60 } }],
      // Not in the LLM proposal stage, or no proposal at all.
      [SETTLING, { ...after, stage: "COURT" }],
      [SETTLING, { ...after, llmProposal: undefined }],
      [SETTLING, { ...after, llmProposal: { status: "PENDING", attempt: "1" } }],
    ];
    for (const [before, changed] of updates) {
      await draftLlmProposal(db, id, before, changed, { advisor, expired: false });
    }
    await draftLlmProposal(db, id, after, undefined, { advisor, expired: false });

    expect(advisor.requests).toHaveLength(0);
    expect(await proposal(id)).toEqual(pending);
  });

  it("leaves alone a dispute whose rental is no longer DISPUTED", async () => {
    const { id, after } = await escalated();
    await db.collection("rentals").doc(id).update({ status: "CLOSED" });
    const advisor = fakeAdvisor(FAIR);

    await draft(id, after, { advisor });
    await draft(id, after, { advisor: null });

    expect(advisor.requests).toHaveLength(0);
    expect(await proposal(id)).toEqual({ status: "PENDING", attempt: 1 });
    expect(await messages(id)).toEqual([]);
  });

  it("writes nothing when the dispute no longer waits for this attempt", async () => {
    const movedOn = [
      { stage: "COURT" },
      { agreedAt: Timestamp.now() },
      { llmProposal: { status: "PENDING", attempt: 2 } },
    ];
    for (const change of movedOn) {
      // The event still shows attempt 1, but the dispute changed before the result was written.
      const { id, after } = await escalated();
      await db.collection("disputes").doc(id).update(change);

      await draft(id, after, {});

      expect(await messages(id)).toEqual([]);
    }
  });
});

describe("when the dispute changes while the model thinks", () => {
  it("drops the answer when the dispute went to court meanwhile", async () => {
    const { id, after } = await escalated();
    const advisor = fakeAdvisor(async () => {
      await db.collection("disputes").doc(id).update({ stage: "COURT" });
      return FAIR;
    });

    await draft(id, after, { advisor });

    expect(await proposal(id)).toEqual({ status: "PENDING", attempt: 1 });
    expect(await messages(id)).toEqual([]);
  });

  it("drops the answer of an attempt that is no longer the latest", async () => {
    const { id, after } = await escalated();
    const advisor = fakeAdvisor(async () => {
      await db
        .collection("disputes")
        .doc(id)
        .update({ llmProposal: { status: "PENDING", attempt: 2 } });
      return FAIR;
    });

    await draft(id, after, { advisor });

    expect(await proposal(id)).toEqual({ status: "PENDING", attempt: 2 });
  });

  it("keeps the first answer when two runs of the same attempt overlap", async () => {
    const { id, after } = await escalated();

    await Promise.all([
      draft(id, after, { advisor: fakeAdvisor(FAIR) }),
      draft(id, after, { advisor: fakeAdvisor({ lenderPercent: 70, reasoning: "Other." }) }),
    ]);

    const stored = await proposal(id);
    expect(stored.status).toBe("READY");
    expect(await messages(id)).toEqual([readyMessage(stored.lenderPercent)]);
  });
});
