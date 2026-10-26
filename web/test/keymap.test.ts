import { describe, expect, it } from 'vitest';
import { directionFor, MOVEMENT_CODES } from '../src/input/keymap';
import type { Direction } from '../src/api/client';

const ALLOWED: readonly Direction[] = ['UP', 'DOWN', 'LEFT', 'RIGHT'];

describe('directionFor', () => {
  it.each([
    ['ArrowUp', 'UP'],
    ['KeyW', 'UP'],
    ['ArrowDown', 'DOWN'],
    ['KeyS', 'DOWN'],
    ['ArrowLeft', 'LEFT'],
    ['KeyA', 'LEFT'],
    ['ArrowRight', 'RIGHT'],
    ['KeyD', 'RIGHT'],
  ] as const)('maps %s to %s', (code, expected) => {
    expect(directionFor(code)).toBe(expected);
  });

  it.each(['KeyQ', 'Space', 'Enter', '', 'arrowup'])('maps %j to null', (code) => {
    expect(directionFor(code)).toBeNull();
  });

  it('only ever returns one of the four straight directions, never a diagonal', () => {
    const results = MOVEMENT_CODES.map((code) => directionFor(code));
    for (const result of results) {
      expect(result).not.toBeNull();
      expect(ALLOWED).toContain(result);
    }
  });
});

describe('MOVEMENT_CODES', () => {
  it('lists exactly the eight movement codes', () => {
    expect([...MOVEMENT_CODES].sort()).toEqual(
      ['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'KeyW', 'KeyA', 'KeyS', 'KeyD'].sort(),
    );
  });

  it('maps every listed code to a direction', () => {
    expect(MOVEMENT_CODES).toHaveLength(8);
    for (const code of MOVEMENT_CODES) {
      expect(directionFor(code)).not.toBeNull();
    }
  });
});
