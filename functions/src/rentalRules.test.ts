// Made with Claude.

import {
  MAX_DEPOSIT,
  RequestError,
  conflicts,
  daysIn,
  isDateString,
  offerExpiry,
  isValidDeposit,
  listingTitleOf,
  parseDeposit,
  parseOfferAnswer,
  parseOfferInput,
  parseRentalId,
  parseRentalRequest,
  readRange,
  requirePerson,
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

describe("offerExpiry", () => {
  const now = new Date("2026-11-04T10:00:00Z");

  it("is one week later when the rental starts after that", () => {
    expect(offerExpiry(now, "2026-12-01")).toEqual(new Date("2026-11-11T10:00:00Z"));
  });

  it("is the rental's first day when that comes sooner", () => {
    expect(offerExpiry(now, "2026-11-08")).toEqual(new Date("2026-11-08T00:00:00Z"));
  });
});

describe("isValidDeposit", () => {
  it("accepts a whole amount from 1 to the cap", () => {
    expect(isValidDeposit(1)).toBe(true);
    expect(isValidDeposit(MAX_DEPOSIT)).toBe(true);
  });

  it("refuses anything else", () => {
    for (const deposit of [0, -5, 2.5, MAX_DEPOSIT + 1, "50", null, undefined]) {
      expect(isValidDeposit(deposit)).toBe(false);
    }
  });
});

describe("parseDeposit", () => {
  it("accepts a whole amount from 1 to the cap", () => {
    expect(parseDeposit(1)).toBe(1);
    expect(parseDeposit(MAX_DEPOSIT)).toBe(MAX_DEPOSIT);
  });

  it("refuses anything else", () => {
    for (const deposit of [0, -5, 2.5, MAX_DEPOSIT + 1, "50", null, undefined]) {
      expect(errorCode(() => parseDeposit(deposit))).toBe("invalid-argument");
    }
  });
});

describe("parseOfferInput", () => {
  const valid = {
    rentalId: "r1",
    deposit: 50,
    pickupAddress: "  Route Cantonale 1  ",
    returnAddress: "Rue de Lausanne 10",
  };

  it("returns the input, with the addresses trimmed", () => {
    expect(parseOfferInput(valid)).toEqual({
      rentalId: "r1",
      deposit: 50,
      pickupAddress: "Route Cantonale 1",
      returnAddress: "Rue de Lausanne 10",
    });
  });

  it("refuses a wrong deposit, a blank address or rental id, or no input", () => {
    for (const data of [
      { ...valid, deposit: 0 },
      { ...valid, pickupAddress: "   " },
      { ...valid, returnAddress: undefined },
      { ...valid, rentalId: "" },
      undefined,
    ]) {
      expect(errorCode(() => parseOfferInput(data))).toBe("invalid-argument");
    }
  });
});

describe("parseOfferAnswer", () => {
  it("reads ACCEPT and DECLINE without a deposit", () => {
    expect(parseOfferAnswer({ rentalId: "r1", action: "ACCEPT" })).toEqual({
      rentalId: "r1",
      action: "ACCEPT",
    });
    expect(parseOfferAnswer({ rentalId: "r1", action: "DECLINE", deposit: 5 })).toEqual({
      rentalId: "r1",
      action: "DECLINE",
    });
  });

  it("reads COUNTER with its deposit", () => {
    expect(parseOfferAnswer({ rentalId: "r1", action: "COUNTER", deposit: 40 })).toEqual({
      rentalId: "r1",
      action: "COUNTER",
      deposit: 40,
    });
  });

  it("refuses COUNTER without a valid deposit, an unknown action, or no rental id", () => {
    for (const data of [
      { rentalId: "r1", action: "COUNTER" },
      { rentalId: "r1", action: "NEGOTIATE" },
      { action: "ACCEPT" },
    ]) {
      expect(errorCode(() => parseOfferAnswer(data))).toBe("invalid-argument");
    }
  });
});

describe("parseRentalId", () => {
  it("returns the rental id, or refuses a missing one", () => {
    expect(parseRentalId({ rentalId: "r1" })).toBe("r1");
    expect(errorCode(() => parseRentalId({}))).toBe("invalid-argument");
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

describe("requirePerson", () => {
  it("returns the id of the person", () => {
    expect(requirePerson("user-1", "borrower")).toBe("user-1");
  });

  it("refuses a missing, blank or non-string id with an internal error naming the role", () => {
    for (const value of [undefined, null, "", 42, {}]) {
      expect(errorCode(() => requirePerson(value, "lender"))).toBe("internal");
    }
    expect(() => requirePerson("", "lender")).toThrow("The rental has no lender.");
  });
});

describe("listingTitleOf", () => {
  it("returns the title, or an empty text when the rental has none or a bad one", () => {
    expect(listingTitleOf({ listingTitle: "Camping tent" })).toBe("Camping tent");
    for (const listingTitle of [undefined, null, 42, {}]) {
      expect(listingTitleOf({ listingTitle })).toBe("");
    }
    expect(listingTitleOf({})).toBe("");
  });
});

describe("parseRentalRequest", () => {
  const valid = {
    rentalId: "0b9f6c1e-4f43-4a3b-9d63-3f4a1f3c2a10",
    listingId: "listing-1",
    startDate: "2030-05-10",
    endDate: "2030-05-12",
  };

  it("returns the ids and the dates, and ignores every other field", () => {
    const input = parseRentalRequest({ ...valid, totalPrice: 1, ownerId: "x", borrowerName: "y" });

    expect(input).toEqual(valid);
  });

  it("accepts a one-day rental", () => {
    expect(parseRentalRequest({ ...valid, endDate: valid.startDate }).endDate).toBe("2030-05-10");
  });

  it("refuses a missing, blank, or malformed id", () => {
    for (const patch of [
      { rentalId: undefined },
      { rentalId: "  " },
      { rentalId: "a/b" },
      { rentalId: "x".repeat(129) },
      { listingId: undefined },
      { listingId: "a/b" },
      { listingId: 7 },
    ]) {
      expect(errorCode(() => parseRentalRequest({ ...valid, ...patch }))).toBe("invalid-argument");
    }
  });

  it("refuses bad dates and an end before the start", () => {
    for (const patch of [
      { startDate: "10/05/2030" },
      { endDate: "2030-02-30" },
      { startDate: undefined },
      { endDate: 20300512 },
      { startDate: "2030-05-13" },
    ]) {
      expect(errorCode(() => parseRentalRequest({ ...valid, ...patch }))).toBe("invalid-argument");
    }
  });

  it("refuses an input that is not an object", () => {
    expect(errorCode(() => parseRentalRequest(null))).toBe("invalid-argument");
  });
});

describe("daysIn", () => {
  it("counts both days", () => {
    expect(daysIn({ start: "2030-05-10", end: "2030-05-10" })).toBe(1);
    expect(daysIn({ start: "2030-05-10", end: "2030-05-12" })).toBe(3);
    expect(daysIn({ start: "2030-02-27", end: "2030-03-02" })).toBe(4);
  });
});
