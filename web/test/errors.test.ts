import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { parse } from 'yaml';
import { fromResponse, fromThrown, type ClientError } from '../src/api/errors';
import { ERROR_CODES } from '../src/api/known-codes';

interface Description {
  components: { schemas: { ErrorBody: { 'x-error-codes': Record<string, { status: number; meaning: string }> } } };
}

const description = parse(readFileSync(new URL('../../docs/api/openapi.yaml', import.meta.url), 'utf8')) as Description;
const table = description.components.schemas.ErrorBody['x-error-codes'];

function envelope(code: string, message: string): string {
  return JSON.stringify({ error: { code, message } });
}

describe('error codes', () => {
  it('the client knows exactly the codes of the API description', () => {
    expect([...ERROR_CODES].sort()).toEqual(Object.keys(table).sort());
  });

  it.each(Object.entries(table))('%s becomes a named api failure with the server message', (code, entry) => {
    const error = fromResponse(entry.status, envelope(code, 'A friendly message.'));
    expect(error).toEqual({ kind: 'api', code, status: entry.status, message: 'A friendly message.' });
  });

  it('an unknown future code is still an api failure that carries the server message', () => {
    const error = fromResponse(418, envelope('TEAPOT', 'I am a teapot.'));
    expect(error).toEqual({ kind: 'api', code: 'TEAPOT', status: 418, message: 'I am a teapot.' });
  });
});

describe('failures that are not api errors', () => {
  const technical = /Error|stack trace|undefined|fetch|JSON|<|\bat \S+\(/;

  it('a proxy page or any non-envelope body is unexpected, without technical detail', () => {
    for (const body of ['<html><body>502 Bad Gateway</body></html>', '', 'null', '{"error":"x"}', '{"error":{"code":5}}', '[]']) {
      const error = fromResponse(502, body);
      expect(error.kind).toBe('unexpected');
      expect(error.message).not.toMatch(technical);
      expect(error.message.length).toBeGreaterThan(10);
    }
  });

  it('a network failure is unreachable and an aborted request is a timeout', () => {
    const unreachable: ClientError = fromThrown(new TypeError('Failed to fetch'), false);
    const timeout: ClientError = fromThrown(new DOMException('aborted', 'AbortError'), true);
    expect(unreachable.kind).toBe('unreachable');
    expect(timeout.kind).toBe('timeout');
    for (const error of [unreachable, timeout]) {
      expect(error.message).not.toMatch(technical);
      expect(error.message.length).toBeGreaterThan(10);
    }
  });

  it('anything else that is thrown is unexpected', () => {
    expect(fromThrown(new Error('boom'), false).kind).toBe('unexpected');
  });
});
