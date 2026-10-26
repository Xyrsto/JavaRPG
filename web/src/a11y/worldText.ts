import type { Direction } from '../api/client';
import type { Point } from '../state/world';

/**
 * The hero's settled position as a sentence. Columns and rows are counted from one, because that is how a person
 * counts them; the map itself counts from zero, so the offset is added here and nowhere else. Plain text only.
 */
export function describeSettled(hero: Point): string {
  return `Hero at column ${hero.x + 1}, row ${hero.y + 1}.`;
}

/** Said when the server put the hero somewhere other than where the page showed it, so the player knows why. */
export function describeCorrection(hero: Point): string {
  return `The game server moved the hero to column ${hero.x + 1}, row ${hero.y + 1}.`;
}

const WORDS: Record<Direction, string> = { UP: 'up', DOWN: 'down', LEFT: 'left', RIGHT: 'right' };

/** Said when a step is refused, naming the direction in words so it is not only shown by a marker. */
export function describeBlocked(direction: Direction): string {
  return `The way ${WORDS[direction]} is blocked.`;
}

/** What the announcer last said, so the same sentence is not repeated back to the player. */
export interface AnnouncerState {
  readonly lastSaid: string | null;
}

/** An announcer that has said nothing yet. */
export const freshAnnouncer: AnnouncerState = { lastSaid: null };

/** The result of offering a sentence: the new state, and the sentence to say or null when it must be swallowed. */
export interface Announcement {
  readonly state: AnnouncerState;
  readonly say: string | null;
}

/**
 * Decides whether a sentence is said. An identical sentence in a row is swallowed, so a walk that the caller
 * reports step by step is still announced once it has settled.
 */
export function announce(state: AnnouncerState, sentence: string): Announcement {
  if (sentence === state.lastSaid) {
    return { state, say: null };
  }
  return { state: { lastSaid: sentence }, say: sentence };
}
