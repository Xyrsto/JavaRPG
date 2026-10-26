import type { Point } from '../state/world';
import { STEP_MS } from '../constants';

/** Where the hero is drawn. Fractional tile coordinates: the game state itself always stays on whole tiles. */
export interface Visual {
  readonly x: number;
  readonly y: number;
}

/**
 * Where the hero is drawn at a given moment, gliding from one tile to the next over one step so the walk
 * looks smooth. The ease is out (fast start, slow arrival) so the hero settles onto the tile. Reduced motion
 * skips the glide entirely. When from and to are the same tile, the caller is applying a server correction:
 * that is drawn as an instant snap, never as a glide over a position the hero never occupied.
 * Time is passed in by the caller, so this function reads no clock.
 */
export function visualPosition(from: Point, to: Point, startedAt: number, now: number, reducedMotion: boolean): Visual {
  if (reducedMotion || (from.x === to.x && from.y === to.y)) {
    return { x: to.x, y: to.y };
  }
  const progress = Math.min(1, Math.max(0, (now - startedAt) / STEP_MS));
  if (progress >= 1) {
    return { x: to.x, y: to.y };
  }
  const eased = 1 - (1 - progress) ** 3;
  return {
    x: from.x + (to.x - from.x) * eased,
    y: from.y + (to.y - from.y) * eased,
  };
}
