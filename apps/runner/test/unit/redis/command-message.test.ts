import { describe, expect, it } from 'vitest';

import { parseRunnerCommandFields, parseRunnerCommandMessage } from '../../../src/redis/command-message.js';

const commandId = '6c0f4306-883b-43b6-94fd-826d14c33074';
const testRunId = '27a47393-169d-4f01-92a0-59cf2dc10f8a';
const createdAt = '2026-07-09T10:00:00+07:00';

describe('runner command message parser', () => {
  it('parses a valid START_RUN command', () => {
    const message = {
      commandId,
      testRunId,
      commandType: 'START_RUN',
      createdAt,
    };

    const parsed = parseRunnerCommandMessage(message);

    expect(parsed).toEqual(message);
  });

  it('parses a valid CANCEL_RUN command', () => {
    const message = {
      commandId,
      testRunId,
      commandType: 'CANCEL_RUN',
      createdAt,
    };

    const parsed = parseRunnerCommandMessage(message);

    expect(parsed.commandType).toBe('CANCEL_RUN');
  });

  it('rejects invalid command types', () => {
    const message = {
      commandId,
      testRunId,
      commandType: 'PAUSE_RUN',
      createdAt,
    };

    expect(() => parseRunnerCommandMessage(message)).toThrow();
  });

  it('rejects missing required ids', () => {
    const message = {
      commandType: 'START_RUN',
      createdAt,
    };

    expect(() => parseRunnerCommandMessage(message)).toThrow();
  });

  it('converts raw Redis field arrays into typed messages', () => {
    const fields = ['commandId', commandId, 'testRunId', testRunId, 'commandType', 'START_RUN', 'createdAt', createdAt];

    const parsed = parseRunnerCommandFields(fields);

    expect(parsed).toEqual({
      commandId,
      testRunId,
      commandType: 'START_RUN',
      createdAt,
    });
  });
});
