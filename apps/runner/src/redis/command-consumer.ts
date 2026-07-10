import { parseRunnerCommandFields, type RunnerCommandDelivery } from './command-message.js';
import type { RedisCommandStreamClient } from './redis-client.js';

export type { RedisCommandStreamClient } from './redis-client.js';

export interface CommandConsumer {
  readNextCommand(): Promise<RunnerCommandDelivery | null>;
  ackCommand(streamMessageId: string): Promise<void>;
}

export interface RedisCommandConsumerOptions {
  readonly stream: string;
  readonly group: string;
  readonly consumerName: string;
  readonly blockMs: number;
  readonly pendingIdleThresholdMs: number;
  readonly recoveryIntervalMs: number;
}

export class RedisCommandConsumer implements CommandConsumer {
  private groupReady = false;
  private lastRecoveryAt = 0;

  public constructor(
    private readonly client: RedisCommandStreamClient,
    private readonly options: RedisCommandConsumerOptions,
  ) {}

  public async readNextCommand(): Promise<RunnerCommandDelivery | null> {
    await this.ensureGroup();
    const recovered = await this.recoverStaleCommand();
    if (recovered !== null) {
      return recovered;
    }

    const result = await this.client.xReadGroup(
      this.options.group,
      this.options.consumerName,
      this.options.stream,
      '>',
      { count: 1, blockMs: this.options.blockMs },
    );
    const stream = result?.[0];
    const message = stream?.messages[0];
    if (message === undefined) {
      return null;
    }

    return {
      streamMessageId: message.id,
      message: parseRunnerCommandFields(message.message),
    };
  }

  private async recoverStaleCommand(): Promise<RunnerCommandDelivery | null> {
    const now = Date.now();
    if (now - this.lastRecoveryAt < this.options.recoveryIntervalMs) {
      return null;
    }
    this.lastRecoveryAt = now;
    const claimed = await this.client.xAutoClaim(
      this.options.stream,
      this.options.group,
      this.options.consumerName,
      this.options.pendingIdleThresholdMs,
      '0-0',
      { count: 1 },
    );
    const message = claimed.messages[0];
    if (message === undefined) {
      return null;
    }
    return {
      streamMessageId: message.id,
      message: parseRunnerCommandFields(message.message),
    };
  }

  public async ackCommand(streamMessageId: string): Promise<void> {
    await this.client.xAck(this.options.stream, this.options.group, streamMessageId);
  }

  private async ensureGroup(): Promise<void> {
    if (this.groupReady) {
      return;
    }

    try {
      await this.client.xGroupCreate(this.options.stream, this.options.group, '0', { mkStream: true });
    } catch (error) {
      if (!isBusyGroupError(error)) {
        throw error;
      }
    }
    this.groupReady = true;
  }
}

function isBusyGroupError(error: unknown): boolean {
  if (!(error instanceof Error)) {
    return false;
  }
  return error.message.includes('BUSYGROUP');
}
