// Made with Claude.

/**
 * Pure rules for disputes: the stages, the input checks and the split of a deposit. No Firestore
 * here, so they are tested without the emulator. A refusal is a RequestError (see rentalRules.ts).
 */
import { RequestError, asFields, parseText } from "./rentalRules";

/** The stages of a dispute, as stored in `disputes/{rentalId}.stage`. */
export const SETTLE = "SETTLE";
export const LLM_PROPOSAL = "LLM_PROPOSAL";

/**
 * Why a request lost a race, in the `details` of its `failed-precondition` error. The app reads
 * this reason, never the message.
 */
export const PROPOSAL_CHANGED = "PROPOSAL_CHANGED";
export const STAGE_CHANGED = "STAGE_CHANGED";

/** A split sent by the app: the dispute's rental and the lender's share, in percent. */
export interface SplitInput {
  rentalId: string;
  lenderPercent: number;
}

/** The two shares of a deposit, in whole PolyPoints, adding up to the deposit. */
export interface DepositSplit {
  lender: number;
  borrower: number;
}

/**
 * The input of `proposeSplit` and `acceptSplit`: a rental id and a whole percentage from 0 to 100.
 * Throws an `invalid-argument` error if one is wrong.
 */
export function parseSplitInput(data: unknown): SplitInput {
  const fields = asFields(data);
  const rentalId = parseText(fields.rentalId, "rentalId");
  const lenderPercent = fields.lenderPercent;
  if (
    typeof lenderPercent !== "number" ||
    !Number.isInteger(lenderPercent) ||
    lenderPercent < 0 ||
    lenderPercent > 100
  ) {
    throw new RequestError(
      "invalid-argument",
      "lenderPercent must be a whole number from 0 to 100.",
    );
  }
  return { rentalId, lenderPercent };
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
