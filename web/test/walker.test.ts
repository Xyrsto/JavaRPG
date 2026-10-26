import { describe, expect, it } from 'vitest';
import { cleared, emptyWalker, pressed, released, stepsDue, type WalkerState } from '../src/input/walker';
import { STEP_MS } from '../src/constants';

const HOUR_MS = 60 * 60 * 1000;

describe('emptyWalker', () => {
  it('holds nothing and owes a step immediately once something is pressed', () => {
    expect(emptyWalker).toEqual({ held: [], lastStepAt: null });
  });
});

describe('pressed', () => {
  it('owes exactly one step at once', () => {
    const state = pressed(emptyWalker, 'UP', 0);
    const due = stepsDue(state, 0);
    expect(due.steps).toBe(1);
    expect(due.direction).toBe('UP');
    expect(due.state.lastStepAt).toBe(0);
  });

  it('keeps the most recently pressed direction last, moving a repeated press to the end', () => {
    const state = pressed(pressed(pressed(emptyWalker, 'UP', 0), 'LEFT', 0), 'UP', 0);
    expect(state.held).toEqual(['LEFT', 'UP']);
  });
});

describe('holding a key', () => {
  it('owes one step per step of the cadence and none in between', () => {
    const start = stepsDue(pressed(emptyWalker, 'RIGHT', 0), 0).state;

    const atHalfStep = stepsDue(start, STEP_MS / 2);
    expect(atHalfStep.steps).toBe(0);

    const atOneStep = stepsDue(start, STEP_MS);
    expect(atOneStep.steps).toBe(1);

    const atTwoSteps = stepsDue(atOneStep.state, STEP_MS * 2);
    expect(atTwoSteps.steps).toBe(1);
  });

  it('does not drift: a late call still keeps the cadence grid', () => {
    const start = stepsDue(pressed(emptyWalker, 'DOWN', 0), 0).state;
    const late = stepsDue(start, STEP_MS + 10);
    expect(late.steps).toBe(1);
    expect(late.state.lastStepAt).toBe(STEP_MS);
  });

  it('pays out several steps for a short gap, up to four', () => {
    const start = stepsDue(pressed(emptyWalker, 'DOWN', 0), 0).state;
    const three = stepsDue(start, STEP_MS * 3);
    expect(three.steps).toBe(3);
    expect(three.state.lastStepAt).toBe(STEP_MS * 3);

    const four = stepsDue(start, STEP_MS * 4);
    expect(four.steps).toBe(4);
    expect(four.state.lastStepAt).toBe(STEP_MS * 4);
  });

  it('caps a long gap at one step rather than a burst', () => {
    const start = stepsDue(pressed(emptyWalker, 'LEFT', 0), 0).state;
    const five = stepsDue(start, STEP_MS * 5);
    expect(five.steps).toBe(1);
    expect(five.state.lastStepAt).toBe(STEP_MS * 5);
  });

  it('a 10 second gap while held owes 1 step, not 80', () => {
    const start = stepsDue(pressed(emptyWalker, 'LEFT', 0), 0).state;
    const due = stepsDue(start, 10_000);
    expect(due.steps).toBe(1);
    expect(due.state.lastStepAt).toBe(10_000);
  });
});

describe('several held directions', () => {
  it('uses the most recently pressed direction, and falls back when that one is released', () => {
    const up = pressed(emptyWalker, 'UP', 0);
    const both = pressed(up, 'LEFT', 0);
    expect(stepsDue(both, 0).direction).toBe('LEFT');

    const afterRelease = released(both, 'LEFT');
    expect(afterRelease.held).toEqual(['UP']);
    expect(stepsDue(afterRelease, 0).direction).toBe('UP');
  });
});

describe('released', () => {
  it('owes nothing once the only key is released', () => {
    const held = stepsDue(pressed(emptyWalker, 'UP', 0), 0).state;
    const let_go = released(held, 'UP');
    expect(let_go.held).toEqual([]);
    expect(let_go.lastStepAt).toBeNull();
    const later = stepsDue(let_go, 500);
    expect(later.steps).toBe(0);
    expect(later.direction).toBeNull();
  });
});

describe('cleared', () => {
  it('returns to the empty state and owes nothing, even an hour later', () => {
    const held = stepsDue(pressed(emptyWalker, 'UP', 0), 0).state;
    const reset = cleared(held);
    expect(reset).toEqual(emptyWalker);
    expect(stepsDue(reset, HOUR_MS).steps).toBe(0);
  });

  it('nothing accumulates across the gap: a fresh press after an hour owes one step', () => {
    const reset = cleared(stepsDue(pressed(emptyWalker, 'UP', 0), 0).state);
    const again = stepsDue(pressed(reset, 'UP', HOUR_MS), HOUR_MS);
    expect(again.steps).toBe(1);
  });
});

describe('input immutability', () => {
  it('never mutates the state it is given', () => {
    const before: WalkerState = { held: ['UP'], lastStepAt: 50 };
    const snapshot = { held: [...before.held], lastStepAt: before.lastStepAt };

    pressed(before, 'LEFT', 100);
    released(before, 'UP');
    cleared(before);
    stepsDue(before, 500);

    expect(before.held).toEqual(snapshot.held);
    expect(before.lastStepAt).toBe(snapshot.lastStepAt);
  });
});
