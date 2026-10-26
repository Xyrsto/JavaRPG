/**
 * The drawing operations the world needs, and nothing more. It is a subset of the browser's 2D context, so the
 * real context satisfies it, and a recording stand-in in the tests satisfies it too: that is how the drawing is
 * tested without a browser.
 */
export type DrawTarget = Pick<
  CanvasRenderingContext2D,
  | 'clearRect'
  | 'drawImage'
  | 'fillRect'
  | 'strokeRect'
  | 'beginPath'
  | 'arc'
  | 'fill'
  | 'stroke'
  | 'moveTo'
  | 'lineTo'
  | 'fillText'
  | 'fillStyle'
  | 'strokeStyle'
  | 'lineWidth'
  | 'font'
  | 'textAlign'
  | 'textBaseline'
>;

/** Turns a colour token from `tiles.ts` (`var(--tile-wall)`) into a colour the canvas understands. */
export type Palette = (token: string) => string;

/** An offscreen surface: something to draw on, and the image that can then be blitted onto the canvas. */
export interface Surface {
  readonly target: DrawTarget;
  readonly image: CanvasImageSource;
}

/** How a surface is made. The tests pass their own, which is why nothing else here touches the browser. */
export type MakeSurface = (width: number, height: number) => Surface;

/**
 * The one browser-touching function in the drawing code. `OffscreenCanvas` is used where the browser has it and a
 * detached canvas element otherwise, which covers Safari 16; both go through the same drawing path afterwards.
 */
export function browserSurface(width: number, height: number): Surface {
  if (typeof OffscreenCanvas === 'function') {
    const canvas = new OffscreenCanvas(width, height);
    const target = canvas.getContext('2d');
    if (target) {
      return { target, image: canvas };
    }
  }
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const target = canvas.getContext('2d');
  if (!target) {
    throw new Error('This browser cannot draw on a canvas.');
  }
  return { target, image: canvas };
}
