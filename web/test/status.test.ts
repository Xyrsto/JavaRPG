import { describe, expect, it } from 'vitest';
import { describeHealth, describeFailure } from '../src/status';

describe('the status line', () => {
  it('says the server is up with the number of quests', () => {
    expect(describeHealth({ status: 'UP', sandbox: 'docker', sandboxSafe: true, questsLoaded: 2 })).toBe(
      'The game server is up. 2 quests are loaded.',
    );
    expect(describeHealth({ status: 'UP', sandbox: 'docker', sandboxSafe: true, questsLoaded: 1 })).toBe(
      'The game server is up. 1 quest is loaded.',
    );
  });

  it('warns when player code does not run in the safe sandbox', () => {
    const text = describeHealth({ status: 'UP', sandbox: 'unsafe-local', sandboxSafe: false, questsLoaded: 2, warning: 'x' });
    expect(text).toContain('not the safe sandbox');
  });

  it('explains each failure in plain words and says how to start the server when unreachable', () => {
    expect(describeFailure({ kind: 'unreachable', message: 'm' })).toContain('cannot be reached');
    expect(describeFailure({ kind: 'unreachable', message: 'm' })).toContain('start');
    expect(describeFailure({ kind: 'timeout', message: 'm' })).toContain('too long');
    expect(describeFailure({ kind: 'unexpected', status: 502, message: 'm' })).toContain('unexpected');
    expect(describeFailure({ kind: 'api', code: 'INTERNAL', status: 500, message: 'Server says hi.' })).toBe('Server says hi.');
  });

  it('never returns markup-looking text of its own', () => {
    const texts = [
      describeFailure({ kind: 'unreachable', message: 'm' }),
      describeFailure({ kind: 'timeout', message: 'm' }),
      describeFailure({ kind: 'unexpected', status: 500, message: 'm' }),
    ];
    for (const text of texts) expect(text).not.toMatch(/[<>]/);
  });
});
