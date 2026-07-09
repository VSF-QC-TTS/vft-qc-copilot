import type { RunnerEventRequest } from '../backend/dto/runner-api.dto.js';
import type { RedisEventStreamClient } from './redis-client.js';

export type { RedisEventStreamClient } from './redis-client.js';

export interface EventStreamPublisher {
  publish(event: RunnerEventRequest): Promise<string>;
}

export interface RedisEventStreamPublisherOptions {
  readonly stream: string;
}

export class RedisEventStreamPublisher implements EventStreamPublisher {
  public constructor(
    private readonly client: RedisEventStreamClient,
    private readonly options: RedisEventStreamPublisherOptions,
  ) {}

  public async publish(event: RunnerEventRequest): Promise<string> {
    return this.client.xAdd(this.options.stream, '*', toRedisFields(event));
  }
}

function toRedisFields(event: RunnerEventRequest): Record<string, string> {
  const fields: Record<string, string> = {
    eventId: event.eventId,
    testRunId: event.testRunId,
    eventType: event.eventType,
  };

  addOptionalField(fields, 'runnerId', event.runnerId);
  addOptionalField(fields, 'commandId', event.commandId);
  addOptionalField(fields, 'occurredAt', event.occurredAt);
  if (event.payload !== undefined && event.payload !== null) {
    fields.payload = JSON.stringify(event.payload);
  }
  return fields;
}

function addOptionalField(fields: Record<string, string>, key: string, value: string | null | undefined): void {
  if (value !== undefined && value !== null && value.length > 0) {
    fields[key] = value;
  }
}
