import type { RunnerEventRequest } from '../backend/dto/runner-api.dto.js';
import type { RedisRunnerEventSinkResult, RunnerEventSink } from './runner-event-sink.js';

export interface RunnerEventPublisher {
  publish(event: RunnerEventRequest): Promise<string>;
}

export class RedisRunnerEventSink implements RunnerEventSink {
  public constructor(private readonly publisher: RunnerEventPublisher) {}

  public async send(event: RunnerEventRequest): Promise<RedisRunnerEventSinkResult> {
    const streamMessageId = await this.publisher.publish(event);
    return {
      transport: 'redis',
      eventId: event.eventId,
      testRunId: event.testRunId,
      streamMessageId,
    };
  }
}
