/** Where the map sits on the canvas, all in device pixels. The canvas backing store is sized to match the stylesheet box. */
export interface Fit {
  /** Side of one tile, in whole device pixels. */
  readonly tile: number;
  /** Left edge of the map inside the canvas, in device pixels. */
  readonly originX: number;
  /** Top edge of the map inside the canvas, in device pixels. */
  readonly originY: number;
  /** Backing-store width of the canvas, in device pixels. */
  readonly canvasW: number;
  /** Backing-store height of the canvas, in device pixels. */
  readonly canvasH: number;
}

/** A CSS or device length turned into a whole, non-negative pixel count. Anything not finite counts as 0. */
function wholeSide(value: number): number {
  return Number.isFinite(value) ? Math.max(0, Math.round(value)) : 0;
}

/**
 * Fits the whole map into the box the page gave the canvas. The tile is a whole number of device
 * pixels so that adjacent tiles meet on exact pixel edges: a fractional tile would blur every edge
 * and leave hairline seams between neighbours. The leftover space is split so the map stays centred.
 * Empty or nonsensical input yields tile 0 and zero origins, never NaN or Infinity.
 */
export function fit(mapW: number, mapH: number, boxW: number, boxH: number, pixelRatio: number): Fit {
  const canvasW = wholeSide(boxW * pixelRatio);
  const canvasH = wholeSide(boxH * pixelRatio);
  if (!(mapW > 0) || !(mapH > 0) || canvasW === 0 || canvasH === 0) {
    return { tile: 0, originX: 0, originY: 0, canvasW, canvasH };
  }
  const tile = Math.max(0, Math.floor(Math.min(canvasW / mapW, canvasH / mapH)));
  const originX = Math.floor((canvasW - tile * mapW) / 2);
  const originY = Math.floor((canvasH - tile * mapH) / 2);
  return { tile, originX, originY, canvasW, canvasH };
}
