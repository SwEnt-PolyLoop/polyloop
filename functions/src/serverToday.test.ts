// Made with Claude.

import { zurichDay } from "./serverToday";

describe("zurichDay", () => {
  it("gives the day in Zurich", () => {
    expect(zurichDay(new Date("2026-11-15T12:00:00Z"))).toBe("2026-11-15");
  });

  it("is already on the next day in Zurich before midnight UTC in winter (UTC+1)", () => {
    expect(zurichDay(new Date("2026-11-09T23:30:00Z"))).toBe("2026-11-10");
  });

  it("is already on the next day in Zurich before midnight UTC in summer (UTC+2)", () => {
    expect(zurichDay(new Date("2026-07-09T22:30:00Z"))).toBe("2026-07-10");
  });

  it("stays on the same day until midnight in Zurich", () => {
    expect(zurichDay(new Date("2026-07-09T21:30:00Z"))).toBe("2026-07-09");
  });

  it("pads a single-digit month and day with a zero", () => {
    expect(zurichDay(new Date("2026-03-05T12:00:00Z"))).toBe("2026-03-05");
  });
});
