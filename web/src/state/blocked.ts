import { npcAt, tileAt } from './world';
import type { WorldModel } from './world';

/** Client copy of the server's MovementRules.isBlocked (game-core, Java): the two must agree. Deliberate difference: any character the client does not know is blocked (the Java treats it as floor), so an older client never walks into what the server refuses. */
export function isBlocked(world: WorldModel, openGates: ReadonlySet<string>, x: number, y: number): boolean {
  const tile = tileAt(world, x, y);
  if (tile === null) {
    return true;
  }
  if (npcAt(world, x, y) !== null) {
    return true;
  }
  if (tile === '#') {
    return true;
  }
  if (tile >= 'A' && tile <= 'Z') {
    return !openGates.has(tile);
  }
  if (tile === '.') {
    return false;
  }
  return true;
}
