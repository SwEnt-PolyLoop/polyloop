// Made with Claude.

// The rule for expired sign-ups and the sweep that deletes them. No Firebase imports here, so the
// tests can run it with a fake; deleteExpiredSignUps.ts runs it on a schedule with Firebase Auth.

/**
 * How long a new account may stay unverified. After that it is deleted, so nobody can block an
 * EPFL address by signing up with it and never verifying (someone else's address, or a typo).
 * The app tells the user about this limit on "Check your inbox"; keep both in sync.
 */
export const SIGN_UP_EXPIRY_MS = 5 * 60 * 1000;

/** The most accounts Firebase returns per page, and deletes per call. */
const PAGE_SIZE = 1000;

/** The parts of a Firebase Auth account the sweep looks at (a `UserRecord` has them all). */
export interface Account {
  uid: string;
  email?: string;
  emailVerified: boolean;
  metadata: { creationTime: string };
  providerData: { providerId: string }[];
}

/** The Firebase Auth calls the sweep makes (`getAuth()` has them); tests pass a fake. */
export interface Accounts {
  listUsers(
    maxResults?: number,
    pageToken?: string,
  ): Promise<{ users: Account[]; pageToken?: string }>;
  deleteUsers(uids: string[]): Promise<{ successCount: number; failureCount: number }>;
}

/**
 * True if [account] signed up with an email and password more than [SIGN_UP_EXPIRY_MS] before
 * [now] and has still not verified its email. Other accounts (verified, or not created through
 * email sign-up) are never touched.
 */
export function isExpiredSignUp(account: Account, now: number): boolean {
  const signedUpWithPassword = account.providerData.some((p) => p.providerId === "password");
  const createdAt = Date.parse(account.metadata.creationTime);
  return (
    account.email !== undefined &&
    signedUpWithPassword &&
    !account.emailVerified &&
    !Number.isNaN(createdAt) &&
    now - createdAt > SIGN_UP_EXPIRY_MS
  );
}

/**
 * Goes through every account, one page at a time, and deletes the expired sign-ups of each page.
 * Returns how many were deleted and how many could not be (they are tried again on the next run).
 */
export async function deleteExpiredSignUps(
  accounts: Accounts,
  now: number,
): Promise<{ deleted: number; failed: number }> {
  let deleted = 0;
  let failed = 0;
  let pageToken: string | undefined = undefined;
  do {
    const page: { users: Account[]; pageToken?: string } = await accounts.listUsers(
      PAGE_SIZE,
      pageToken,
    );
    const expired = page.users.filter((a) => isExpiredSignUp(a, now)).map((a) => a.uid);
    if (expired.length > 0) {
      const result = await accounts.deleteUsers(expired);
      deleted += result.successCount;
      failed += result.failureCount;
    }
    pageToken = page.pageToken;
  } while (pageToken);
  return { deleted, failed };
}
