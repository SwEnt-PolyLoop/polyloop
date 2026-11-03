// Made with Claude.

/** A point on Earth, in degrees. */
export interface LatLng {
  latitude: number;
  longitude: number;
}

/**
 * Cells per degree of the grid points are snapped to: 100, so a 0.01° grid, about 1.1 km
 * north-south and 0.8 km east-west around EPFL.
 */
const CELLS_PER_DEGREE = 100;

/**
 * Rounds [point] to the nearest point of the 0.01° grid. The exact point is then at most half a
 * cell away (about 700 m), and every address in the same cell gives the same blurred point, so the
 * exact one cannot be read back.
 */
export function blurPoint(point: LatLng): LatLng {
  return { latitude: snap(point.latitude), longitude: snap(point.longitude) };
}

function snap(degrees: number): number {
  // Dividing a whole number of cells gives 46.52, not 46.52000000000001. Adding 0 turns -0 into 0.
  return Math.round(degrees * CELLS_PER_DEGREE) / CELLS_PER_DEGREE + 0;
}
