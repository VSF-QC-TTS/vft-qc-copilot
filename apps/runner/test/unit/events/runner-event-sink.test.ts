import { describe, expect, it } from 'vitest';

import type { BackendClient } from '../../../src/backend/backend-client.js';
import type {
  RunnerControlStateResponse,
  RunnerEventIngestionResponse,
  RunnerEventRequest,
  RunnerHeartbeatRequest,
  RunnerInstanceResponse,
  RunnerRuntimeSecretsRequest,
  RunnerRuntimeSecretsResponse,
  RunnerTestRunConfigResponse,
} from '../../../src/backend/dto/runner-api.dto.js';
import { RunnerError } from '../../../src/errors/runner-error.js';
import { HttpRunnerEventSink } from '../../../src/events/http-runner-event-sink.js';
import { RedisRunnerEventSink, type RunnerEventPublisher } from '../../../src/events/redis-runner-event-sink.js';
import { sendEventWithRetry } from '../../../src/events/runner-event-sink.js';

const testRunId = '27a47393-169d-4f01-92a0-59cf2dc10f8a';

const event: RunnerEventRequest = {
  eventId: 'evt-run-started',
  testRunId,
  eventType: 'RUN_STARTED',
  commandId: '6c0f4306-883b-43b6-94fd-826d14c33074',
  occurredAt: '2026-07-09T10:00:00.000Z',
  payload: { status: 'RUNNING' },
};

describe('runner event sinks', () => {
  it('publishes Redis events and returns the stream message id', async () => {
    const publisher = new FakeRunnerEventPublisher('1740000000000-0');
    const sink = new RedisRunnerEventSink(publisher);

    const result = await sink.send(event);

    expect(result).toEqual({
      transport: 'redis',
      eventId: 'evt-run-started',
      testRunId,
      streamMessageId: '1740000000000-0',
    });
    expect(publisher.published).toEqual([event]);
  });

  it('retries HTTP event sends with the same event id', async () => {
    const backend = new FakeBackendClient([
      new RunnerError('temporary backend error', { category: 'backend.http', retryable: true, status: 503 }),
      response('APPLIED', 7),
    ]);
    const sink = new HttpRunnerEventSink(backend);

    const result = await sendEventWithRetry(sink, event, { maxAttempts: 2 });

    expect(result).toEqual({ transport: 'http', response: response('APPLIED', 7) });
    expect(backend.eventIds).toEqual(['evt-run-started', 'evt-run-started']);
  });

  it('treats an idempotent replay response with the existing sequence as success', async () => {
    const backend = new FakeBackendClient([response('APPLIED', 3)]);
    const sink = new HttpRunnerEventSink(backend);

    const result = await sendEventWithRetry(sink, event, { maxAttempts: 3 });

    expect(result).toEqual({ transport: 'http', response: response('APPLIED', 3) });
  });

  it('does not retry INVALID_STATE responses forever', async () => {
    const backend = new FakeBackendClient([response('INVALID_STATE', 4)]);
    const sink = new HttpRunnerEventSink(backend);

    const result = await sendEventWithRetry(sink, event, { maxAttempts: 3 });

    expect(result).toEqual({ transport: 'http', response: response('INVALID_STATE', 4) });
    expect(backend.eventIds).toEqual(['evt-run-started']);
  });
});

class FakeRunnerEventPublisher implements RunnerEventPublisher {
  public readonly published: RunnerEventRequest[] = [];

  public constructor(private readonly streamMessageId: string) {}

  public async publish(eventToPublish: RunnerEventRequest): Promise<string> {
    this.published.push(eventToPublish);
    return this.streamMessageId;
  }
}

class FakeBackendClient implements BackendClient {
  public readonly eventIds: string[] = [];

  public constructor(private readonly results: readonly (RunnerEventIngestionResponse | RunnerError)[]) {}

  public async registerInstance(): Promise<RunnerInstanceResponse> {
    throw new Error('Unexpected call');
  }

  public async heartbeat(_request: RunnerHeartbeatRequest): Promise<RunnerInstanceResponse> {
    throw new Error('Unexpected call');
  }

  public async getTestRunConfig(): Promise<RunnerTestRunConfigResponse> {
    throw new Error('Unexpected call');
  }

  public async resolveRuntimeSecrets(
    _testRunId: string,
    _request: RunnerRuntimeSecretsRequest,
  ): Promise<RunnerRuntimeSecretsResponse> {
    throw new Error('Unexpected call');
  }

  public async getControlState(): Promise<RunnerControlStateResponse> {
    throw new Error('Unexpected call');
  }

  public async sendEvent(eventToSend: RunnerEventRequest): Promise<RunnerEventIngestionResponse> {
    this.eventIds.push(eventToSend.eventId);
    const result = this.results[this.eventIds.length - 1];
    if (result === undefined) {
      throw new Error('Missing fake backend result');
    }
    if (result instanceof RunnerError) {
      throw result;
    }
    return result;
  }
}

function response(status: RunnerEventIngestionResponse['status'], sequenceNo: number): RunnerEventIngestionResponse {
  return {
    eventId: 'evt-run-started',
    testRunId,
    sequenceNo,
    status,
  };
}
