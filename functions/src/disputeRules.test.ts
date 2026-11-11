// Made with Claude.

import { parseSplitInput, splitDeposit } from "./disputeRules";
import { RequestError } from "./rentalRules";

describe("splitDeposit", () => {
  // The same cases as DisputeUiStateTest in the app, which shows the same amounts.
  it("rounds the borrower's share down and gives the rest to the lender", () => {
    expect(splitDeposit(33, 50)).toEqual({ lender: 17, borrower: 16 });
    expect(splitDeposit(55, 33)).toEqual({ lender: 19, borrower: 36 });
    expect(splitDeposit(1, 50)).toEqual({ lender: 1, borrower: 0 });
  });

  it("gives the whole deposit to one side at 0% or 100%", () => {
    expect(splitDeposit(60, 0)).toEqual({ lender: 0, borrower: 60 });
    expect(splitDeposit(60, 100)).toEqual({ lender: 60, borrower: 0 });
  });

  it("gives nothing to either side when there is no deposit", () => {
    expect(splitDeposit(0, 30)).toEqual({ lender: 0, borrower: 0 });
  });
});

describe("parseSplitInput", () => {
  it("accepts a rental id and a whole percentage from 0 to 100", () => {
    expect(parseSplitInput({ rentalId: " r1 ", lenderPercent: 0 })).toEqual({
      rentalId: "r1",
      lenderPercent: 0,
    });
    expect(parseSplitInput({ rentalId: "r1", lenderPercent: 100 }).lenderPercent).toBe(100);
  });

  it("refuses a percentage that is not a whole number from 0 to 100", () => {
    for (const lenderPercent of [-1, 101, 50.5, "50", null, undefined]) {
      expect(() => parseSplitInput({ rentalId: "r1", lenderPercent })).toThrow(RequestError);
    }
  });

  it("refuses a missing rental id", () => {
    expect(() => parseSplitInput({ lenderPercent: 50 })).toThrow("rentalId is required.");
    expect(() => parseSplitInput(null)).toThrow(RequestError);
  });
});
