import { describe, expect, it } from 'vitest';
import { draw, type Scene } from '../src/render/renderer';
import { ensureLayer, layerKey, makeCache } from '../src/render/staticLayer';
import { fit } from '../src/render/geometry';
import type { DrawTarget, Palette, Surface } from '../src/render/surface';
import type { WorldModel, WorldNpc } from '../src/state/world';

/** Records which drawing calls a frame makes, so the cost of a frame can be compared without a browser. */
class Recorder {
  readonly calls: string[] = [];
  fillStyle = '';
  strokeStyle = '';
  lineWidth = 0;
  font = '';
  textAlign = 'start';
  textBaseline = 'alphabetic';

  private note(name: string): void {
    this.calls.push(name);
  }

  clearRect(): void {
    this.note('clearRect');
  }
  drawImage(): void {
    this.note('drawImage');
  }
  fillRect(): void {
    this.note('fillRect');
  }
  strokeRect(): void {
    this.note('strokeRect');
  }
  beginPath(): void {
    this.note('beginPath');
  }
  arc(): void {
    this.note('arc');
  }
  fill(): void {
    this.note('fill');
  }
  stroke(): void {
    this.note('stroke');
  }
  moveTo(): void {
    this.note('moveTo');
  }
  lineTo(): void {
    this.note('lineTo');
  }
  fillText(): void {
    this.note('fillText');
  }
}

function asTarget(recorder: Recorder): DrawTarget {
  return recorder as unknown as DrawTarget;
}

const palette: Palette = (token) => token;

function room(id: string, width: number, height: number, npcs: readonly WorldNpc[]): WorldModel {
  const rows: string[] = [];
  for (let y = 0; y < height; y += 1) {
    const edge = y === 0 || y === height - 1;
    rows.push(edge ? '#'.repeat(width) : `#${'.'.repeat(width - 2)}#`);
  }
  return {
    id,
    width,
    height,
    rows,
    legend: { '#': 'wall', '.': 'floor' },
    npcs,
    gates: [],
    heroStart: { x: 1, y: 1 },
  };
}

const twoNpcs: readonly WorldNpc[] = [
  { id: 'elder', name: 'Elder Mira', questId: 'V01', x: 3, y: 2 },
  { id: 'smith', name: 'Smith Orin', questId: 'V02', x: 5, y: 2 },
];

function sceneFor(world: WorldModel, bumpTile: { x: number; y: number } | null = null): Scene {
  return { world, hero: { x: 2, y: 2 }, facing: 'RIGHT', bumpTile };
}

describe('one frame of the world', () => {
  it('costs the same whatever the size of the map, because the map itself is a blitted image', () => {
    const small = new Recorder();
    const huge = new Recorder();
    const box = fit(12, 9, 600, 450, 1);
    const hugeBox = fit(200, 200, 600, 450, 1);
    const image = {} as CanvasImageSource;

    draw(asTarget(small), sceneFor(room('village', 12, 9, twoNpcs)), box, palette, image);
    draw(asTarget(huge), sceneFor(room('huge', 200, 200, twoNpcs)), hugeBox, palette, image);

    expect(huge.calls).toEqual(small.calls);
    // A frame is: clear, blit, the two characters, the hero and its pointer. Nothing per tile.
    expect(small.calls.filter((call) => call === 'fillRect')).toHaveLength(0);
    expect(small.calls.filter((call) => call === 'drawImage')).toHaveLength(1);
  });

  it('grows only with the number of characters, never with the number of tiles', () => {
    const none = new Recorder();
    const two = new Recorder();
    const box = fit(12, 9, 600, 450, 1);
    const image = {} as CanvasImageSource;

    draw(asTarget(none), sceneFor(room('village', 12, 9, [])), box, palette, image);
    draw(asTarget(two), sceneFor(room('village', 12, 9, twoNpcs)), box, palette, image);

    expect(two.calls.length).toBeGreaterThan(none.calls.length);
    expect(two.calls.filter((call) => call === 'arc')).toHaveLength(3);
    expect(none.calls.filter((call) => call === 'arc')).toHaveLength(1);
  });

  it('marks a refused tile, and only while the marker is fresh', () => {
    const without = new Recorder();
    const withBump = new Recorder();
    const box = fit(12, 9, 600, 450, 1);
    const image = {} as CanvasImageSource;

    draw(asTarget(without), sceneFor(room('village', 12, 9, twoNpcs), null), box, palette, image);
    draw(asTarget(withBump), sceneFor(room('village', 12, 9, twoNpcs), { x: 4, y: 1 }), box, palette, image);

    expect(without.calls).not.toContain('strokeRect');
    expect(withBump.calls.filter((call) => call === 'strokeRect')).toHaveLength(1);
  });

  it('draws nothing but the clear when there is no room for a tile', () => {
    const recorder = new Recorder();
    draw(asTarget(recorder), sceneFor(room('village', 12, 9, twoNpcs)), fit(12, 9, 0, 0, 1), palette, null);
    expect(recorder.calls).toEqual(['clearRect']);
  });
});

describe('the map image', () => {
  function countingMake(): { make: (w: number, h: number) => Surface; made: () => number } {
    let made = 0;
    return {
      make: () => {
        made += 1;
        return { target: asTarget(new Recorder()), image: {} as CanvasImageSource };
      },
      made: () => made,
    };
  }

  const world = room('village', 12, 9, twoNpcs);
  const box = fit(12, 9, 600, 450, 1);

  it('is drawn once and reused while nothing about it changes', () => {
    const cache = makeCache();
    const counting = countingMake();
    const gates = new Set<string>();

    for (let frame = 0; frame < 60; frame += 1) {
      ensureLayer(cache, world, gates, box, palette, counting.make);
    }

    expect(counting.made()).toBe(1);
  });

  it('is drawn again when a gate opens, when the world changes, or when the tile size changes', () => {
    const counting = countingMake();
    const cache = makeCache();
    ensureLayer(cache, world, new Set<string>(), box, palette, counting.make);
    ensureLayer(cache, world, new Set(['A']), box, palette, counting.make);
    ensureLayer(cache, room('other', 12, 9, twoNpcs), new Set(['A']), box, palette, counting.make);
    ensureLayer(cache, room('other', 12, 9, twoNpcs), new Set(['A']), fit(12, 9, 300, 225, 1), palette, counting.make);

    expect(counting.made()).toBe(4);
  });

  it('does not depend on where the hero is, so walking never redraws it', () => {
    const gates = new Set(['A']);
    expect(layerKey(world, gates, box)).toBe(layerKey(world, gates, box));
    expect(layerKey(world, gates, box)).not.toBe(layerKey(world, new Set<string>(), box));
    // The same open gates in another order are the same key: the set, not its iteration order, decides.
    expect(layerKey(world, new Set(['A', 'B']), box)).toBe(layerKey(world, new Set(['B', 'A']), box));
  });

  it('draws nothing when the canvas has no room', () => {
    const counting = countingMake();
    const cache = makeCache();
    expect(ensureLayer(cache, world, new Set<string>(), fit(12, 9, 0, 0, 1), palette, counting.make)).toBeNull();
    expect(counting.made()).toBe(0);
  });
});
