// Made with Claude.

import {
  RequestError,
  conflicts,
  isDateString,
  parseAcceptInput,
  readRange,
  requireVerifiedEpfl,
} from "./rentalRules";

/** Runs [fn] and returns the RequestError code it throws, or "other" for any other error. */
function errorCode(fn: () => unknown): string | null {
  try {
    fn();
    return null;
  } catch (error) {
    return error instanceof RequestError ? error.code : "other";
  }
}

describe("conflicts (buffer day included)", () => {
  const booked = { start: "2026-11-10", end: "2026-11-12" };

  it("is true for an overlapping range", () => {
    expect(conflicts(booked, { start: "2026-11-11", end: "2026-11-15" })).toBe(true);
  });

  it("is true for a range that starts on the buffer day after the booking", () => {
    expect(conflicts(booked, { start: "2026-11-13", end: "2026-11-15" })).toBe(true);
  });

  it("is true for a range that ends on the buffer day before the booking", () => {
    expect(conflicts(booked, { start: "2026-11-07", end: "2026-11-09" })).toBe(true);
  });

  it("is false with one free day between the two rentals", () => {
    expect(conflicts(booked, { start: "2026-11-14", end: "2026-11-15" })).toBe(false);
    expect(conflicts(booked, { start: "2026-11-06", end: "2026-11-08" })).toBe(false);
  });

  it("is true for a range inside the booking", () => {
    expect(conflicts(booked, { start: "2026-11-11", end: "2026-11-11" })).toBe(true);
  });

  it("counts month and year boundaries as consecutive days", () => {
    const newYear = { start: "2026-12-31", end: "2026-12-31" };
    expect(conflicts(newYear, { start: "2027-01-01", end: "2027-01-02" })).toBe(true);
    expect(conflicts(newYear, { start: "2027-01-02", end: "2027-01-03" })).toBe(false);
  });
});

describe("isDateString", () => {
  it("accepts a real calendar date", () => {
    expect(isDateString("2026-02-28")).toBe(true);
  });

  it("refuses a wrong format or an impossible date", () => {
    expect(isDateString("2026-2-28")).toBe(false);
    expect(isDateString("2026-02-30")).toBe(false);
    expect(isDateString(20261128)).toBe(false);
  });
});

describe("readRange", () => {
  it("returns a valid stored range", () => {
    expect(readRange({ start: "2026-11-10", end: "2026-11-12" })).toEqual({
      start: "2026-11-10",
      end: "2026-11-12",
    });
  });

  it("refuses a range that ends before it starts or is malformed", () => {
    expect(readRange({ start: "2026-11-12", end: "2026-11-10" })).toBeNull();
    expect(readRange({ start: "2026-11-10" })).toBeNull();
    expect(readRange("2026-11-10")).toBeNull();
  });
});

describe("parseAcceptInput", () => {
  const valid = {
    rentalId: "r1",
    deposit: 50,
    pickupAddress: "  Route Cantonale 1  ",
    returnAddress: "Rue de Lausanne 10",
  };

  it("returns the input, with the addresses trimmed", () => {
    expect(parseAcceptInput(valid)).toEqual({
      rentalId: "r1",
      deposit: 50,
      pickupAddress: "Route Cantonale 1",
      returnAddress: "Rue de Lausanne 10",
    });
  });

  it("refuses a deposit that is not a whole number above 0", () => {
    for (const deposit of [0, -5, 2.5, "50", null]) {
      expect(errorCode(() => parseAcceptInput({ ...valid, deposit }))).toBe("invalid-argument");
    }
  });

  it("refuses a blank address or rental id", () => {
    expect(errorCode(() => parseAcceptInput({ ...valid, pickupAddress: "   " }))).toBe(
      "invalid-argument",
    );
    expect(errorCode(() => parseAcceptInput({ ...valid, rentalId: "" }))).toBe("invalid-argument");
    expect(errorCode(() => parseAcceptInput(undefined))).toBe("invalid-argument");
  });
});

describe("requireVerifiedEpfl", () => {
  it("returns the uid of a verified @epfl.ch account", () => {
    const auth = { uid: "u1", token: { email: "Ana.B@epfl.ch", email_verified: true } };
    expect(requireVerifiedEpfl(auth)).toBe("u1");
  });

  it("refuses a signed-out caller", () => {
    expect(errorCode(() => requireVerifiedEpfl(undefined))).toBe("unauthenticated");
  });

  it("refuses an unverified account or another domain", () => {
    const unverified = { uid: "u1", token: { email: "a@epfl.ch", email_verified: false } };
    const other = { uid: "u1", token: { email: "a@gmail.com", email_verified: true } };
    expect(errorCode(() => requireVerifiedEpfl(unverified))).toBe("permission-denied");
    expect(errorCode(() => requireVerifiedEpfl(other))).toBe("permission-denied");
  });
});
