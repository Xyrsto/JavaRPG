import type { ClientError } from './api/errors';
import type { HealthView } from './api/client';

/** The status line shown once the server answered. Plain text only. */
export function describeHealth(health: HealthView): string {
  const n = health.questsLoaded;
  const quests = `${n} ${n === 1 ? 'quest is' : 'quests are'} loaded.`;
  const base = `The game server is up. ${quests}`;
  return health.sandboxSafe ? base : `${base} Player code is running in a mode that is not the safe sandbox.`;
}

/** The status line shown when a call failed. Plain text only. */
export function describeFailure(error: ClientError): string {
  switch (error.kind) {
    case 'unreachable':
      return 'The game server cannot be reached. Please start the game server and this page will connect by itself.';
    case 'timeout':
      return 'The game server took too long to answer. Trying again.';
    case 'unexpected':
      return 'The game server sent an unexpected answer. Trying again.';
    case 'api':
      return error.message;
  }
}
