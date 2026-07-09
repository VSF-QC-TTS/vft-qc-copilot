import { describe, expect, it } from 'vitest';

import {
  DefaultRunnerEventFactory,
  type TestcaseCompletedEventInput,
  type TestcaseErrorEventInput,
} from '../../../src/events/runner-event-factory.js';

const testRunId = '27a47393-169d-4f01-92a0-59cf2dc10f8a';
const commandId = '6c0f4306-883b-43b6-94fd-826d14c33074';
const testRunTargetId = 'c1350b87-c35c-40b2-bbb1-b6f8815720fd';

describe('DefaultRunnerEventFactory', () => {
  it('creates a valid RUN_STARTED event', () => {
    const factory = createFactory('evt-run-started');

    const event = factory.createRunStarted({ testRunId, commandId });

    expect(event).toEqual({
      eventId: 'evt-run-started',
      testRunId,
      eventType: 'RUN_STARTED',
      commandId,
      occurredAt: '2026-07-09T10:00:00.000Z',
      payload: { status: 'RUNNING' },
    });
  });

  it('requires testRunTargetId and datasetRowId for TESTCASE_COMPLETED', () => {
    const factory = createFactory('evt-case-completed');
    const input = { testRunId, datasetRowId: 1, status: 'PASSED' } as unknown as TestcaseCompletedEventInput;

    expect(() => factory.createTestcaseCompleted(input)).toThrow();
  });

  it('requires testRunTargetId and datasetRowId for TESTCASE_ERROR', () => {
    const factory = createFactory('evt-case-error');
    const input = { testRunId, testRunTargetId, status: 'ERROR' } as unknown as TestcaseErrorEventInput;

    expect(() => factory.createTestcaseError(input)).toThrow();
  });

  it('creates a RUN_COMPLETED event with advisory summary', () => {
    const factory = createFactory('evt-run-completed');

    const event = factory.createRunCompleted({
      testRunId,
      commandId,
      summary: { total: 2, passed: 1, failed: 1, errored: 0, durationMs: 1234 },
    });

    expect(event.payload).toEqual({
      summary: { total: 2, passed: 1, failed: 1, errored: 0, durationMs: 1234 },
    });
  });

  it('sanitizes RUN_FAILED error text', () => {
    const factory = createFactory('evt-run-failed');

    const event = factory.createRunFailed({
      testRunId,
      commandId,
      error: 'Request failed with Authorization: Bearer vfqc-secret-token',
      errorCategory: 'BACKEND_UNAVAILABLE',
    });

    expect(event.payload).toEqual({
      error: 'Request failed with Authorization: Bearer ****MASKED****',
      errorCategory: 'BACKEND_UNAVAILABLE',
    });
  });

  it('creates a RUN_CANCELLED event with a reason', () => {
    const factory = createFactory('evt-run-cancelled');

    const event = factory.createRunCancelled({ testRunId, commandId, reason: 'user requested cancellation' });

    expect(event.payload).toEqual({ reason: 'user requested cancellation' });
  });

  it('keeps ARTIFACT_CREATED metadata-only', () => {
    const factory = createFactory('evt-artifact-created');

    const event = factory.createArtifactCreated({
      testRunId,
      commandId,
      artifactType: 'promptfoo-result',
      name: 'result.json',
      uri: 'local://result.json',
      sanitized: true,
      contentHash: 'sha256:abc123',
    });

    expect(event.payload).toEqual({
      artifactType: 'promptfoo-result',
      name: 'result.json',
      uri: 'local://result.json',
      sanitized: true,
      contentHash: 'sha256:abc123',
    });
  });
});

function createFactory(eventId: string): DefaultRunnerEventFactory {
  return new DefaultRunnerEventFactory({
    eventIdGenerator: () => eventId,
    now: () => '2026-07-09T10:00:00.000Z',
  });
}
