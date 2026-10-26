import type { Client, PlayerView } from '../api/client';
import type { ClientError } from '../api/errors';

/** The key the player's save code is kept under in this browser. Changing it orphans every saved player. */
export const SESSION_KEY = 'javarpg.playerId';

/** Where the save code lives. Both calls may fail quietly: the browser can refuse storage at any time. */
export interface SessionStore {
  read(): string | null;
  /** False when the browser refused to store the id, so the player will not be remembered next visit. */
  write(id: string): boolean;
}

interface KeyValueStore {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
}

function isKeyValueStore(value: unknown): value is KeyValueStore {
  return typeof value === 'object' && value !== null && 'getItem' in value && 'setItem' in value;
}

/**
 * The save code kept in this browser's local storage. Every access is guarded: private browsing or blocked site data
 * must leave the game playable for the visit, so a refusal reads as "nothing stored" and a failed write as "not remembered".
 */
export function browserStore(): SessionStore {
  const local = (): KeyValueStore | null => {
    const found: unknown = Reflect.get(globalThis, 'localStorage');
    return isKeyValueStore(found) ? found : null;
  };
  return {
    read() {
      try {
        return local()?.getItem(SESSION_KEY) ?? null;
      } catch {
        return null;
      }
    },
    write(id) {
      try {
        const target = local();
        if (target === null) {
          return false;
        }
        target.setItem(SESSION_KEY, id);
        return true;
      } catch {
        return false;
      }
    },
  };
}

/**
 * How the page opened the player's session. Resumed and created both carry the player; the remembered flag says
 * whether the id was (or could be) kept in the browser.
 */
export type SessionOutcome =
  | { readonly kind: 'resumed'; readonly player: PlayerView; readonly remembered: boolean }
  | { readonly kind: 'created'; readonly player: PlayerView; readonly remembered: boolean }
  | { readonly kind: 'unknown-save' }
  | { readonly kind: 'failed'; readonly error: ClientError };

/**
 * Finds or makes the player. A stored id that the server does not know is reported as 'unknown-save' and the stored
 * value is deliberately left in place: it is the player's only link to their progress, so a server problem must never
 * erase it. Creating a player is attempted once, never retried, because creation is rate-limited per address and each
 * success makes a new player.
 */
export async function openSession(
  client: Pick<Client, 'getPlayer' | 'createPlayer'>,
  store: SessionStore,
): Promise<SessionOutcome> {
  const stored = safeRead(store);
  if (stored) {
    const lookup = await client.getPlayer(stored);
    if (lookup.ok) {
      return { kind: 'resumed', player: lookup.value, remembered: true };
    }
    if (lookup.error.kind === 'api' && lookup.error.code === 'PLAYER_NOT_FOUND') {
      return { kind: 'unknown-save' };
    }
    return { kind: 'failed', error: lookup.error };
  }
  const created = await client.createPlayer();
  if (!created.ok) {
    return { kind: 'failed', error: created.error };
  }
  const remembered = safeWrite(store, created.value.playerId);
  return { kind: 'created', player: created.value, remembered };
}

/** A store that throws on read counts as holding nothing; the caller then makes a player for this visit. */
function safeRead(store: SessionStore): string | null {
  try {
    return store.read();
  } catch {
    return null;
  }
}

/** A store that throws on write counts as not remembering; the player still plays this visit. */
function safeWrite(store: SessionStore, id: string): boolean {
  try {
    return store.write(id);
  } catch {
    return false;
  }
}
