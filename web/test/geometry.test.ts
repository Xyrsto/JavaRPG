import { describe, expect, it } from 'vitest';
import { fit } from '../src/render/geometry';

function allFinite(values: readonly number[]): boolean {
  return values.every((value) => Number.isFinite(value));
}

describe('fitting the map to the canvas', () => {
  it('gives a whole tile size that fits inside the canvas with non-negative origins', () => {
    const f = fit(12, 9, 600, 450, 1);
    expect(Number.isInteger(f.tile)).toBe(true);
    expect(f.tile * 12).toBeLessThanOrEqual(f.canvasW);
    expect(f.tile * 9).toBeLessThanOrEqual(f.canvasH);
    expect(f.originX).toBeGreaterThanOrEqual(0);
    expect(f.originY).toBeGreaterThanOrEqual(0);
  });

  it('doubles the canvas and the tile at pixel ratio 2', () => {
    const one = fit(12, 9, 600, 450, 1);
    const two = fit(12, 9, 600, 450, 2);
    expect(two.canvasW).toBe(one.canvasW * 2);
    expect(two.canvasH).toBe(one.canvasH * 2);
    expect(two.tile).toBe(one.tile * 2);
  });

  it('is limited by the height in a wide box and leaves slack left and right', () => {
    const f = fit(12, 9, 2000, 450, 1);
    expect(f.tile).toBe(Math.floor(450 / 9));
    expect(f.originX).toBeGreaterThan(0);
    expect([0, 1]).toContain(f.originY);
  });

  it('is limited by the width in a tall box and leaves slack top and bottom', () => {
    const f = fit(12, 9, 600, 2000, 1);
    expect(f.tile).toBe(Math.floor(600 / 12));
    expect(f.originY).toBeGreaterThan(0);
    expect([0, 1]).toContain(f.originX);
  });

  it('always reports an integer tile, even for awkward sizes', () => {
    const cases: ReadonlyArray<readonly [number, number, number, number, number]> = [
      [12, 9, 601, 453, 1.5],
      [7, 13, 333, 917, 1.25],
      [30, 17, 1023, 511, 2.75],
      [5, 5, 99, 101, 0.9],
    ];
    for (const [mw, mh, bw, bh, ratio] of cases) {
      const f = fit(mw, mh, bw, bh, ratio);
      expect(Number.isInteger(f.tile)).toBe(true);
      expect(f.tile * mw).toBeLessThanOrEqual(f.canvasW);
      expect(f.tile * mh).toBeLessThanOrEqual(f.canvasH);
      expect(f.originX).toBeGreaterThanOrEqual(0);
      expect(f.originY).toBeGreaterThanOrEqual(0);
    }
  });

  it('rounds the canvas size to whole device pixels', () => {
    const f = fit(12, 9, 601, 453, 1.5);
    expect(f.canvasW).toBe(Math.round(601 * 1.5));
    expect(f.canvasH).toBe(Math.round(453 * 1.5));
  });

  it('gives tile 0 and no NaN or Infinity for a zero-width box', () => {
    const f = fit(12, 9, 0, 450, 1);
    expect(f.tile).toBe(0);
    expect(f.originX).toBe(0);
    expect(f.originY).toBe(0);
    expect(f.canvasW).toBe(0);
    expect(allFinite([f.tile, f.originX, f.originY, f.canvasW, f.canvasH])).toBe(true);
  });

  it('gives tile 0 and no NaN or Infinity for a zero-height box', () => {
    const f = fit(12, 9, 600, 0, 1);
    expect(f.tile).toBe(0);
    expect(f.originX).toBe(0);
    expect(f.originY).toBe(0);
    expect(f.canvasH).toBe(0);
    expect(allFinite([f.tile, f.originX, f.originY, f.canvasW, f.canvasH])).toBe(true);
  });

  it('gives tile 0 and no NaN or Infinity for a zero-tile map', () => {
    const f = fit(0, 9, 600, 450, 1);
    expect(f.tile).toBe(0);
    expect(f.originX).toBe(0);
    expect(f.originY).toBe(0);
    expect(f.canvasW).toBe(600);
    expect(f.canvasH).toBe(450);
    expect(allFinite([f.tile, f.originX, f.originY, f.canvasW, f.canvasH])).toBe(true);
  });
});
