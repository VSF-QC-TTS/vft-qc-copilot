import { v7 as uuidv7 } from 'uuid';

import type { RunnerEventRequest } from '../backend/dto/runner-api.dto.js';
import { RunnerEventRequestSchema } from '../backend/dto/runner-api.dto.js';
import {
  ArtifactCreatedPayloadSchema,
  RunCancelledPayloadSchema,
  RunCompletedPayloadSchema,
  RunFailedPayloadSchema,
  RunLogPayloadSchema,
  RunProgressPayloadSchema,
  RunStartedPayloadSchema,
  TestcaseResultPayloadSchema,
  type ArtifactCreatedPayload,
  type RunCancelledPayload,
  type RunCompletedPayload,
  type RunFailedPayload,
  type RunLogPayload,
  type RunProgressPayload,
  type TestcaseResultPayload,
} from './normalized-events.js';

export interface RunnerEventFactory {
  createRunStarted(input: RunStartedEventInput): RunnerEventRequest;
  createRunProgress(input: RunProgressEventInput): RunnerEventRequest;
  createTestcaseCompleted(input: TestcaseCompletedEventInput): RunnerEventRequest;
  createTestcaseError(input: TestcaseErrorEventInput): RunnerEventRequest;
  createRunCompleted(input: RunCompletedEventInput): RunnerEventRequest;
  createRunFailed(input: RunFailedEventInput): RunnerEventRequest;
  createRunCancelled(input: RunCancelledEventInput): RunnerEventRequest;
  createRunLog(input: RunLogEventInput): RunnerEventRequest;
  createArtifactCreated(input: ArtifactCreatedEventInput): RunnerEventRequest;
}

export interface RunnerEventFactoryOptions {
  readonly eventIdGenerator?: () => string;
  readonly now?: () => string;
  readonly runnerId?: string;
}

interface BaseEventInput {
  readonly testRunId: string;
  readonly commandId?: string;
}

export type RunStartedEventInput = BaseEventInput;
export type RunProgressEventInput = BaseEventInput & RunProgressPayload;
export type TestcaseCompletedEventInput = BaseEventInput & TestcaseResultPayload;
export type TestcaseErrorEventInput = BaseEventInput & TestcaseResultPayload;
export type RunCompletedEventInput = BaseEventInput & RunCompletedPayload;
export type RunFailedEventInput = BaseEventInput & RunFailedPayload;
export type RunCancelledEventInput = BaseEventInput & RunCancelledPayload;
export type RunLogEventInput = BaseEventInput & RunLogPayload;
export type ArtifactCreatedEventInput = BaseEventInput & ArtifactCreatedPayload;

export class DefaultRunnerEventFactory implements RunnerEventFactory {
  private readonly eventIdGenerator: () => string;
  private readonly now: () => string;
  private readonly runnerId?: string;

  public constructor(options: RunnerEventFactoryOptions = {}) {
    this.eventIdGenerator = options.eventIdGenerator ?? uuidv7;
    this.now = options.now ?? (() => new Date().toISOString());
    if (options.runnerId !== undefined) {
      this.runnerId = options.runnerId;
    }
  }

  public createRunStarted(input: RunStartedEventInput): RunnerEventRequest {
    return this.createEvent(input, 'RUN_STARTED', RunStartedPayloadSchema.parse({ status: 'RUNNING' }));
  }

  public createRunProgress(input: RunProgressEventInput): RunnerEventRequest {
    const payload = RunProgressPayloadSchema.parse(input);
    return this.createEvent(input, 'RUN_PROGRESS', payload);
  }

  public createTestcaseCompleted(input: TestcaseCompletedEventInput): RunnerEventRequest {
    const payload = TestcaseResultPayloadSchema.parse(input);
    return this.createEvent(input, 'TESTCASE_COMPLETED', payload);
  }

  public createTestcaseError(input: TestcaseErrorEventInput): RunnerEventRequest {
    const payload = TestcaseResultPayloadSchema.parse(input);
    return this.createEvent(input, 'TESTCASE_ERROR', payload);
  }

  public createRunCompleted(input: RunCompletedEventInput): RunnerEventRequest {
    const payload = RunCompletedPayloadSchema.parse(input);
    return this.createEvent(input, 'RUN_COMPLETED', payload);
  }

  public createRunFailed(input: RunFailedEventInput): RunnerEventRequest {
    const payload = RunFailedPayloadSchema.parse({
      ...input,
      error: sanitizeEventText(input.error),
      message: sanitizeEventText(input.message),
    });
    return this.createEvent(input, 'RUN_FAILED', payload);
  }

  public createRunCancelled(input: RunCancelledEventInput): RunnerEventRequest {
    const payload = RunCancelledPayloadSchema.parse(input);
    return this.createEvent(input, 'RUN_CANCELLED', payload);
  }

  public createRunLog(input: RunLogEventInput): RunnerEventRequest {
    const payload = RunLogPayloadSchema.parse({
      ...input,
      message: sanitizeEventText(input.message),
    });
    return this.createEvent(input, 'RUN_LOG', payload);
  }

  public createArtifactCreated(input: ArtifactCreatedEventInput): RunnerEventRequest {
    const payload = ArtifactCreatedPayloadSchema.parse({
      artifactType: input.artifactType,
      name: input.name,
      uri: input.uri,
      sanitized: input.sanitized,
      contentHash: input.contentHash,
    });
    return this.createEvent(input, 'ARTIFACT_CREATED', payload);
  }

  private createEvent(
    input: BaseEventInput,
    eventType: RunnerEventRequest['eventType'],
    payload: Record<string, unknown>,
  ): RunnerEventRequest {
    const event: RunnerEventRequest = {
      eventId: this.eventIdGenerator(),
      testRunId: input.testRunId,
      eventType,
      occurredAt: this.now(),
      payload,
    };
    if (this.runnerId !== undefined) {
      event.runnerId = this.runnerId;
    }
    if (input.commandId !== undefined) {
      event.commandId = input.commandId;
    }
    return RunnerEventRequestSchema.parse(event);
  }
}

function sanitizeEventText(value: string | undefined): string | undefined {
  if (value === undefined) {
    return undefined;
  }

  return value
    .replace(/(Authorization:\s*Bearer\s+)[^\s,;]+/giu, '$1****MASKED****')
    .replace(/((?:token|secret|password|api[_-]?key)=)[^\s,;]+/giu, '$1****MASKED****');
}
