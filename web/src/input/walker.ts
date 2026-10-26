import type { Direction } from '../api/client';
import { STEP_MS } from '../constants';

/** The longest gap, in steps, that is paid out as several steps. Anything longer counts as one step. */
const MAX_CATCH_UP = 4;

/** What the walker remembers between frames: the held directions, oldest press first, and when the last step was owed. */
export interface WalkerState {
  readonly held: readonly Direction[];
  readonly lastStepAt: number | null;
}

/** The walker with no keys held. Nothing is owed until a key is pressed. */
export const emptyWalker: WalkerState = { held: [], lastStepAt: null };

/** A key went down. The most recent press wins, and the step is owed at once so the press is felt immediately. */
export function pressed(state: WalkerState, direction: Direction, _now: number): WalkerState {
  return { held: [...state.held.filter((d) => d !== direction), direction], lastStepAt: null };
}

/** A key came up. Once no key is held, nothing is owed, so a released key never leaves the hero walking. */
export function released(state: WalkerState, direction: Direction): WalkerState {
  const held = state.held.filter((d) => d !== direction);
  return { held, lastStepAt: held.length === 0 ? null : state.lastStepAt };
}

/** The page lost focus or was hidden. Forgets every key so a lost key-release cannot leave the hero walking forever. */
export function cleared(_state: WalkerState): WalkerState {
  return { held: [], lastStepAt: null };
}

/** The steps owed at a given moment, the direction to take them in, and the walker state after paying them. */
export interface StepsDue {
  readonly steps: number;
  readonly direction: Direction | null;
  readonly state: WalkerState;
}

/**
 * How many steps the hero is owed at time `now`, in the direction of the most recently pressed key. Steps come at
 * one per STEP_MS while held. A long gap between calls pays at most one step rather than a burst, and a short gap
 * is paid in whole steps while keeping the STEP_MS grid so the pace does not drift.
 */
export function stepsDue(state: WalkerState, now: number): StepsDue {
  const direction = state.held[state.held.length - 1] ?? null;
  if (direction === null) {
    return { steps: 0, direction: null, state: { held: [], lastStepAt: null } };
  }
  if (state.lastStepAt === null) {
    return { steps: 1, direction, state: { held: [...state.held], lastStepAt: now } };
  }
  const owed = Math.floor((now - state.lastStepAt) / STEP_MS);
  if (owed <= 0) {
    return { steps: 0, direction, state: { held: [...state.held], lastStepAt: state.lastStepAt } };
  }
  if (owed > MAX_CATCH_UP) {
    return { steps: 1, direction, state: { held: [...state.held], lastStepAt: now } };
  }
  return {
    steps: owed,
    direction,
    state: { held: [...state.held], lastStepAt: state.lastStepAt + owed * STEP_MS },
  };
}
