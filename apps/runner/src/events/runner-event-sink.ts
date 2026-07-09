import type { RunnerEventIngestionResponse, RunnerEventRequest } from '../backend/dto/runner-api.dto.js';
import { RunnerError } from '../errors/runner-error.js';

export type RunnerEventSinkResult = HttpRunnerEventSinkResult | RedisRunnerEventSinkResult;

export interface HttpRunnerEventSinkResult {
  readonly transport: 'http';
  readonly response: RunnerEventIngestionResponse;
}

export interface RedisRunnerEventSinkResult {
  readonly transport: 'redis';
  readonly eventId: string;
  readonly testRunId: string;
  readonly streamMessageId: string;
}

export interface RunnerEventSink {
  send(event: RunnerEventRequest): Promise<RunnerEventSinkResult>;
}

export interface EventRetryOptions {
  readonly maxAttempts: number;
}

export async function sendEventWithRetry(
  sink: RunnerEventSink,
  event: RunnerEventRequest,
  options: EventRetryOptions,
): Promise<RunnerEventSinkResult> {
  let attempt = 0;

  while (attempt < options.maxAttempts) {
    attempt += 1;
    try {
      const result = await sink.send(event);
      if (isTerminalNonRetryableResult(result)) {
        return result;
      }
      return result;
    } catch (error) {
      if (attempt >= options.maxAttempts || !isRetryableError(error)) {
        throw error;
      }
    }
  }

  throw new RunnerError('Runner event retry attempts exhausted', {
    category: 'runner.event.retry',
    retryable: false,
  });
}

function isTerminalNonRetryableResult(result: RunnerEventSinkResult): boolean {
  return result.transport === 'http' && result.response.status === 'INVALID_STATE';
}

function isRetryableError(error: unknown): boolean {
  return error instanceof RunnerError && error.retryable === true;
}
