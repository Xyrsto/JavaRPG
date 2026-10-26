import type { Direction } from '../api/client';

/*
 * Keys are read by KeyboardEvent.code, not .key: code is the physical key position, so an AZERTY player gets the
 * same finger positions without configuring anything, and this table cannot depend on the keyboard layout.
 */
const DIRECTIONS: Readonly<Record<string, Direction>> = {
  ArrowUp: 'UP',
  KeyW: 'UP',
  ArrowDown: 'DOWN',
  KeyS: 'DOWN',
  ArrowLeft: 'LEFT',
  KeyA: 'LEFT',
  ArrowRight: 'RIGHT',
  KeyD: 'RIGHT',
};

/** The physical key codes that move the hero, so the caller can stop exactly these from scrolling the page. */
export const MOVEMENT_CODES: readonly string[] = Object.keys(DIRECTIONS);

/** The direction a physical key moves the hero in, or null when the key does not move the hero. */
export function directionFor(code: string): Direction | null {
  return Object.prototype.hasOwnProperty.call(DIRECTIONS, code) ? (DIRECTIONS[code] ?? null) : null;
}
