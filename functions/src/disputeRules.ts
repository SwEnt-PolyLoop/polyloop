// Made with Claude.

/**
 * Pure rules for disputes: the stages, the input checks and the split of a deposit. No Firestore
 * here, so they are tested without the emulator. A refusal is a RequestError (see rentalRules.ts).
 */
import { RequestError, asFields, parseText } from "./rentalRules";

/** The stages of a dispute, as stored in `disputes/{rentalId}.stage`. */
export const SETTLE = "SETTLE";
export const LLM_PROPOSAL = "LLM_PROPOSAL";
export const COURT = "COURT";

/** The most attempts at the LLM proposal: the first one, then up to two retries. */
export const MAX_LLM_ATTEMPTS = 3;

/**
 * How many days a dispute stays in direct settlement before it escalates by itself to the LLM
 * proposal: `escalatesAt` is set this long after the dispute opens.
 */
export const SETTLE_DAYS = 30;

/**
 * The states of the LLM proposal, as stored in `disputes/{rentalId}.llmProposal.status`: waiting
 * for the LLM, ready for the parties to answer, or failed (see "Disputes" in docs/ARCHITECTURE.md).
 */
export const PENDING = "PENDING";
export const READY = "READY";
export const FAILED = "FAILED";

/**
 * Why a request lost a race, in the `details` of its `failed-precondition` error. The app reads
 * this reason, never the message.
 */
export const PROPOSAL_CHANGED = "PROPOSAL_CHANGED";
export const STAGE_CHANGED = "STAGE_CHANGED";
export const LLM_PROPOSAL_CHANGED = "LLM_PROPOSAL_CHANGED";

/** A split sent by the app: the dispute's rental and the lender's share, in percent. */
export interface SplitInput {
  rentalId: string;
  lenderPercent: number;
}

/** An acceptance of the LLM proposal sent by the app: the dispute's rental and the attempt seen. */
export interface LlmAcceptInput {
  rentalId: string;
  attempt: number;
}

/** The two shares of a deposit, in whole PolyPoints, adding up to the deposit. */
export interface DepositSplit {
  lender: number;
  borrower: number;
}

/**
 * Whether [value] is a valid split: the lender's share as a whole percentage from 0 to 100. The
 * one definition for the parties' proposals, the LLM's answer and the stored LLM proposal.
 */
export function isWholePercent(value: unknown): value is number {
  return typeof value === "number" && Number.isInteger(value) && value >= 0 && value <= 100;
}

/**
 * The input of `proposeSplit` and `acceptSplit`: a rental id and a whole percentage from 0 to 100.
 * Throws an `invalid-argument` error if one is wrong.
 */
export function parseSplitInput(data: unknown): SplitInput {
  const fields = asFields(data);
  const rentalId = parseText(fields.rentalId, "rentalId");
  const lenderPercent = fields.lenderPercent;
  if (!isWholePercent(lenderPercent)) {
    throw new RequestError(
      "invalid-argument",
      "lenderPercent must be a whole number from 0 to 100.",
    );
  }
  return { rentalId, lenderPercent };
}

/**
 * The input of `acceptLlmProposal`: a rental id and the attempt the caller saw, a whole number from
 * 1. Throws an `invalid-argument` error if one is wrong.
 */
export function parseLlmAcceptInput(data: unknown): LlmAcceptInput {
  const fields = asFields(data);
  const rentalId = parseText(fields.rentalId, "rentalId");
  const attempt = fields.attempt;
  if (typeof attempt !== "number" || !Number.isInteger(attempt) || attempt < 1) {
    throw new RequestError("invalid-argument", "attempt must be a whole number from 1.");
  }
  return { rentalId, attempt };
}

/**
 * Splits [deposit] PolyPoints when the lender gets [lenderPercent] percent: the borrower's share is
 * rounded down and the lender gets the rest, so the shares always add up to the deposit. The app
 * shows the same amounts (`splitDeposit` in DisputeViewModel.kt).
 */
export function splitDeposit(deposit: number, lenderPercent: number): DepositSplit {
  const borrower = Math.floor((deposit * (100 - lenderPercent)) / 100);
  return { lender: deposit - borrower, borrower };
}
