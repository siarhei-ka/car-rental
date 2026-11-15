import { afterEach, describe, expect, it, vi } from "vitest";
import { newId } from "../ids";

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

afterEach(() => vi.unstubAllGlobals());

describe("newId", () => {
  it("is a random UUID", () => {
    expect(newId()).toMatch(UUID_V4);
    expect(newId()).not.toBe(newId());
  });

  it("is a random UUID also where the browser has no randomUUID, as on plain HTTP", () => {
    vi.stubGlobal("crypto", { getRandomValues: crypto.getRandomValues.bind(crypto) });

    expect(newId()).toMatch(UUID_V4);
    expect(newId()).not.toBe(newId());
  });
});
