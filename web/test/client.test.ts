import { describe, expect, it } from 'vitest';
import { createClient } from '../src/api/client';

interface Call {
  url: string;
  init: RequestInit;
}

function fake(status: number, body: string, calls: Call[] = []): typeof fetch {
  return (async (input: RequestInfo | URL, init?: RequestInit) => {
    calls.push({ url: String(input), init: init ?? {} });
    return new Response(body, { status, headers: { 'Content-Type': 'application/json' } });
  }) as typeof fetch;
}

const PLAYER = '3f2b8c1e-5a47-4d0e-9c1a-7b6d2e8f4a10';
const HEALTH = { status: 'UP', sandbox: 'docker', sandboxSafe: true, questsLoaded: 2 };

describe('the call layer', () => {
  it('reads a successful answer into a typed result', async () => {
    const client = createClient({ fetch: fake(200, JSON.stringify(HEALTH)) });
    const result = await client.health();
    expect(result).toEqual({ ok: true, value: HEALTH });
  });

  it('uses the same address by default and the configured base when given', async () => {
    const calls: Call[] = [];
    await createClient({ fetch: fake(200, JSON.stringify(HEALTH), calls) }).health();
    await createClient({ base: 'http://127.0.0.1:9999', fetch: fake(200, JSON.stringify(HEALTH), calls) }).health();
    expect(calls.map((c) => c.url)).toEqual(['/health', 'http://127.0.0.1:9999/health']);
  });

  it('places the ids in the path only, encoded, with no query string', async () => {
    const calls: Call[] = [];
    const client = createClient({ fetch: fake(200, '{}', calls) });
    await client.getPlayer(PLAYER);
    await client.getQuest(PLAYER, 'V01');
    await client.listQuests(PLAYER);
    await client.interact(PLAYER);
    await client.move(PLAYER, ['UP', 'LEFT']);
    await client.run(PLAYER, 'V01', 'class Main {}');
    await client.submit(PLAYER, 'V01', 'class Main {}');
    await client.nextHint(PLAYER, 'V01');
    await client.createPlayer();
    await client.world();
    expect(calls.map((c) => c.url)).toEqual([
      `/api/v1/players/${PLAYER}`,
      `/api/v1/players/${PLAYER}/quests/V01`,
      `/api/v1/players/${PLAYER}/quests`,
      `/api/v1/players/${PLAYER}/interact`,
      `/api/v1/players/${PLAYER}/move`,
      `/api/v1/players/${PLAYER}/quests/V01/run`,
      `/api/v1/players/${PLAYER}/quests/V01/submit`,
      `/api/v1/players/${PLAYER}/quests/V01/hints/next`,
      '/api/v1/players',
      '/api/v1/world',
    ]);
    for (const call of calls) {
      expect(call.url).not.toContain('?');
      expect(call.init.credentials).toBe('omit');
      expect(call.init.cache).toBe('no-store');
    }
    await client.getPlayer('../../etc/passwd');
    expect(calls[calls.length - 1]?.url).toBe('/api/v1/players/..%2F..%2Fetc%2Fpasswd');
  });

  it('sends JSON bodies for the operations that take one', async () => {
    const calls: Call[] = [];
    const client = createClient({ fetch: fake(200, '{}', calls) });
    await client.move(PLAYER, ['UP', 'LEFT']);
    await client.run(PLAYER, 'V01', 'class Main {}');
    expect(calls[0]?.init.method).toBe('POST');
    expect(new Headers(calls[0]?.init.headers).get('Content-Type')).toBe('application/json');
    expect(JSON.parse(String(calls[0]?.init.body))).toEqual({ directions: ['UP', 'LEFT'] });
    expect(JSON.parse(String(calls[1]?.init.body))).toEqual({ source: 'class Main {}' });
  });

  it('turns an error envelope into a named failure', async () => {
    const body = JSON.stringify({ error: { code: 'QUEST_LOCKED', message: 'Finish the first quest first.' } });
    const result = await createClient({ fetch: fake(403, body) }).getQuest(PLAYER, 'V02');
    expect(result).toEqual({
      ok: false,
      error: { kind: 'api', code: 'QUEST_LOCKED', status: 403, message: 'Finish the first quest first.' },
    });
  });

  it('turns a proxy page into an unexpected failure', async () => {
    const result = await createClient({ fetch: fake(502, '<html>Bad gateway</html>') }).health();
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.error.kind).toBe('unexpected');
  });

  it('turns an unreachable server into an unreachable failure', async () => {
    const failing = (async () => {
      throw new TypeError('Failed to fetch');
    }) as typeof fetch;
    const result = await createClient({ fetch: failing }).health();
    expect(result).toMatchObject({ ok: false, error: { kind: 'unreachable' } });
  });

  it('aborts a request that takes too long and reports a timeout', async () => {
    const hanging = ((_input: RequestInfo | URL, init?: RequestInit) =>
      new Promise((_resolve, reject) => {
        init?.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
      })) as typeof fetch;
    const result = await createClient({ fetch: hanging, timeoutMs: 20 }).health();
    expect(result).toMatchObject({ ok: false, error: { kind: 'timeout' } });
  });

  it('a successful answer that is not JSON is an unexpected failure', async () => {
    const result = await createClient({ fetch: fake(200, 'not json') }).health();
    expect(result).toMatchObject({ ok: false, error: { kind: 'unexpected' } });
  });
});
