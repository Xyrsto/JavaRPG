import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Client, PlayerView, Result } from '../src/api/client';
import type { ClientError } from '../src/api/errors';
import { browserStore, openSession, SESSION_KEY, type SessionStore } from '../src/storage/session';

const ID = '3f2b8c1e-5a47-4d0e-9c1a-7b6d2e8f4a10';

function player(playerId: string): PlayerView {
  return {
    playerId,
    completedQuestIds: [],
    hero: { x: 0, y: 0 },
    level: 1,
    levelFloorXp: 0,
    nextLevelXp: 100,
    openGates: [],
    worldEffects: [],
    xp: 0,
  };
}

const UNREACHABLE: ClientError = { kind: 'unreachable', message: 'The game server cannot be reached.' };
const NOT_FOUND: ClientError = { kind: 'api', code: 'PLAYER_NOT_FOUND', status: 404, message: 'No such player.' };

interface Calls {
  created: number;
  looked: string[];
}

/** A client that answers from fixed results and counts what it was asked, so the tests can check the calls. */
function fakeClient(
  create: Result<PlayerView>,
  lookup: Result<PlayerView>,
  calls: Calls,
): Pick<Client, 'getPlayer' | 'createPlayer'> {
  return {
    async getPlayer(playerId) {
      calls.looked.push(playerId);
      return lookup;
    },
    async createPlayer() {
      calls.created += 1;
      return create;
    },
  };
}

/** An in-memory store whose contents the test can inspect. */
function memoryStore(initial: string | null) {
  const box = { value: initial };
  const store: SessionStore = {
    read: () => box.value,
    write: (id) => {
      box.value = id;
      return true;
    },
  };
  return { store, box };
}

const brokenStore: SessionStore = {
  read() {
    throw new Error('storage denied');
  },
  write() {
    throw new Error('storage denied');
  },
};

function fresh(): Calls {
  return { created: 0, looked: [] };
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('the saved player id', () => {
  it('is kept under the agreed key', () => {
    expect(SESSION_KEY).toBe('javarpg.playerId');
  });

  it('is empty and not remembered where the browser has no local storage', () => {
    const store = browserStore();
    expect(store.read()).toBeNull();
    expect(store.write(ID)).toBe(false);
  });

  it('reads and writes through local storage when the browser allows it', () => {
    const map = new Map<string, string>();
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => map.get(key) ?? null,
      setItem: (key: string, value: string) => {
        map.set(key, value);
      },
    });
    const store = browserStore();
    expect(store.write(ID)).toBe(true);
    expect(map.get(SESSION_KEY)).toBe(ID);
    expect(store.read()).toBe(ID);
  });

  it('treats refused local storage as empty and not remembered, without throwing', () => {
    vi.stubGlobal('localStorage', {
      getItem: () => {
        throw new Error('SecurityError');
      },
      setItem: () => {
        throw new Error('QuotaExceededError');
      },
    });
    const store = browserStore();
    expect(store.read()).toBeNull();
    expect(store.write(ID)).toBe(false);
  });
});

describe('opening the session', () => {
  it('creates exactly one player when nothing is stored, keeps its id and reports created', async () => {
    const calls = fresh();
    const { store, box } = memoryStore(null);
    const outcome = await openSession(fakeClient({ ok: true, value: player(ID) }, { ok: true, value: player(ID) }, calls), store);
    expect(outcome).toEqual({ kind: 'created', player: player(ID), remembered: true });
    expect(calls.created).toBe(1);
    expect(calls.looked).toEqual([]);
    expect(box.value).toBe(ID);
  });

  it('resumes a stored player and never creates a new one', async () => {
    const calls = fresh();
    const { store } = memoryStore(ID);
    const outcome = await openSession(fakeClient({ ok: true, value: player('other') }, { ok: true, value: player(ID) }, calls), store);
    expect(outcome).toEqual({ kind: 'resumed', player: player(ID), remembered: true });
    expect(calls.created).toBe(0);
    expect(calls.looked).toEqual([ID]);
  });

  it('reports an unknown save and leaves the stored id alone when the server does not know it', async () => {
    const calls = fresh();
    const { store, box } = memoryStore(ID);
    const outcome = await openSession(fakeClient({ ok: true, value: player('other') }, { ok: false, error: NOT_FOUND }, calls), store);
    expect(outcome).toEqual({ kind: 'unknown-save' });
    expect(box.value).toBe(ID);
    expect(calls.created).toBe(0);
  });

  it('reports a failure with its error when the server cannot be reached while resuming, and keeps the stored id', async () => {
    const calls = fresh();
    const { store, box } = memoryStore(ID);
    const outcome = await openSession(fakeClient({ ok: true, value: player('other') }, { ok: false, error: UNREACHABLE }, calls), store);
    expect(outcome).toEqual({ kind: 'failed', error: UNREACHABLE });
    expect(box.value).toBe(ID);
    expect(calls.created).toBe(0);
  });

  it('reports a failure without retrying when creating a player fails', async () => {
    const calls = fresh();
    const { store, box } = memoryStore(null);
    const outcome = await openSession(fakeClient({ ok: false, error: UNREACHABLE }, { ok: false, error: UNREACHABLE }, calls), store);
    expect(outcome).toEqual({ kind: 'failed', error: UNREACHABLE });
    expect(calls.created).toBe(1);
    expect(box.value).toBeNull();
  });

  it('still creates a player and reports not remembered when storage throws on read and write', async () => {
    const calls = fresh();
    const outcome = await openSession(fakeClient({ ok: true, value: player(ID) }, { ok: true, value: player(ID) }, calls), brokenStore);
    expect(outcome).toEqual({ kind: 'created', player: player(ID), remembered: false });
    expect(calls.created).toBe(1);
  });

  it('still reports the new player as created, not remembered, when storage throws on write', async () => {
    const calls = fresh();
    const writeOnlyBroken: SessionStore = {
      read: () => null,
      write() {
        throw new Error('quota');
      },
    };
    const outcome = await openSession(fakeClient({ ok: true, value: player(ID) }, { ok: true, value: player(ID) }, calls), writeOnlyBroken);
    expect(outcome).toEqual({ kind: 'created', player: player(ID), remembered: false });
  });
});
