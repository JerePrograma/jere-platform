import { describe, expect, it } from "vitest";

import { formatProductLabel } from "./index";

describe("formatProductLabel", () => {
  it("creates a stable platform product label", () => {
    expect(formatProductLabel("Foundation")).toBe(
      "Jere Platform · Foundation"
    );
  });
});
