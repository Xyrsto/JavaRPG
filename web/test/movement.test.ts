import { describe, expect, it } from 'vitest';
import type { Direction } from '../src/api/client';
import { MAX_WALK } from '../src/constants';
import {
  answered,
  apply,
  failed,
  resynced,
  start,
  step,
  stepOf,
} from '../src/state/movement';
import type { MovementState, Outcome } from '../src/state/movement';
import type { Point, WorldModel } from '../src/state/world';

/** A closed room of open floor: walls on every edge, floor inside. Wide enough for a 32-step walk. */
function room(width: number, height: number): WorldModel {
  const rows = Array.from({ length: height }, (_, y) =>
    Array.from({ length: width }, (_, x) =>
      x === 0 || y === 0 || x === width - 1 || y === height - 1 ? '#' : '.',
    ).join(''),
  );
  return {
    id: 'test-room',
    width,
    height,
    rows,
    legend: { '#': 'wall', '.': 'floor' },
    npcs: [],
    gates: [],
    heroStart: { x: 2, y: 2 },
  };
}

const WORLD = room(40, 5);
const NO_GATES: ReadonlySet<string> = new Set();
const HERO: Point = { x: 2, y: 2 };

/** The one rule every state must keep: the drawn position is the server's position plus every step not yet confirmed. */
function assertInvariant(state: MovementState): void {
  expect(state.shown).toEqual(apply(state.confirmed, [...state.inFlight, ...state.queued]));
}

/** Checks the invariant on the state an event produced, then hands the outcome back. */
function checked(outcome: Outcome): Outcome {
  assertInvariant(outcome.state);
  return outcome;
}

/** A small seeded random number source, so the random sequences are the same on every run. */
function prng(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function randomDirection(rand: () => number): Direction {
  const n = rand();
  if (n < 0.25) return 'UP';
  if (n < 0.5) return 'DOWN';
  if (n < 0.75) return 'LEFT';
  return 'RIGHT';
}

describe('the movement reducer', () => {
  it('starts with the server position as both the truth and the drawn position, nothing pending', () => {
    const state = start(HERO);
    expect(state).toEqual({ confirmed: HERO, shown: HERO, inFlight: [], queued: [], bump: null });
    assertInvariant(state);
  });

  it('moves UP as y minus one, DOWN as y plus one, LEFT as x minus one and RIGHT as x plus one', () => {
    const at: Point = { x: 5, y: 5 };
    expect(stepOf(at, 'UP')).toEqual({ x: 5, y: 4 });
    expect(stepOf(at, 'DOWN')).toEqual({ x: 5, y: 6 });
    expect(stepOf(at, 'LEFT')).toEqual({ x: 4, y: 5 });
    expect(stepOf(at, 'RIGHT')).toEqual({ x: 6, y: 5 });
  });

  it('applies a list of steps in order and returns the start unchanged for an empty list', () => {
    expect(apply(HERO, [])).toEqual(HERO);
    expect(apply(HERO, ['RIGHT', 'RIGHT', 'DOWN', 'LEFT'])).toEqual({ x: 3, y: 3 });
  });

  it('never mutates the state it is given', () => {
    const state = start({ x: 1, y: 2 });
    const before = JSON.parse(JSON.stringify(state)) as unknown;
    checked(step(state, WORLD, NO_GATES, 'LEFT', 1));
    checked(step(state, WORLD, NO_GATES, 'RIGHT', 2));
    expect(JSON.parse(JSON.stringify(state)) as unknown).toEqual(before);
  });

  describe('a step into a wall', () => {
    it('moves nothing, sends nothing and records the bump at the refused tile', () => {
      const state = start({ x: 1, y: 2 });
      const outcome = checked(step(state, WORLD, NO_GATES, 'LEFT', 7));
      expect(outcome.state.shown).toEqual({ x: 1, y: 2 });
      expect(outcome.state.confirmed).toEqual({ x: 1, y: 2 });
      expect(outcome.state.inFlight).toEqual([]);
      expect(outcome.state.queued).toEqual([]);
      expect(outcome.state.bump).toEqual({ x: 0, y: 2, direction: 'LEFT', at: 7 });
      expect(outcome.effect).toEqual({ kind: 'none' });
      expect(outcome.moved).toBe(false);
      expect(outcome.blocked).toBe(true);
    });
  });

  describe('a step on open floor', () => {
    it('sends the first step at once', () => {
      const outcome = checked(step(start(HERO), WORLD, NO_GATES, 'RIGHT', 0));
      expect(outcome.state.shown).toEqual({ x: 3, y: 2 });
      expect(outcome.state.inFlight).toEqual(['RIGHT']);
      expect(outcome.state.queued).toEqual([]);
      expect(outcome.effect).toEqual({ kind: 'send', directions: ['RIGHT'] });
      expect(outcome.moved).toBe(true);
      expect(outcome.blocked).toBe(false);
    });

    it('queues later steps while a walk is in flight, without sending them', () => {
      const first = checked(step(start(HERO), WORLD, NO_GATES, 'RIGHT', 0));
      const second = checked(step(first.state, WORLD, NO_GATES, 'DOWN', 1));
      expect(second.state.shown).toEqual({ x: 3, y: 3 });
      expect(second.state.inFlight).toEqual(['RIGHT']);
      expect(second.state.queued).toEqual(['DOWN']);
      expect(second.effect).toEqual({ kind: 'none' });
      expect(second.moved).toBe(true);
    });
  });

  describe('an answer that is not blocked', () => {
    it('leaves the drawn position exactly where it was and hands the queued steps on as the next send', () => {
      const first = checked(step(start(HERO), WORLD, NO_GATES, 'RIGHT', 0));
      const second = checked(step(first.state, WORLD, NO_GATES, 'RIGHT', 1));
      const third = checked(step(second.state, WORLD, NO_GATES, 'RIGHT', 2));
      const before = third.state.shown;

      const answer = checked(answered(third.state, { x: 3, y: 2, blocked: false }));
      expect(answer.state.shown).toEqual(before);
      expect(answer.state.confirmed).toEqual({ x: 3, y: 2 });
      expect(answer.state.inFlight).toEqual(['RIGHT', 'RIGHT']);
      expect(answer.state.queued).toEqual([]);
      expect(answer.effect).toEqual({ kind: 'send', directions: ['RIGHT', 'RIGHT'] });
      expect(answer.moved).toBe(false);
      expect(answer.blocked).toBe(false);

      const last = checked(answered(answer.state, { x: 5, y: 2, blocked: false }));
      expect(last.state.shown).toEqual({ x: 5, y: 2 });
      expect(last.state.inFlight).toEqual([]);
      expect(last.state.queued).toEqual([]);
      expect(last.effect).toEqual({ kind: 'none' });
    });
  });

  describe('an answer that is blocked', () => {
    it('snaps the drawn position to the server, empties both lists, clears the bump and asks for a resync', () => {
      const bumped = checked(step(start({ x: 1, y: 2 }), WORLD, NO_GATES, 'LEFT', 3));
      const first = checked(step(bumped.state, WORLD, NO_GATES, 'RIGHT', 4));
      const second = checked(step(first.state, WORLD, NO_GATES, 'RIGHT', 5));
      const third = checked(step(second.state, WORLD, NO_GATES, 'DOWN', 6));
      expect(third.state.queued).toEqual(['RIGHT', 'DOWN']);

      const outcome = checked(answered(third.state, { x: 1, y: 2, blocked: true }));
      expect(outcome.state.confirmed).toEqual({ x: 1, y: 2 });
      expect(outcome.state.shown).toEqual({ x: 1, y: 2 });
      expect(outcome.state.inFlight).toEqual([]);
      expect(outcome.state.queued).toEqual([]);
      expect(outcome.state.bump).toBeNull();
      expect(outcome.effect).toEqual({ kind: 'resync' });
      expect(outcome.moved).toBe(false);
      expect(outcome.blocked).toBe(true);
    });
  });

  describe('a failed call', () => {
    it('empties both lists, puts the drawn position back on the server position and asks for a resync, never a send', () => {
      const first = checked(step(start(HERO), WORLD, NO_GATES, 'RIGHT', 0));
      const second = checked(step(first.state, WORLD, NO_GATES, 'RIGHT', 1));

      const outcome = checked(failed(second.state));
      expect(outcome.state.shown).toEqual(HERO);
      expect(outcome.state.confirmed).toEqual(HERO);
      expect(outcome.state.inFlight).toEqual([]);
      expect(outcome.state.queued).toEqual([]);
      expect(outcome.effect).toEqual({ kind: 'resync' });
      expect(outcome.moved).toBe(false);
      expect(outcome.blocked).toBe(false);
    });

    it('asks for a resync even when nothing was pending', () => {
      const outcome = checked(failed(start(HERO)));
      expect(outcome.effect).toEqual({ kind: 'resync' });
    });
  });

  describe('a resync', () => {
    it('makes the given hero both the truth and the drawn position, with nothing pending', () => {
      const bumped = checked(step(start({ x: 1, y: 2 }), WORLD, NO_GATES, 'LEFT', 3));
      const walking = checked(step(bumped.state, WORLD, NO_GATES, 'RIGHT', 4));
      const outcome = checked(resynced(walking.state, { x: 9, y: 1 }));
      expect(outcome.state).toEqual({ confirmed: { x: 9, y: 1 }, shown: { x: 9, y: 1 }, inFlight: [], queued: [], bump: null });
      expect(outcome.effect).toEqual({ kind: 'none' });
    });
  });

  describe('the walk limit', () => {
    it('refuses a step at MAX_WALK pending steps: the state comes back unchanged and the drawn position does not move', () => {
      let state = start(HERO);
      for (let press = 0; press < MAX_WALK; press += 1) {
        state = checked(step(state, WORLD, NO_GATES, 'RIGHT', press)).state;
      }
      expect(state.inFlight.length + state.queued.length).toBe(MAX_WALK);

      const before = state;
      const refused = checked(step(state, WORLD, NO_GATES, 'RIGHT', 99));
      expect(refused.state).toBe(before);
      expect(refused.state.shown).toEqual({ x: HERO.x + MAX_WALK, y: HERO.y });
      expect(refused.effect).toEqual({ kind: 'none' });
      expect(refused.moved).toBe(false);
      expect(refused.blocked).toBe(false);
    });
  });

  describe('end to end against a fake server', () => {
    it('walks 20 presses to the right: the hero ends 20 tiles along, each step is sent exactly once, the invariant holds throughout', () => {
      // The fake server answers one walk at a time, and only every third press, so later presses really queue.
      let state = start(HERO);
      let server: Point = HERO;
      const inbox: (readonly Direction[])[] = [];
      const sent: Direction[] = [];

      const take = (outcome: Outcome): void => {
        checked(outcome);
        expect(outcome.effect.kind).not.toBe('resync');
        if (outcome.effect.kind === 'send') {
          sent.push(...outcome.effect.directions);
          inbox.push(outcome.effect.directions);
        }
        state = outcome.state;
      };

      const drain = (): void => {
        for (let walk = inbox.shift(); walk !== undefined; walk = inbox.shift()) {
          server = apply(server, walk);
          take(answered(state, { x: server.x, y: server.y, blocked: false }));
        }
      };

      for (let press = 0; press < 20; press += 1) {
        const outcome = step(state, WORLD, NO_GATES, 'RIGHT', press);
        expect(outcome.moved).toBe(true);
        take(outcome);
        if (press % 3 === 2) {
          drain();
        }
      }
      drain();

      expect(server).toEqual({ x: HERO.x + 20, y: HERO.y });
      expect(state.confirmed).toEqual(server);
      expect(state.shown).toEqual(server);
      expect(state.inFlight).toEqual([]);
      expect(state.queued).toEqual([]);
      expect(sent).toHaveLength(20);
      expect(sent.every((direction) => direction === 'RIGHT')).toBe(true);
    });
  });

  describe('long random sequences', () => {
    it('keeps the invariant after every event of presses, answers, failures and resyncs', () => {
      for (const seed of [1, 7, 42, 1234, 99991]) {
        const rand = prng(seed);
        let state = start(HERO);
        for (let now = 0; now < 400; now += 1) {
          const roll = rand();
          let outcome: Outcome;
          if (roll < 0.6) {
            outcome = step(state, WORLD, NO_GATES, randomDirection(rand), now);
            if (outcome.effect.kind === 'send') {
              expect(outcome.effect.directions.length).toBeGreaterThan(0);
            }
          } else if (roll < 0.8) {
            outcome = answered(state, {
              x: Math.floor(rand() * 40),
              y: Math.floor(rand() * 5),
              blocked: rand() < 0.2,
            });
          } else if (roll < 0.9) {
            outcome = failed(state);
            expect(outcome.effect).toEqual({ kind: 'resync' });
          } else {
            outcome = resynced(state, { x: 1 + Math.floor(rand() * 38), y: 2 });
          }
          checked(outcome);
          state = outcome.state;
        }
      }
    });
  });
});
