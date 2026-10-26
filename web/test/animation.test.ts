import { describe, expect, it } from 'vitest';
import { visualPosition } from '../src/render/animation';
import { STEP_MS } from '../src/constants';

const START = 1000;

describe('where the hero is drawn between two tiles', () => {
  it('is the starting tile at the moment the step begins', () => {
    expect(visualPosition({ x: 2, y: 3 }, { x: 3, y: 3 }, START, START, false)).toEqual({ x: 2, y: 3 });
  });

  it('is exactly the destination once a step has taken its full time, and stays there', () => {
    expect(visualPosition({ x: 2, y: 3 }, { x: 3, y: 3 }, START, START + STEP_MS, false)).toEqual({ x: 3, y: 3 });
    expect(visualPosition({ x: 2, y: 3 }, { x: 3, y: 3 }, START, START + STEP_MS * 10, false)).toEqual({
      x: 3,
      y: 3,
    });
  });

  it('is strictly between the two tiles halfway, and past the linear midpoint because the ease is out', () => {
    const halfway = visualPosition({ x: 2, y: 3 }, { x: 3, y: 3 }, START, START + STEP_MS / 2, false);
    expect(halfway.x).toBeGreaterThan(2);
    expect(halfway.x).toBeLessThan(3);
    expect(halfway.x).toBeGreaterThan(2.5);
    expect(halfway.y).toBe(3);
  });

  it('clamps a time before the step begins to the starting tile instead of overshooting backwards', () => {
    expect(visualPosition({ x: 2, y: 3 }, { x: 3, y: 3 }, START, START - 500, false)).toEqual({ x: 2, y: 3 });
  });

  it('returns the destination at once when reduce motion is on', () => {
    expect(visualPosition({ x: 2, y: 3 }, { x: 3, y: 3 }, START, START, true)).toEqual({ x: 3, y: 3 });
  });

  it('returns the tile unchanged at every time when from and to are the same (a server correction snaps)', () => {
    const tile = { x: 5, y: 7 };
    for (const now of [START - 100, START, START + STEP_MS / 3, START + STEP_MS, START + 10_000]) {
      expect(visualPosition(tile, tile, START, now, false)).toEqual({ x: 5, y: 7 });
    }
  });

  it('moves only y on a vertical step', () => {
    const mid = visualPosition({ x: 4, y: 1 }, { x: 4, y: 2 }, START, START + STEP_MS / 2, false);
    expect(mid.x).toBe(4);
    expect(mid.y).toBeGreaterThan(1);
    expect(mid.y).toBeLessThan(2);
  });

  it('moves only x on a horizontal step', () => {
    const mid = visualPosition({ x: 4, y: 1 }, { x: 3, y: 1 }, START, START + STEP_MS / 2, false);
    expect(mid.y).toBe(1);
    expect(mid.x).toBeLessThan(4);
    expect(mid.x).toBeGreaterThan(3);
  });

  it('always returns finite numbers', () => {
    const times = [START - 1e6, START - 1, START, START + 1, START + STEP_MS / 7, START + STEP_MS, START + 1e9];
    for (const now of times) {
      for (const reduced of [true, false]) {
        const v = visualPosition({ x: 0, y: 0 }, { x: 31, y: 31 }, START, now, reduced);
        expect(Number.isFinite(v.x)).toBe(true);
        expect(Number.isFinite(v.y)).toBe(true);
      }
    }
  });
});
