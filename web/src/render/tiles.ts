/** What a map tile is drawn as: a shape and glyph, so tiles differ by more than colour. */
export type TileShape = 'ground' | 'wall' | 'gate-closed' | 'gate-open' | 'unknown';

/** How one map tile looks. Colours are custom-property names so one stylesheet can tune contrast. */
export interface TileLook {
  readonly shape: TileShape;
  readonly glyph: string | null;
  readonly fill: string;
}

/** Picks the look of a map tile; a gate's letter stays visible and its shape says whether it is open. */
export function tileLook(tile: string | null, openGates: ReadonlySet<string>): TileLook {
  if (tile === '.') {
    return { shape: 'ground', glyph: null, fill: 'var(--tile-ground)' };
  }
  if (tile === '#') {
    return { shape: 'wall', glyph: null, fill: 'var(--tile-wall)' };
  }
  if (tile !== null && tile.length === 1 && tile >= 'A' && tile <= 'Z') {
    if (openGates.has(tile)) {
      return { shape: 'gate-open', glyph: tile, fill: 'var(--tile-gate-open)' };
    }
    return { shape: 'gate-closed', glyph: tile, fill: 'var(--tile-gate-closed)' };
  }
  return { shape: 'unknown', glyph: '?', fill: 'var(--tile-unknown)' };
}

/** What a character or the hero is drawn as on top of the map. */
export type MarkShape = 'hero' | 'npc';

/** How a mark on the map looks. */
export interface MarkLook {
  readonly shape: MarkShape;
  readonly glyph: string;
  readonly fill: string;
}

/** A character's look: its name's first letter, so players can tell characters apart without colour. */
export function npcLook(name: string): MarkLook {
  const first = name.charAt(0);
  return { shape: 'npc', glyph: first === '' ? '?' : first.toUpperCase(), fill: 'var(--mark-npc)' };
}

/** The hero's look: a single fixed glyph that no character uses. */
export const heroLook: MarkLook = { shape: 'hero', glyph: '@', fill: 'var(--mark-hero)' };
