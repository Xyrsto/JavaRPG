import { fromResponse, fromThrown, unexpectedAnswer, type ClientError } from './errors';
import type { components } from './schema';

type Schemas = components['schemas'];

/** The server writes null for optional values although the description only marks them optional. */
type Wire<T> = T extends (infer U)[]
  ? Wire<U>[]
  : T extends object
    ? { [K in keyof T]: undefined extends T[K] ? Wire<Exclude<T[K], undefined>> | null | undefined : Wire<T[K]> }
    : T;

export type HealthView = Wire<Schemas['HealthView']>;
export type WorldView = Wire<Schemas['WorldView']>;
export type PlayerView = Wire<Schemas['PlayerView']>;
export type MoveResponse = Wire<Schemas['MoveResponse']>;
export type InteractResponse = Wire<Schemas['InteractResponse']>;
export type QuestListResponse = Wire<Schemas['QuestListResponse']>;
export type QuestDetailView = Wire<Schemas['QuestDetailView']>;
export type RunResponse = Wire<Schemas['RunResponse']>;
export type SubmitResponse = Wire<Schemas['SubmitResponse']>;
export type HintResponse = Wire<Schemas['HintResponse']>;
export type Direction = 'UP' | 'DOWN' | 'LEFT' | 'RIGHT';

export type Result<T> = { ok: true; value: T } | { ok: false; error: ClientError };

export interface ClientOptions {
  /** Where the game server is. Empty (the default) means the same address as the page. */
  base?: string;
  fetch?: typeof fetch;
  /** Limit for quick calls, in milliseconds. */
  timeoutMs?: number;
  /** Limit for run and submit, which wait for the sandbox. */
  longTimeoutMs?: number;
}

export interface Client {
  health(): Promise<Result<HealthView>>;
  world(): Promise<Result<WorldView>>;
  createPlayer(): Promise<Result<PlayerView>>;
  getPlayer(playerId: string): Promise<Result<PlayerView>>;
  move(playerId: string, directions: Direction[]): Promise<Result<MoveResponse>>;
  interact(playerId: string): Promise<Result<InteractResponse>>;
  listQuests(playerId: string): Promise<Result<QuestListResponse>>;
  getQuest(playerId: string, questId: string): Promise<Result<QuestDetailView>>;
  run(playerId: string, questId: string, source: string): Promise<Result<RunResponse>>;
  submit(playerId: string, questId: string, source: string): Promise<Result<SubmitResponse>>;
  nextHint(playerId: string, questId: string): Promise<Result<HintResponse>>;
}

const enc = encodeURIComponent;

/**
 * The one way the client talks to the game server. Ids go only into the path, never into a query string, a log or
 * the page address. Server problems come back as a failed Result; nothing is thrown for them.
 */
export function createClient(options: ClientOptions = {}): Client {
  const base = options.base ?? '';
  const doFetch = options.fetch ?? ((input, init) => globalThis.fetch(input, init));
  const quick = options.timeoutMs ?? 8000;
  const slow = options.longTimeoutMs ?? 15000;

  async function call<T>(method: 'GET' | 'POST', path: string, body?: unknown, timeoutMs = quick): Promise<Result<T>> {
    const controller = new AbortController();
    let timedOut = false;
    const timer = setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, timeoutMs);
    try {
      const headers: Record<string, string> = { Accept: 'application/json' };
      const init: RequestInit = { method, headers, credentials: 'omit', cache: 'no-store', signal: controller.signal };
      if (body !== undefined) {
        headers['Content-Type'] = 'application/json';
        init.body = JSON.stringify(body);
      }
      const response = await doFetch(base + path, init);
      const text = await response.text();
      if (!response.ok) {
        return { ok: false, error: fromResponse(response.status, text) };
      }
      try {
        return { ok: true, value: JSON.parse(text) as T };
      } catch {
        return { ok: false, error: unexpectedAnswer(response.status) };
      }
    } catch (thrown) {
      return { ok: false, error: fromThrown(thrown, timedOut) };
    } finally {
      clearTimeout(timer);
    }
  }

  const player = (playerId: string) => `/api/v1/players/${enc(playerId)}`;
  const quest = (playerId: string, questId: string) => `${player(playerId)}/quests/${enc(questId)}`;

  return {
    health: () => call('GET', '/health'),
    world: () => call('GET', '/api/v1/world'),
    createPlayer: () => call('POST', '/api/v1/players'),
    getPlayer: (playerId) => call('GET', player(playerId)),
    move: (playerId, directions) => call('POST', `${player(playerId)}/move`, { directions }),
    interact: (playerId) => call('POST', `${player(playerId)}/interact`),
    listQuests: (playerId) => call('GET', `${player(playerId)}/quests`),
    getQuest: (playerId, questId) => call('GET', quest(playerId, questId)),
    run: (playerId, questId, source) => call('POST', `${quest(playerId, questId)}/run`, { source }, slow),
    submit: (playerId, questId, source) => call('POST', `${quest(playerId, questId)}/submit`, { source }, slow),
    nextHint: (playerId, questId) => call('POST', `${quest(playerId, questId)}/hints/next`),
  };
}
