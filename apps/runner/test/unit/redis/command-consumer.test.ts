import { describe, expect, it } from 'vitest';

import { RedisCommandConsumer, type RedisCommandStreamClient } from '../../../src/redis/command-consumer.js';
import type { RedisStreamReadResult } from '../../../src/redis/redis-client.js';

const commandId = '6c0f4306-883b-43b6-94fd-826d14c33074';
const testRunId = '27a47393-169d-4f01-92a0-59cf2dc10f8a';
const createdAt = '2026-07-09T10:00:00+07:00';

describe('RedisCommandConsumer', () => {
  it('reads the next command through XREADGROUP without acknowledging it', async () => {
    const client = new FakeRedisCommandStreamClient([
      {
        name: 'vfqc:runs:commands',
        messages: [
          {
            id: '1740000000000-0',
            message: ['commandId', commandId, 'testRunId', testRunId, 'commandType', 'START_RUN', 'createdAt', createdAt],
          },
        ],
      },
    ]);
    const consumer = createConsumer(client);

    const delivery = await consumer.readNextCommand();

    expect(delivery).toEqual({
      streamMessageId: '1740000000000-0',
      message: {
        commandId,
        testRunId,
        commandType: 'START_RUN',
        createdAt,
      },
    });
    expect(client.ackCalls).toEqual([]);
  });

  it('exposes XACK separately for successful downstream completion', async () => {
    const client = new FakeRedisCommandStreamClient([]);
    const consumer = createConsumer(client);

    await consumer.ackCommand('1740000000000-0');

    expect(client.ackCalls).toEqual([{ stream: 'vfqc:runs:commands', group: 'vfqc-runners', id: '1740000000000-0' }]);
  });

  it('creates the consumer group before reading commands', async () => {
    const client = new FakeRedisCommandStreamClient([]);
    const consumer = createConsumer(client);

    await consumer.readNextCommand();

    expect(client.groupCreateCalls).toEqual([
      { stream: 'vfqc:runs:commands', group: 'vfqc-runners', id: '0', mkStream: true },
    ]);
  });

  it('returns null when XREADGROUP has no messages', async () => {
    const client = new FakeRedisCommandStreamClient([]);
    const consumer = createConsumer(client);

    const delivery = await consumer.readNextCommand();

    expect(delivery).toBeNull();
  });

  it('reclaims a stale pending command before reading a new command', async () => {
    const staleMessage: RedisStreamReadResult['messages'][number] = {
      id: '1739999999999-0',
      message: ['commandId', commandId, 'testRunId', testRunId, 'commandType', 'START_RUN', 'createdAt', createdAt],
    };
    const client = new FakeRedisCommandStreamClient([], [staleMessage]);
    const consumer = createConsumer(client);

    const delivery = await consumer.readNextCommand();

    expect(delivery?.streamMessageId).toBe('1739999999999-0');
    expect(client.autoClaimCalls).toEqual([
      {
        stream: 'vfqc:runs:commands',
        group: 'vfqc-runners',
        consumer: 'runner-01',
        minIdleTimeMs: 60_000,
        start: '0-0',
        count: 1,
      },
    ]);
    expect(client.readCalls).toBe(0);
  });
});

interface GroupCreateCall {
  readonly stream: string;
  readonly group: string;
  readonly id: string;
  readonly mkStream: boolean;
}

interface AckCall {
  readonly stream: string;
  readonly group: string;
  readonly id: string;
}

class FakeRedisCommandStreamClient implements RedisCommandStreamClient {
  public readonly groupCreateCalls: GroupCreateCall[] = [];
  public readonly ackCalls: AckCall[] = [];
  public readonly autoClaimCalls: Array<{
    readonly stream: string;
    readonly group: string;
    readonly consumer: string;
    readonly minIdleTimeMs: number;
    readonly start: string;
    readonly count: number;
  }> = [];
  public readCalls = 0;

  public constructor(
    private readonly result: readonly RedisStreamReadResult[],
    private readonly claimedMessages: readonly RedisStreamReadResult['messages'][number][] = [],
  ) {}

  public async connect(): Promise<void> {}

  public async disconnect(): Promise<void> {}

  public async xGroupCreate(stream: string, group: string, id: string, options: { readonly mkStream: boolean }): Promise<void> {
    this.groupCreateCalls.push({ stream, group, id, mkStream: options.mkStream });
  }

  public async xReadGroup(): Promise<readonly RedisStreamReadResult[] | null> {
    this.readCalls += 1;
    return this.result.length === 0 ? null : this.result;
  }

  public async xAutoClaim(
    stream: string,
    group: string,
    consumer: string,
    minIdleTimeMs: number,
    start: string,
    options: { readonly count: number },
  ): Promise<{ readonly nextId: string; readonly messages: readonly RedisStreamReadResult['messages'][number][] }> {
    this.autoClaimCalls.push({ stream, group, consumer, minIdleTimeMs, start, count: options.count });
    return { nextId: '0-0', messages: this.claimedMessages };
  }

  public async xAck(stream: string, group: string, id: string): Promise<number> {
    this.ackCalls.push({ stream, group, id });
    return 1;
  }
}

function createConsumer(client: RedisCommandStreamClient): RedisCommandConsumer {
  return new RedisCommandConsumer(client, {
    stream: 'vfqc:runs:commands',
    group: 'vfqc-runners',
    consumerName: 'runner-01',
    blockMs: 5_000,
    pendingIdleThresholdMs: 60_000,
    recoveryIntervalMs: 30_000,
  });
}
