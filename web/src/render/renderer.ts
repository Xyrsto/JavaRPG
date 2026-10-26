import type { Direction } from '../api/client';
import type { WorldModel } from '../state/world';
import type { Fit } from './geometry';
import type { Visual } from './animation';
import type { DrawTarget, Palette } from './surface';
import { heroLook, npcLook, type MarkLook } from './tiles';

/** Everything that changes from frame to frame. The unchanging map is the blitted layer, drawn elsewhere. */
export interface Scene {
  readonly world: WorldModel;
  /** Where the hero is drawn, in fractional tiles, so a walk looks smooth. */
  readonly hero: Visual;
  /** The last direction walked, shown as a pointer so the hero differs from a character by shape, not colour. */
  readonly facing: Direction;
  /** The tile a step was refused into, while the marker is still fresh, or null. */
  readonly bumpTile: { readonly x: number; readonly y: number } | null;
}

/** Half a tile, as a pixel offset: the centre of the tile at a fractional position. */
function centre(value: number, origin: number, tile: number): number {
  return origin + value * tile + tile / 2;
}

/** A character or the hero: a disc with a letter, so they read as people and not as part of the map. */
function drawMark(target: DrawTarget, look: MarkLook, cx: number, cy: number, tile: number, palette: Palette): void {
  target.beginPath();
  target.arc(cx, cy, tile * 0.38, 0, Math.PI * 2);
  target.fillStyle = palette(look.fill);
  target.fill();
  target.fillStyle = palette('var(--mark-glyph)');
  target.fillText(look.glyph, cx, cy);
}

/** How far along each axis the hero's pointer reaches, from the direction last walked. */
function pointerOffset(facing: Direction): { readonly dx: number; readonly dy: number } {
  switch (facing) {
    case 'UP':
      return { dx: 0, dy: -1 };
    case 'DOWN':
      return { dx: 0, dy: 1 };
    case 'LEFT':
      return { dx: -1, dy: 0 };
    case 'RIGHT':
      return { dx: 1, dy: 0 };
  }
}

/**
 * One frame. The work here is a blit, one disc per character, the hero and at most one marker — it never walks the
 * map, so the cost of a frame does not grow with the size of the world. That is what keeps 60 frames a second true
 * for the larger worlds still to come.
 */
export function draw(
  target: DrawTarget,
  scene: Scene,
  fit: Fit,
  palette: Palette,
  layer: CanvasImageSource | null,
): void {
  const { tile, originX, originY } = fit;
  target.clearRect(0, 0, fit.canvasW, fit.canvasH);
  if (tile <= 0) {
    return;
  }
  if (layer !== null) {
    target.drawImage(layer, 0, 0);
  }
  target.textAlign = 'center';
  target.textBaseline = 'middle';
  target.font = `${String(Math.floor(tile * 0.5))}px system-ui, sans-serif`;

  for (const npc of scene.world.npcs) {
    drawMark(target, npcLook(npc.name), centre(npc.x, originX, tile), centre(npc.y, originY, tile), tile, palette);
  }

  const heroX = centre(scene.hero.x, originX, tile);
  const heroY = centre(scene.hero.y, originY, tile);
  drawMark(target, heroLook, heroX, heroY, tile, palette);
  const pointer = pointerOffset(scene.facing);
  target.strokeStyle = palette('var(--mark-hero)');
  target.lineWidth = Math.max(2, Math.floor(tile / 10));
  target.beginPath();
  target.moveTo(heroX + pointer.dx * tile * 0.3, heroY + pointer.dy * tile * 0.3);
  target.lineTo(heroX + pointer.dx * tile * 0.5, heroY + pointer.dy * tile * 0.5);
  target.stroke();

  if (scene.bumpTile !== null) {
    // A refused step is marked by an outline and said in words elsewhere, never by colour alone, so the sign
    // survives a player who has asked for reduced motion.
    const width = Math.max(2, Math.floor(tile / 8));
    target.strokeStyle = palette('var(--bump)');
    target.lineWidth = width;
    target.strokeRect(
      originX + scene.bumpTile.x * tile + width / 2,
      originY + scene.bumpTile.y * tile + width / 2,
      tile - width,
      tile - width,
    );
  }
}
