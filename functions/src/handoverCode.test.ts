// Made with Claude.

import { codeFingerprint, isSameCode, newHandoverCode } from "./handoverCode";

describe("newHandoverCode", () => {
  it("is 43 URL-safe characters", () => {
    expect(newHandoverCode()).toMatch(/^[A-Za-z0-9_-]{43}$/);
  });

  it("is different each time", () => {
    const codes = new Set(Array.from({ length: 1000 }, () => newHandoverCode()));
    expect(codes.size).toBe(1000);
  });
});

describe("codeFingerprint", () => {
  // The same values as the app's HandoverCodeTest, so the phone and the server always agree.
  it("is the SHA-256 of the code, in lowercase hex", () => {
    expect(codeFingerprint("abc")).toBe(
      "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
    );
  });

  it("reads the code as UTF-8", () => {
    expect(codeFingerprint("é")).toBe(
      "4a99557e4033c3539de2eb65472017cad5f9557f7a0625a09f1c3f6e2ba69c4c",
    );
  });

  it("is 64 hex characters for a real code", () => {
    expect(codeFingerprint(newHandoverCode())).toMatch(/^[0-9a-f]{64}$/);
  });

  it("differs for codes one character apart", () => {
    expect(codeFingerprint("secret-code")).not.toBe(codeFingerprint("secret-codf"));
  });
});

describe("isSameCode", () => {
  it("accepts the exact code", () => {
    const code = newHandoverCode();
    expect(isSameCode(code, code)).toBe(true);
  });

  it("refuses another code, even one character apart or with a space", () => {
    expect(isSameCode("secret-codf", "secret-code")).toBe(false);
    expect(isSameCode(" secret-code", "secret-code")).toBe(false);
    expect(isSameCode("SECRET-CODE", "secret-code")).toBe(false);
  });

  it("refuses a shorter or longer code", () => {
    expect(isSameCode("secret", "secret-code")).toBe(false);
    expect(isSameCode("secret-code-2", "secret-code")).toBe(false);
  });

  it("refuses anything when there is no real code to compare with", () => {
    expect(isSameCode("", "")).toBe(false);
    expect(isSameCode("  ", "  ")).toBe(false);
    expect(isSameCode("x", undefined)).toBe(false);
  });

  it("refuses a scan that is not text", () => {
    expect(isSameCode(42, "42")).toBe(false);
    expect(isSameCode(null, "secret-code")).toBe(false);
    expect(isSameCode({ code: "secret-code" }, "secret-code")).toBe(false);
  });
});
