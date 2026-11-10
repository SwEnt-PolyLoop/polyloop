// Made with Claude.

import { serverToday } from "./index";
import { zurichDay } from "./serverToday";

// Firebase loads this ES module to verify tokens, which Jest cannot load; `run` skips that step.
jest.mock("jose", () => ({}));

/** The request of the signed-in user [uid], with no input. */
function asUser(uid: string, email = `${uid}@epfl.ch`, verified = true) {
  return { auth: { uid, token: { email, email_verified: verified } }, data: {} } as never;
}

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

describe("serverToday", () => {
  it("gives a verified EPFL user today's date in Zurich", async () => {
    const before = zurichDay(new Date());
    const result = await serverToday.run(asUser("today-user"));
    const after = zurichDay(new Date());
    // Either day, in case midnight in Zurich passes during the call.
    expect([before, after]).toContain(result.today);
  });

  it("refuses a caller who is not signed in", async () => {
    await expect(serverToday.run({ data: {} } as never)).rejects.toMatchObject({
      code: "unauthenticated",
    });
  });

  it("refuses an unverified email", async () => {
    await expect(
      serverToday.run(asUser("today-user", "today-user@epfl.ch", false)),
    ).rejects.toMatchObject({ code: "permission-denied" });
  });

  it("refuses an email outside @epfl.ch", async () => {
    await expect(
      serverToday.run(asUser("today-user", "today-user@gmail.com")),
    ).rejects.toMatchObject({ code: "permission-denied" });
  });
});
