import { describe, expect, it } from 'vitest';
import {
  announce,
  describeBlocked,
  describeCorrection,
  describeSettled,
  freshAnnouncer,
} from '../src/a11y/worldText';
import type { Direction } from '../src/api/client';

describe('the sentences for a player who is not looking at the drawing', () => {
  it('announces the settled position counting columns and rows from one', () => {
    expect(describeSettled({ x: 5, y: 4 })).toBe('Hero at column 6, row 5.');
    expect(describeSettled({ x: 0, y: 0 })).toBe('Hero at column 1, row 1.');
  });

  it('says the server moved the hero, counting from one as well', () => {
    expect(describeCorrection({ x: 5, y: 4 })).toBe('The game server moved the hero to column 6, row 5.');
    expect(describeCorrection({ x: 0, y: 0 })).toBe('The game server moved the hero to column 1, row 1.');
  });

  it('says which way is blocked in words, for all four directions', () => {
    expect(describeBlocked('UP')).toBe('The way up is blocked.');
    expect(describeBlocked('DOWN')).toBe('The way down is blocked.');
    expect(describeBlocked('LEFT')).toBe('The way left is blocked.');
    expect(describeBlocked('RIGHT')).toBe('The way right is blocked.');
  });

  it('says a new sentence, swallows an immediate repeat, and says it again after something else', () => {
    const first = announce(freshAnnouncer, 'Hero at column 1, row 1.');
    expect(first.say).toBe('Hero at column 1, row 1.');

    const repeat = announce(first.state, 'Hero at column 1, row 1.');
    expect(repeat.say).toBeNull();

    const other = announce(repeat.state, 'The way up is blocked.');
    expect(other.say).toBe('The way up is blocked.');

    const again = announce(other.state, 'Hero at column 1, row 1.');
    expect(again.say).toBe('Hero at column 1, row 1.');
  });

  it('never puts markup characters in any sentence', () => {
    const directions: Direction[] = ['UP', 'DOWN', 'LEFT', 'RIGHT'];
    const positions = [
      { x: 0, y: 0 },
      { x: 31, y: 9 },
    ];
    const texts: string[] = [];
    for (const position of positions) {
      texts.push(describeSettled(position), describeCorrection(position));
    }
    for (const direction of directions) {
      texts.push(describeBlocked(direction));
    }
    for (const text of texts) {
      expect(text).not.toMatch(/[<>&]/);
    }
  });
});
