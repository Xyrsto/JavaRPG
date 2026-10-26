import './style.css';
import { createClient, type Direction } from './api/client';
import { apiBase } from './config';
import { boot, type Game } from './boot';
import { BUMP_MS, SETTLE_MS } from './constants';
import { announce, describeBlocked, describeCorrection, describeSettled, freshAnnouncer } from './a11y/worldText';
import { directionFor, MOVEMENT_CODES } from './input/keymap';
import { cleared, emptyWalker, pressed, released, stepsDue, type WalkerState } from './input/walker';
import { visualPosition } from './render/animation';
import { fit, type Fit } from './render/geometry';
import { draw } from './render/renderer';
import { ensureLayer, makeCache } from './render/staticLayer';
import { browserSurface, type Palette } from './render/surface';
import { answered, failed, resynced, start, step, type Effect, type MovementState } from './state/movement';
import type { Point } from './state/world';
import { describeFailure, describeHealth } from './status';
import { browserStore } from './storage/session';

const canvas = document.getElementById('world');
const worldText = document.getElementById('world-text');
const statusLine = document.getElementById('server-status');
const client = createClient({ base: apiBase });

let shownStatus = '';
/** What the status line says when nothing is wrong, so a failure message is cleared once walking works again. */
let restingStatus = 'Walk with the arrow keys or with W, A, S and D.';

/** Only touches the page when the words change, so a live region does not repeat itself. */
function say(text: string): void {
  if (statusLine && text !== shownStatus) {
    statusLine.textContent = text;
    shownStatus = text;
  }
}

/**
 * Resolves the colour tokens that `tiles.ts` returns against the stylesheet, once, because reading a computed style
 * every frame would mean a layout read every frame. The cache is dropped when the colour scheme changes.
 */
function cssPalette(): Palette {
  const styles = window.getComputedStyle(document.documentElement);
  const known = new Map<string, string>();
  return (token) => {
    const cached = known.get(token);
    if (cached !== undefined) {
      return cached;
    }
    const name = token.startsWith('var(') ? token.slice(4, -1).trim() : token;
    const value = styles.getPropertyValue(name).trim() || '#888888';
    known.set(token, value);
    return value;
  };
}

if (!(canvas instanceof HTMLCanvasElement)) {
  say('This page needs a browser that can draw on a canvas.');
} else {
  void run(canvas);
}

async function run(surface: HTMLCanvasElement): Promise<void> {
  const context = surface.getContext('2d');
  if (!context) {
    say('This browser cannot draw on a canvas.');
    return;
  }

  const game = await boot({
    client,
    store: browserStore(),
    say,
    wait: (ms) => new Promise((resolve) => window.setTimeout(resolve, ms)),
  });
  if (!game) {
    return;
  }
  surface.style.setProperty('--world-aspect', `${String(game.world.width)} / ${String(game.world.height)}`);
  announceSandbox();
  play(surface, context, game);
}

/** The game itself, once the canvas and the world are both known to be there. */
function play(surface: HTMLCanvasElement, context: CanvasRenderingContext2D, game: Game): void {
  const world = game.world;
  const playerId = game.player.playerId;
  const layers = makeCache();
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  const darkScheme = window.matchMedia('(prefers-color-scheme: dark)');
  let palette = cssPalette();
  darkScheme.addEventListener('change', () => {
    // The map image holds resolved colours, so it must be drawn again in the new scheme.
    palette = cssPalette();
    layers.key = null;
  });

  let openGates = new Set(game.player.openGates);
  let move: MovementState = start(game.player.hero);
  let walker: WalkerState = emptyWalker;
  let announcer = freshAnnouncer;
  let facing: Direction = 'DOWN';
  let glide = { from: move.shown, to: move.shown, startedAt: 0 };
  let settledAt: number | null = 0;
  let box = { width: surface.clientWidth, height: surface.clientHeight };

  new ResizeObserver((entries) => {
    const entry = entries[0];
    if (entry) {
      box = { width: entry.contentRect.width, height: entry.contentRect.height };
    }
  }).observe(surface);

  surface.addEventListener('keydown', (event) => {
    if (MOVEMENT_CODES.includes(event.code)) {
      // The arrows would otherwise scroll the page out from under the player.
      event.preventDefault();
    }
    // The browser's own key repeat is never used: the pace is the game's, not the player's OS settings.
    if (event.repeat) {
      return;
    }
    const direction = directionFor(event.code);
    if (direction) {
      walker = pressed(walker, direction, performance.now());
    }
  });
  surface.addEventListener('keyup', (event) => {
    const direction = directionFor(event.code);
    if (direction) {
      walker = released(walker, direction);
    }
  });
  // A key held while the page loses focus never reports its release, so the keys are forgotten instead.
  const forget = (): void => {
    walker = cleared(walker);
  };
  surface.addEventListener('blur', forget);
  window.addEventListener('pagehide', forget);
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') {
      forget();
    }
  });

  /** Announces a sentence to players who are not looking at the map. */
  function tell(sentence: string): void {
    const next = announce(announcer, sentence);
    announcer = next.state;
    if (next.say !== null && worldText) {
      worldText.textContent = next.say;
    }
  }

  /** Applies what the reducer asks for: send the next walk, or ask the server where the hero really is. */
  function carryOut(effect: Effect): void {
    if (effect.kind === 'send') {
      void sendWalk(effect.directions);
    } else if (effect.kind === 'resync') {
      void reread();
    }
  }

  /** Remembers that the hero was corrected rather than walked, so the drawing snaps instead of gliding. */
  function snapTo(position: Point, now: number): void {
    glide = { from: position, to: position, startedAt: now };
  }

  async function sendWalk(directions: readonly Direction[]): Promise<void> {
    const result = await client.move(playerId, [...directions]);
    const now = performance.now();
    const before = move.shown;
    const outcome = result.ok ? answered(move, result.value) : failed(move);
    move = outcome.state;
    // A walk that works again clears whatever the last failure left on the status line.
    say(result.ok ? restingStatus : describeFailure(result.error));
    if (move.shown.x !== before.x || move.shown.y !== before.y) {
      snapTo(move.shown, now);
      tell(describeCorrection(move.shown));
      settledAt = now;
    }
    carryOut(outcome.effect);
  }

  async function reread(): Promise<void> {
    const result = await client.getPlayer(playerId);
    if (!result.ok) {
      say(describeFailure(result.error));
      return;
    }
    const now = performance.now();
    openGates = new Set(result.value.openGates);
    layers.key = null;
    const before = move.shown;
    move = resynced(move, result.value.hero).state;
    snapTo(move.shown, now);
    if (move.shown.x !== before.x || move.shown.y !== before.y) {
      tell(describeCorrection(move.shown));
    }
    settledAt = now;
  }

  function update(now: number): void {
    const due = stepsDue(walker, now);
    walker = due.state;
    for (let taken = 0; taken < due.steps && due.direction; taken += 1) {
      const before = move.shown;
      const outcome = step(move, world, openGates, due.direction, now);
      move = outcome.state;
      facing = due.direction;
      if (outcome.blocked) {
        tell(describeBlocked(due.direction));
        settledAt = now;
        break;
      }
      if (outcome.moved) {
        glide = { from: before, to: move.shown, startedAt: now };
        settledAt = null;
      }
      carryOut(outcome.effect);
    }
    if (settledAt === null && now - glide.startedAt >= SETTLE_MS && walker.held.length === 0) {
      settledAt = now;
      tell(describeSettled(move.shown));
    }
  }

  function frame(now: number): void {
    update(now);
    const box2 = fit(world.width, world.height, box.width, box.height, window.devicePixelRatio || 1);
    resize(surface, box2);
    const layer = ensureLayer(layers, world, openGates, box2, palette, browserSurface);
    const hero = visualPosition(glide.from, glide.to, glide.startedAt, now, reducedMotion.matches);
    const bump = move.bump !== null && now - move.bump.at < BUMP_MS ? { x: move.bump.x, y: move.bump.y } : null;
    draw(context, { world, hero, facing, bumpTile: bump }, box2, palette, layer);
    requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);
}

/** Sizing the canvas clears it, so it is only ever resized when the size has actually changed. */
function resize(surface: HTMLCanvasElement, box: Fit): void {
  if (surface.width !== box.canvasW || surface.height !== box.canvasH) {
    surface.width = box.canvasW;
    surface.height = box.canvasH;
  }
}

/** The health call no longer gates the drawing; it is still made, because it carries the unsafe-sandbox warning. */
function announceSandbox(): void {
  void client.health().then((result) => {
    if (result.ok && !result.value.sandboxSafe) {
      restingStatus = describeHealth(result.value);
    }
    say(restingStatus);
  });
}

export type { Game };
