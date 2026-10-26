import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { isBlocked } from '../src/state/blocked';
import type { WorldGate, WorldModel, WorldNpc } from '../src/state/world';

interface VillageFile {
  readonly id: string;
  readonly width: number;
  readonly height: number;
  readonly rows: readonly string[];
  readonly heroStart: { readonly x: number; readonly y: number };
  readonly npcs: readonly { readonly id: string; readonly name: string; readonly questId: string; readonly x: number; readonly y: number }[];
}

/** Builds the model the way buildWorld would: gates are the capital letters in the rows. */
function modelFrom(file: VillageFile): WorldModel {
  const npcs: WorldNpc[] = file.npcs.map((npc) => ({ id: npc.id, name: npc.name, questId: npc.questId, x: npc.x, y: npc.y }));
  const gates: WorldGate[] = [];
  file.rows.forEach((row, y) => {
    [...row].forEach((ch, x) => {
      if (ch >= 'A' && ch <= 'Z') {
        gates.push({ id: ch, x, y });
      }
    });
  });
  return {
    id: file.id,
    width: file.width,
    height: file.height,
    rows: file.rows,
    legend: {},
    npcs,
    gates,
    heroStart: { x: file.heroStart.x, y: file.heroStart.y },
  };
}

const villagePath = new URL('../../content/world/village.json', import.meta.url);
const village = modelFrom(JSON.parse(readFileSync(villagePath, 'utf8')) as VillageFile);
const noGates: ReadonlySet<string> = new Set();

describe('isBlocked on the village map', () => {
  it.each([
    ['left of the map', -1, 4],
    ['above the map', 5, -1],
    ['right of the map', village.width, 4],
    ['below the map', 5, village.height],
  ])('blocks %s', (_label, x, y) => {
    expect(isBlocked(village, noGates, x, y)).toBe(true);
  });

  it('blocks the whole top row, which is all walls', () => {
    for (let x = 0; x < village.width; x++) {
      expect(isBlocked(village, noGates, x, 0)).toBe(true);
    }
  });

  it('does not block the hero start tile', () => {
    expect(isBlocked(village, noGates, village.heroStart.x, village.heroStart.y)).toBe(false);
  });

  it('blocks the tile where Elder Mira stands', () => {
    expect(isBlocked(village, noGates, 3, 2)).toBe(true);
  });

  it('blocks the tile where Smith Orin stands', () => {
    expect(isBlocked(village, noGates, 8, 2)).toBe(true);
  });

  it('blocks the gate A tile while the gate is closed', () => {
    expect(isBlocked(village, noGates, 5, 6)).toBe(true);
  });

  it('lets the hero onto the gate A tile once A is open', () => {
    expect(isBlocked(village, new Set(['A']), 5, 6)).toBe(false);
  });
});

describe('isBlocked on a map with an unknown character', () => {
  const tiny: WorldModel = {
    id: 'tiny',
    width: 3,
    height: 3,
    rows: ['...', '.~.', '...'],
    legend: {},
    npcs: [],
    gates: [],
    heroStart: { x: 0, y: 0 },
  };

  it('blocks a tile holding a character the client does not know', () => {
    expect(isBlocked(tiny, noGates, 1, 1)).toBe(true);
  });

  it('still lets the hero onto plain floor on the same map', () => {
    expect(isBlocked(tiny, noGates, 0, 0)).toBe(false);
  });
});
