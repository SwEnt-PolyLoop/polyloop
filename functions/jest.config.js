/** Runs the TypeScript tests in src/ with ts-jest. */
module.exports = {
  preset: "ts-jest",
  testEnvironment: "node",
  roots: ["<rootDir>/src"],
  // One file at a time: test files share the Firestore emulator, and acceptances running in several
  // files at once block each other's transactions until the tests time out.
  maxWorkers: 1,
};
