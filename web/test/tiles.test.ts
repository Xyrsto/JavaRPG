import { describe, expect, it } from 'vitest';
import { heroLook, npcLook, tileLook } from '../src/render/tiles';
import type { MarkLook, TileLook } from '../src/render/tiles';

const noGates: ReadonlySet<string> = new Set();

describe('tileLook', () => {
  it('draws floor as plain ground with no glyph', () => {
    expect(tileLook('.', noGates)).toEqual({ shape: 'ground', glyph: null, fill: 'var(--tile-ground)' });
  });

  it('draws a wall with no glyph', () => {
    expect(tileLook('#', noGates)).toEqual({ shape: 'wall', glyph: null, fill: 'var(--tile-wall)' });
  });

  it('draws a closed gate with its letter', () => {
    expect(tileLook('A', noGates)).toEqual({ shape: 'gate-closed', glyph: 'A', fill: 'var(--tile-gate-closed)' });
  });

  it('draws an open gate with its letter', () => {
    expect(tileLook('A', new Set(['A']))).toEqual({ shape: 'gate-open', glyph: 'A', fill: 'var(--tile-gate-open)' });
  });

  it('gives an unexplained character the unknown look', () => {
    expect(tileLook('~', noGates)).toEqual({ shape: 'unknown', glyph: '?', fill: 'var(--tile-unknown)' });
  });

  it('gives a missing tile (outside the map) the unknown look', () => {
    expect(tileLook(null, noGates)).toEqual({ shape: 'unknown', glyph: '?', fill: 'var(--tile-unknown)' });
  });

  it('gives the same gate letter a different shape when it is open', () => {
    const closed = tileLook('A', noGates);
    const open = tileLook('A', new Set(['A']));
    expect(closed.shape).not.toBe(open.shape);
    expect(closed.glyph).toBe('A');
    expect(open.glyph).toBe('A');
  });
});

describe('npcLook and heroLook', () => {
  it('uses the upper-cased first letter of the name as the character glyph', () => {
    expect(npcLook('elder mira')).toEqual({ shape: 'npc', glyph: 'E', fill: 'var(--mark-npc)' });
  });

  it('uses ? as the character glyph when the name is empty', () => {
    expect(npcLook('').glyph).toBe('?');
  });

  it('draws the hero as @', () => {
    expect(heroLook).toEqual({ shape: 'hero', glyph: '@', fill: 'var(--mark-hero)' });
  });
});

describe('visual distinctness', () => {
  it('gives every kind of thing a different shape or glyph, not only a different colour', () => {
    const looks: readonly (TileLook | MarkLook)[] = [
      tileLook('.', noGates),
      tileLook('#', noGates),
      tileLook('A', noGates),
      tileLook('A', new Set(['A'])),
      npcLook('elder mira'),
      heroLook,
    ];
    const keys = looks.map((look) => `${look.shape}|${look.glyph ?? ''}`);
    for (let i = 0; i < keys.length; i++) {
      for (let j = i + 1; j < keys.length; j++) {
        expect(keys[i], `looks ${i} and ${j}`).not.toBe(keys[j]);
      }
    }
  });

  it('uses only custom-property colour references', () => {
    const fills = [
      tileLook('.', noGates).fill,
      tileLook('#', noGates).fill,
      tileLook('A', noGates).fill,
      tileLook('A', new Set(['A'])).fill,
      tileLook('~', noGates).fill,
      npcLook('elder mira').fill,
      heroLook.fill,
    ];
    for (const fill of fills) {
      expect(fill.startsWith('var(--')).toBe(true);
    }
  });
});
