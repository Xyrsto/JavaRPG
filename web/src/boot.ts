import type { Client, PlayerView } from './api/client';
import { buildWorld, type WorldModel } from './state/world';
import { openSession, type SessionOutcome, type SessionStore } from './storage/session';
import { describeFailure } from './status';

/** Everything the page needs before it can draw anything: the map, and whose hero it is. */
export interface Game {
  readonly world: WorldModel;
  readonly player: PlayerView;
  /** False when the browser refused to store the save code, so this visit will not be remembered. */
  readonly remembered: boolean;
}

export interface BootDeps {
  readonly client: Pick<Client, 'world' | 'getPlayer' | 'createPlayer'>;
  readonly store: SessionStore;
  /** Writes a line the player can read while they wait. */
  say(text: string): void;
  /** Waits before the next attempt. Passed in so the start-up can be driven without a real clock. */
  wait(ms: number): Promise<void>;
}

const UNKNOWN_SAVE =
  'This browser holds a saved game the server does not know. The save code has been kept, in case the server comes back with it.';
const NOT_REMEMBERED =
  'This browser will not remember the game: progress lasts only while the page is open.';

/**
 * Fetches the world and finds or makes the player, both at once, and draws nothing until it has both. The world and
 * a resumed player are retried with a growing pause, exactly as the status line already did for the health call.
 * Making a NEW player is attempted once and never retried: creation is rate-limited per address and every success
 * makes another player, so a retry loop would both burn the limit and litter the server.
 * Returns null when the page cannot continue by itself and the player must reload.
 */
export async function boot(deps: BootDeps): Promise<Game | null> {
  const resuming = deps.store.read() !== null;
  let world: WorldModel | null = null;
  let session: SessionOutcome | null = null;

  for (let attempt = 0; ; attempt += 1) {
    let worldMessage: string | null = null;
    // A failed attempt is retried only when it was a resume: a creation must not happen twice.
    const needSession: boolean = session === null || (session.kind === 'failed' && resuming);
    // Both calls are started before either is awaited, so they travel together.
    const worldJob = world === null ? deps.client.world() : null;
    const sessionJob: Promise<SessionOutcome> | null = needSession ? openSession(deps.client, deps.store) : null;
    const worldResult = worldJob === null ? null : await worldJob;
    const sessionResult: SessionOutcome | null = sessionJob === null ? null : await sessionJob;

    if (worldResult !== null) {
      if (worldResult.ok) {
        world = buildWorld(worldResult.value);
      } else {
        worldMessage = describeFailure(worldResult.error);
      }
    }
    if (sessionResult !== null) {
      session = sessionResult;
    }

    let sessionMessage: string | null = null;
    if (session !== null) {
      if (session.kind === 'unknown-save') {
        deps.say(UNKNOWN_SAVE);
        return null;
      }
      if (session.kind === 'failed') {
        sessionMessage = describeFailure(session.error);
        if (!resuming) {
          deps.say(`${sessionMessage} Please reload the page to start a game.`);
          return null;
        }
      } else if (world !== null) {
        if (!session.remembered) {
          deps.say(NOT_REMEMBERED);
        }
        return { world, player: session.player, remembered: session.remembered };
      }
    }

    deps.say(worldMessage ?? sessionMessage ?? 'Loading the village.');
    await deps.wait(Math.min(10_000, 2_000 * (attempt + 1)));
  }
}
