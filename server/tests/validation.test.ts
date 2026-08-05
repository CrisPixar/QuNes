import { describe, expect, it } from "bun:test";
import { normalizePassword, normalizeUsername, positiveInteger, textWithin } from "../src/utils/validation.js";

describe("input validation", () => {
  it("normalizes valid usernames", () => {
    expect(normalizeUsername("  alice_01 ")).toBe("alice_01");
    expect(normalizeUsername("bad name")).toBeNull();
  });

  it("limits password size", () => {
    expect(normalizePassword("short")).toBeNull();
    expect(normalizePassword("StrongPassword123!")).toBe("StrongPassword123!");
  });

  it("keeps text and pagination bounded", () => {
    expect(textWithin(" hello ", 10)).toBe("hello");
    expect(textWithin("", 10)).toBeNull();
    expect(positiveInteger("999", 50, 100)).toBe(100);
    expect(positiveInteger("bad", 50, 100)).toBe(50);
  });
});
