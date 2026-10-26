import type { WorldModel } from '../state/world';
import { tileAt } from '../state/world';
import type { Fit } from './geometry';
import type { DrawTarget, MakeSurface, Palette } from './surface';
import { tileLook } from './tiles';

/**
 * The part of the map that does not change between frames: ground, walls and gates. It is drawn once into an
 * offscreen image and blitted every frame, so the work a frame costs does not grow with the size of the map.
 */
export interface LayerCache {
  key: string | null;
  image: CanvasImageSource | null;
}

/** A cache with nothing in it yet. */
export function makeCache(): LayerCache {
  return { key: null, image: null };
}

/**
 * Everything that changes what the unchanging part looks like: which world it is, which gates are open for this
 * player, and how big a tile is now. Anything else (the hero walking, a character, a refused step) is drawn on top
 * each frame and must never invalidate the layer.
 */
export function layerKey(world: WorldModel, openGates: ReadonlySet<string>, fit: Fit): string {
  const gates = [...openGates].sort().join('');
  return `${world.id}|${gates}|${String(fit.tile)}|${String(fit.canvasW)}x${String(fit.canvasH)}`;
}

/** Draws the ground, the walls and the gates, each tile telling what it is by shape and letter as well as colour. */
function drawTiles(
  target: DrawTarget,
  world: WorldModel,
  openGates: ReadonlySet<string>,
  fit: Fit,
  palette: Palette,
): void {
  const { tile, originX, originY } = fit;
  const inset = Math.max(1, Math.floor(tile / 12));
  target.textAlign = 'center';
  target.textBaseline = 'middle';
  target.font = `${String(Math.floor(tile * 0.62))}px system-ui, sans-serif`;
  for (let y = 0; y < world.height; y += 1) {
    for (let x = 0; x < world.width; x += 1) {
      const look = tileLook(tileAt(world, x, y), openGates);
      const left = originX + x * tile;
      const top = originY + y * tile;
      target.fillStyle = palette(look.fill);
      target.fillRect(left, top, tile, tile);
      if (look.shape === 'wall') {
        // A raised inner edge, so a wall is a wall without relying on its colour.
        target.strokeStyle = palette('var(--tile-glyph)');
        target.lineWidth = inset;
        target.strokeRect(left + inset, top + inset, tile - inset * 2, tile - inset * 2);
      }
      if (look.shape === 'gate-closed') {
        // Bars across a closed gate; an open one is left clear, which is the shape difference between them.
        target.strokeStyle = palette('var(--tile-glyph)');
        target.lineWidth = inset;
        target.beginPath();
        for (let bar = 1; bar <= 3; bar += 1) {
          const barX = left + (tile * bar) / 4;
          target.moveTo(barX, top + inset);
          target.lineTo(barX, top + tile - inset);
        }
        target.stroke();
      }
      if (look.glyph !== null) {
        target.fillStyle = palette('var(--tile-glyph)');
        target.fillText(look.glyph, left + tile / 2, top + tile / 2);
      }
    }
  }
}

/**
 * The image to blit this frame, drawing it again only when `layerKey` has changed. Returns null while the canvas
 * has no room for a tile, so the caller simply draws nothing rather than guessing at a size.
 */
export function ensureLayer(
  cache: LayerCache,
  world: WorldModel,
  openGates: ReadonlySet<string>,
  fit: Fit,
  palette: Palette,
  make: MakeSurface,
): CanvasImageSource | null {
  if (fit.tile <= 0 || fit.canvasW <= 0 || fit.canvasH <= 0) {
    return null;
  }
  const key = layerKey(world, openGates, fit);
  if (cache.key === key && cache.image !== null) {
    return cache.image;
  }
  const surface = make(fit.canvasW, fit.canvasH);
  drawTiles(surface.target, world, openGates, fit, palette);
  cache.key = key;
  cache.image = surface.image;
  return surface.image;
}
