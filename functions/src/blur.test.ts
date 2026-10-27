// Made with Claude.

import { blurPoint } from "./blur";

describe("blurPoint", () => {
  it("rounds to the nearest 0.01 degree", () => {
    // Near the EPFL Rolex Learning Center.
    expect(blurPoint({ latitude: 46.51843, longitude: 6.56826 })).toEqual({
      latitude: 46.52,
      longitude: 6.57,
    });
  });

  it("keeps the blurred point within half a cell of the exact one", () => {
    const exact = { latitude: 46.52499, longitude: 6.56501 };

    const blurred = blurPoint(exact);

    expect(Math.abs(blurred.latitude - exact.latitude)).toBeLessThanOrEqual(0.005);
    expect(Math.abs(blurred.longitude - exact.longitude)).toBeLessThanOrEqual(0.005);
  });

  it("gives the same point for every address in the same cell", () => {
    const first = blurPoint({ latitude: 46.5161, longitude: 6.5641 });
    const second = blurPoint({ latitude: 46.5238, longitude: 6.5633 });

    expect(second).toEqual(first);
  });

  it("returns exact hundredths and never negative zero", () => {
    const blurred = blurPoint({ latitude: 46.5207, longitude: -0.004 });

    expect(blurred.latitude).toBe(46.52);
    expect(Object.is(blurred.longitude, 0)).toBe(true);
  });
});
