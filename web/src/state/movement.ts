import type { Direction } from '../api/client';
import { MAX_WALK } from '../constants';
import { isBlocked } from './blocked';
import type { Point, WorldModel } from './world';

/** A refused step, kept so the page can mark the tile the hero bumped into. */
export interface Bump {
  readonly x: number;
  readonly y: number;
  readonly direction: Direction;
  readonly at: number;
}

/** The hero's position as the server knows it, as the page draws it, and the steps still waiting on the server. */
export interface MovementState {
  /** The last position the server reported: the truth. */
  readonly confirmed: Point;
  /** The position the page draws: always the truth plus every step not yet confirmed. */
  readonly shown: Point;
  /** The steps of the walk being answered right now. */
  readonly inFlight: readonly Direction[];
  /** Steps taken since that walk was sent, not sent yet. */
  readonly queued: readonly Direction[];
  readonly bump: Bump | null;
}

/** What the caller must do next: nothing, send a walk, or re-read the player from the server. */
export type Effect =
  | { readonly kind: 'none' }
  | { readonly kind: 'send'; readonly directions: readonly Direction[] }
  | { readonly kind: 'resync' };

/** The result of one event: the new state, what to do next, and whether the hero moved or hit a wall. */
export interface Outcome {
  readonly state: MovementState;
  readonly effect: Effect;
  readonly moved: boolean;
  readonly blocked: boolean;
}

const NONE: Effect = { kind: 'none' };
const RESYNC: Effect = { kind: 'resync' };

/** The state for a hero the server has just placed, with nothing pending. */
export function start(hero: Point): MovementState {
  return { confirmed: { x: hero.x, y: hero.y }, shown: { x: hero.x, y: hero.y }, inFlight: [], queued: [], bump: null };
}

/** One tile in a direction. Rows count from the top, so UP is y minus one. */
export function stepOf(point: Point, direction: Direction): Point {
  switch (direction) {
    case 'UP':
      return { x: point.x, y: point.y - 1 };
    case 'DOWN':
      return { x: point.x, y: point.y + 1 };
    case 'LEFT':
      return { x: point.x - 1, y: point.y };
    case 'RIGHT':
      return { x: point.x + 1, y: point.y };
  }
}

/** The position reached from a point by taking the steps in order. Walks are relative, so order matters. */
export function apply(point: Point, directions: readonly Direction[]): Point {
  return directions.reduce<Point>((at, direction) => stepOf(at, direction), point);
}

/**
 * One press of a direction key. A step into a wall is refused and never sent, because the server should not pay for
 * it; a step the server would not take in one walk is refused so the page never shows an unjustified position.
 */
export function step(
  state: MovementState,
  world: WorldModel,
  openGates: ReadonlySet<string>,
  direction: Direction,
  now: number,
): Outcome {
  const target = stepOf(state.shown, direction);
  if (isBlocked(world, openGates, target.x, target.y)) {
    const bump: Bump = { x: target.x, y: target.y, direction, at: now };
    return { state: { ...state, bump }, effect: NONE, moved: false, blocked: true };
  }
  if (state.inFlight.length + state.queued.length >= MAX_WALK) {
    return { state, effect: NONE, moved: false, blocked: false };
  }
  if (state.inFlight.length === 0) {
    const next: MovementState = { ...state, shown: target, inFlight: [direction], queued: [] };
    return { state: next, effect: { kind: 'send', directions: [direction] }, moved: true, blocked: false };
  }
  const next: MovementState = { ...state, shown: target, queued: [...state.queued, direction] };
  return { state: next, effect: NONE, moved: true, blocked: false };
}

/**
 * The server's answer to the walk in flight. A clean answer sends the queued steps on; a blocked answer means the
 * client's copy of the rules disagreed with the server, so the page snaps back and re-reads the world.
 */
export function answered(
  state: MovementState,
  answer: { readonly x: number; readonly y: number; readonly blocked: boolean },
): Outcome {
  const confirmed: Point = { x: answer.x, y: answer.y };
  if (answer.blocked) {
    const snapped: MovementState = { confirmed, shown: confirmed, inFlight: [], queued: [], bump: null };
    return { state: snapped, effect: RESYNC, moved: false, blocked: true };
  }
  const nextWalk = state.queued;
  const next: MovementState = {
    confirmed,
    shown: apply(confirmed, nextWalk),
    inFlight: nextWalk,
    queued: [],
    bump: state.bump,
  };
  const effect: Effect = nextWalk.length > 0 ? { kind: 'send', directions: nextWalk } : NONE;
  return { state: next, effect, moved: false, blocked: false };
}

/**
 * The walk failed for any reason. The walk is never resent, since a walk is relative and a lost answer may already
 * have moved the hero; the page goes back to the server position and asks to re-read it.
 */
export function failed(state: MovementState): Outcome {
  const back: MovementState = { confirmed: state.confirmed, shown: state.confirmed, inFlight: [], queued: [], bump: state.bump };
  return { state: back, effect: RESYNC, moved: false, blocked: false };
}

/** The server has told the page where the hero really is: everything pending is dropped and the drawn position follows. */
export function resynced(state: MovementState, hero: Point): Outcome {
  return { state: start(hero), effect: NONE, moved: false, blocked: false };
}
