import { createClient } from 'redis';

import type { RedisCommandFields } from './command-message.js';

export interface RedisLifecycle {
  connect(): Promise<void>;
  disconnect(): Promise<void>;
}

export interface RedisStreamMessage {
  readonly id: string;
  readonly message: RedisCommandFields;
}

export interface RedisStreamReadResult {
  readonly name: string;
  readonly messages: readonly RedisStreamMessage[];
}

export interface RedisGroupCreateOptions {
  readonly mkStream: boolean;
}

export interface RedisReadGroupOptions {
  readonly count: number;
  readonly blockMs: number;
}

export interface RedisCommandStreamClient extends RedisLifecycle {
  xGroupCreate(stream: string, group: string, id: string, options: RedisGroupCreateOptions): Promise<void>;
  xReadGroup(
    group: string,
    consumer: string,
    stream: string,
    id: string,
    options: RedisReadGroupOptions,
  ): Promise<readonly RedisStreamReadResult[] | null>;
  xAck(stream: string, group: string, id: string): Promise<number>;
}

export interface RedisEventStreamClient extends RedisLifecycle {
  xAdd(stream: string, id: string, fields: Record<string, string>): Promise<string>;
}

type NodeRedisClient = ReturnType<typeof createClient>;

export class NodeRedisStreamClient implements RedisCommandStreamClient, RedisEventStreamClient {
  private readonly client: NodeRedisClient;

  public constructor(redisUrl: string) {
    this.client = createClient({ url: redisUrl });
  }

  public async connect(): Promise<void> {
    await this.client.connect();
  }

  public async disconnect(): Promise<void> {
    await this.client.close();
  }

  public async xGroupCreate(
    stream: string,
    group: string,
    id: string,
    options: RedisGroupCreateOptions,
  ): Promise<void> {
    await this.client.xGroupCreate(stream, group, id, { MKSTREAM: options.mkStream });
  }

  public async xReadGroup(
    group: string,
    consumer: string,
    stream: string,
    id: string,
    options: RedisReadGroupOptions,
  ): Promise<readonly RedisStreamReadResult[] | null> {
    const result = await this.client.xReadGroup(
      group,
      consumer,
      { key: stream, id },
      { COUNT: options.count, BLOCK: options.blockMs },
    );

    return normalizeRedisStreamReadResult(result);
  }

  public async xAck(stream: string, group: string, id: string): Promise<number> {
    return Number(await this.client.xAck(stream, group, id));
  }

  public async xAdd(stream: string, id: string, fields: Record<string, string>): Promise<string> {
    const streamMessageId = await this.client.xAdd(stream, id, fields);
    if (streamMessageId === null) {
      throw new Error('Redis XADD did not return a stream message id');
    }
    return streamMessageId;
  }
}

export function createRedisCommandStreamClient(redisUrl: string): RedisCommandStreamClient {
  return new NodeRedisStreamClient(redisUrl);
}

export function createRedisEventStreamClient(redisUrl: string): RedisEventStreamClient {
  return new NodeRedisStreamClient(redisUrl);
}

function normalizeRedisStreamReadResult(result: unknown): readonly RedisStreamReadResult[] | null {
  if (result === null) {
    return null;
  }
  if (!Array.isArray(result)) {
    throw new Error('Unexpected Redis XREADGROUP response');
  }

  return result.map((stream) => normalizeStreamResult(stream));
}

function normalizeStreamResult(stream: unknown): RedisStreamReadResult {
  if (!isRecord(stream)) {
    throw new Error('Unexpected Redis stream response');
  }

  const name = getStringField(stream, 'name');
  const messages = getArrayField(stream, 'messages').map((message) => normalizeStreamMessage(message));
  return { name, messages };
}

function normalizeStreamMessage(message: unknown): RedisStreamMessage {
  if (!isRecord(message)) {
    throw new Error('Unexpected Redis stream message');
  }

  return {
    id: getStringField(message, 'id'),
    message: getCommandFields(message.message),
  };
}

function getStringField(record: Readonly<Record<string, unknown>>, key: string): string {
  const value = record[key];
  if (typeof value !== 'string') {
    throw new Error(`Unexpected Redis response field: ${key}`);
  }
  return value;
}

function getArrayField(record: Readonly<Record<string, unknown>>, key: string): readonly unknown[] {
  const value = record[key];
  if (!Array.isArray(value)) {
    throw new Error(`Unexpected Redis response field: ${key}`);
  }
  return value;
}

function getCommandFields(value: unknown): RedisCommandFields {
  if (Array.isArray(value) && value.every((field) => typeof field === 'string')) {
    return value;
  }
  if (isStringRecord(value)) {
    return value;
  }
  throw new Error('Unexpected Redis command message fields');
}

function isRecord(value: unknown): value is Readonly<Record<string, unknown>> {
  return typeof value === 'object' && value !== null;
}

function isStringRecord(value: unknown): value is Readonly<Record<string, string>> {
  return isRecord(value) && Object.values(value).every((field) => typeof field === 'string');
}
