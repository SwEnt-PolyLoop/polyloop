// Made with Claude.

import {
  Account,
  Accounts,
  SIGN_UP_EXPIRY_MS,
  deleteExpiredSignUps,
  isExpiredSignUp,
} from "./expiredSignUps";

const now = Date.parse("2026-10-27T12:00:00Z");

/** An unverified email/password account created [ageMs] before [now]. */
function account(uid: string, ageMs: number, changes: Partial<Account> = {}): Account {
  return {
    uid,
    email: `${uid}@epfl.ch`,
    emailVerified: false,
    metadata: { creationTime: new Date(now - ageMs).toUTCString() },
    providerData: [{ providerId: "password" }],
    ...changes,
  };
}

const tooOld = SIGN_UP_EXPIRY_MS + 1000;

describe("isExpiredSignUp", () => {
  it("is true for an unverified account older than the limit", () => {
    expect(isExpiredSignUp(account("ana", tooOld), now)).toBe(true);
  });

  it("is false for an unverified account still inside the limit", () => {
    expect(isExpiredSignUp(account("ana", SIGN_UP_EXPIRY_MS - 1000), now)).toBe(false);
  });

  it("is false at exactly the limit", () => {
    expect(isExpiredSignUp(account("ana", SIGN_UP_EXPIRY_MS), now)).toBe(false);
  });

  it("is false for a verified account, however old", () => {
    const verified = account("ana", 365 * 24 * 60 * 60 * 1000, { emailVerified: true });
    expect(isExpiredSignUp(verified, now)).toBe(false);
  });

  it("is false for an account that did not sign up with a password", () => {
    expect(isExpiredSignUp(account("ana", tooOld, { providerData: [] }), now)).toBe(false);
    const google = account("ana", tooOld, { providerData: [{ providerId: "google.com" }] });
    expect(isExpiredSignUp(google, now)).toBe(false);
  });

  it("is false for an account without an email", () => {
    expect(isExpiredSignUp(account("ana", tooOld, { email: undefined }), now)).toBe(false);
  });

  it("is false when the creation time cannot be read", () => {
    const unreadable = account("ana", tooOld, { metadata: { creationTime: "not a date" } });
    expect(isExpiredSignUp(unreadable, now)).toBe(false);
  });

  it("is false for an account created after now (clock difference)", () => {
    expect(isExpiredSignUp(account("ana", -60 * 1000), now)).toBe(false);
  });
});

/** Fake Firebase Auth: serves [pages] in order and records each deleteUsers call. */
function fakeAccounts(pages: Account[][], failuresPerCall = 0) {
  const deleteCalls: string[][] = [];
  const pageTokens: (string | undefined)[] = [];
  const accounts: Accounts = {
    async listUsers(_maxResults, pageToken) {
      pageTokens.push(pageToken);
      const index = pageToken === undefined ? 0 : Number(pageToken);
      const next = index + 1 < pages.length ? String(index + 1) : undefined;
      return { users: pages[index] ?? [], pageToken: next };
    },
    async deleteUsers(uids) {
      deleteCalls.push(uids);
      const failureCount = Math.min(failuresPerCall, uids.length);
      return { successCount: uids.length - failureCount, failureCount };
    },
  };
  return { accounts, deleteCalls, pageTokens };
}

describe("deleteExpiredSignUps", () => {
  it("deletes only the expired sign-ups", async () => {
    const { accounts, deleteCalls } = fakeAccounts([
      [
        account("expired", tooOld),
        account("fresh", 60 * 1000),
        account("verified", tooOld, { emailVerified: true }),
      ],
    ]);

    expect(await deleteExpiredSignUps(accounts, now)).toEqual({ deleted: 1, failed: 0 });
    expect(deleteCalls).toEqual([["expired"]]);
  });

  it("goes through every page and deletes page by page", async () => {
    const { accounts, deleteCalls, pageTokens } = fakeAccounts([
      [account("a", tooOld), account("b", 0)],
      [account("c", 0)],
      [account("d", tooOld), account("e", tooOld)],
    ]);

    expect(await deleteExpiredSignUps(accounts, now)).toEqual({ deleted: 3, failed: 0 });
    expect(pageTokens).toEqual([undefined, "1", "2"]);
    expect(deleteCalls).toEqual([["a"], ["d", "e"]]);
  });

  it("does not call deleteUsers when nothing expired", async () => {
    const { accounts, deleteCalls } = fakeAccounts([[account("fresh", 0)], []]);

    expect(await deleteExpiredSignUps(accounts, now)).toEqual({ deleted: 0, failed: 0 });
    expect(deleteCalls).toEqual([]);
  });

  it("handles a project with no accounts", async () => {
    const { accounts, deleteCalls } = fakeAccounts([[]]);

    expect(await deleteExpiredSignUps(accounts, now)).toEqual({ deleted: 0, failed: 0 });
    expect(deleteCalls).toEqual([]);
  });

  it("counts the accounts that could not be deleted and keeps going", async () => {
    const { accounts, deleteCalls } = fakeAccounts(
      [[account("a", tooOld), account("b", tooOld)], [account("c", tooOld)]],
      1,
    );

    expect(await deleteExpiredSignUps(accounts, now)).toEqual({ deleted: 1, failed: 2 });
    expect(deleteCalls).toEqual([["a", "b"], ["c"]]);
  });

  it("fails when the accounts cannot be listed", async () => {
    const accounts: Accounts = {
      listUsers: () => Promise.reject(new Error("permission denied")),
      deleteUsers: () => Promise.reject(new Error("not called")),
    };

    await expect(deleteExpiredSignUps(accounts, now)).rejects.toThrow("permission denied");
  });
});
