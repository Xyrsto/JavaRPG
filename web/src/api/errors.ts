/** A problem with a call, classified so a screen can show it. Messages are player-safe: no technical detail. */
export type ClientError =
  | { kind: 'api'; code: string; status: number; message: string }
  | { kind: 'unreachable'; message: string }
  | { kind: 'timeout'; message: string }
  | { kind: 'unexpected'; status: number; message: string };

const MESSAGES = {
  unreachable: 'The game server cannot be reached. Check that it is running and try again.',
  timeout: 'The game server took too long to answer. Please try again in a moment.',
  unexpected: 'The game server sent an answer the game did not expect. Please try again.',
} as const;

/** The server answered with a failure status: a known error envelope, or something else (a proxy page, say). */
export function fromResponse(status: number, bodyText: string): ClientError {
  try {
    const body: unknown = JSON.parse(bodyText);
    const error = (body as { error?: { code?: unknown; message?: unknown } } | null)?.error;
    if (typeof error?.code === 'string' && typeof error.message === 'string') {
      return { kind: 'api', code: error.code, status, message: error.message };
    }
  } catch {
    // not JSON: fall through to the generic failure
  }
  return { kind: 'unexpected', status, message: MESSAGES.unexpected };
}

/** The request itself failed before any answer arrived. */
export function fromThrown(thrown: unknown, timedOut: boolean): ClientError {
  if (timedOut) {
    return { kind: 'timeout', message: MESSAGES.timeout };
  }
  if (thrown instanceof TypeError) {
    return { kind: 'unreachable', message: MESSAGES.unreachable };
  }
  return { kind: 'unexpected', status: 0, message: MESSAGES.unexpected };
}

/** A success status with a body that is not what the description promises. */
export function unexpectedAnswer(status: number): ClientError {
  return { kind: 'unexpected', status, message: MESSAGES.unexpected };
}
