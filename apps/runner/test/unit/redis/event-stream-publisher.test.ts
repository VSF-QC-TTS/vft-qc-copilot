import { describe, expect, it } from 'vitest';

import type { RunnerEventRequest } from '../../../src/backend/dto/runner-api.dto.js';
import { RedisEventStreamPublisher, type RedisEventStreamClient } from '../../../src/redis/event-stream-publisher.js';

const event: RunnerEventRequest = {
  eventId: 'evt-run-started',
  testRunId: '27a47393-169d-4f01-92a0-59cf2dc10f8a',
  eventType: 'RUN_STARTED',
  commandId: '6c0f4306-883b-43b6-94fd-826d14c33074',
  occurredAt: '2026-07-09T10:00:00.000Z',
  payload: { status: 'RUNNING' },
};

describe('RedisEventStreamPublisher', () => {
  it('publishes runner events to the configured Redis stream', async () => {
    const client = new FakeRedisEventStreamClient('1740000000000-0');
    const publisher = createPublisher(client);

    const streamMessageId = await publisher.publish(event);

    expect(streamMessageId).toBe('1740000000000-0');
    expect(client.addCalls).toEqual([
      {
        stream: 'vfqc:runs:events',
        id: '*',
        fields: {
          eventId: 'evt-run-started',
          testRunId: '27a47393-169d-4f01-92a0-59cf2dc10f8a',
          eventType: 'RUN_STARTED',
          commandId: '6c0f4306-883b-43b6-94fd-826d14c33074',
          occurredAt: '2026-07-09T10:00:00.000Z',
          payload: '{"status":"RUNNING"}',
        },
      },
    ]);
  });

  it('omits absent nullable event fields instead of publishing blank values', async () => {
    const client = new FakeRedisEventStreamClient('1740000000000-1');
    const publisher = createPublisher(client);

    await publisher.publish({
      eventId: 'evt-run-completed',
      testRunId: event.testRunId,
      eventType: 'RUN_COMPLETED',
      payload: { summary: { total: 1 } },
    });

    expect(client.addCalls[0]?.fields).toEqual({
      eventId: 'evt-run-completed',
      testRunId: event.testRunId,
      eventType: 'RUN_COMPLETED',
      payload: '{"summary":{"total":1}}',
    });
  });

  it('does not add plaintext secrets when the event payload is already sanitized', async () => {
    const client = new FakeRedisEventStreamClient('1740000000000-2');
    const publisher = createPublisher(client);

    await publisher.publish({
      eventId: 'evt-case',
      testRunId: event.testRunId,
      eventType: 'TESTCASE_COMPLETED',
      payload: { request: { Authorization: 'Bearer ****MASKED****' } },
    });

    expect(JSON.stringify(client.addCalls)).not.toContain('vfqc_secret_1234567890abcdef');
  });

  it('propagates Redis publish failures for caller retry handling', async () => {
    const client = new FakeRedisEventStreamClient('unused', new Error('redis unavailable'));
    const publisher = createPublisher(client);

    await expect(publisher.publish(event)).rejects.toThrow('redis unavailable');
  });
});

interface AddCall {
  readonly stream: string;
  readonly id: string;
  readonly fields: Record<string, string>;
}

class FakeRedisEventStreamClient implements RedisEventStreamClient {
  public readonly addCalls: AddCall[] = [];

  public constructor(
    private readonly streamMessageId: string,
    private readonly failure?: Error,
  ) {}

  public async connect(): Promise<void> {}

  public async disconnect(): Promise<void> {}

  public async xAdd(stream: string, id: string, fields: Record<string, string>): Promise<string> {
    this.addCalls.push({ stream, id, fields });
    if (this.failure !== undefined) {
      throw this.failure;
    }
    return this.streamMessageId;
  }
}

function createPublisher(client: RedisEventStreamClient): RedisEventStreamPublisher {
  return new RedisEventStreamPublisher(client, { stream: 'vfqc:runs:events' });
}
