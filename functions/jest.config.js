/**
 * Runs the TypeScript tests with ts-jest. New tests go in test/; older ones are still in src/ until
 * they are moved.
 */
module.exports = {
  preset: "ts-jest",
  testEnvironment: "node",
  roots: ["<rootDir>/src", "<rootDir>/test"],
  // One file at a time: test files share the Firestore emulator, and acceptances running in several
  // files at once block each other's transactions until the tests time out.
  maxWorkers: 1,
  // Tests that run two transactions on the same document at once make the emulator abort and retry
  // one of them, which can take longer than Jest's 5 s default on a slow CI runner.
  testTimeout: 30000,
};
