import { describe, expect, it } from 'vitest';

import type { BackendClient } from '../../../src/backend/backend-client.js';
import type {
  RegisterRunnerInstanceRequest,
  RunnerControlStateResponse,
  RunnerEventIngestionResponse,
  RunnerEventRequest,
  RunnerHeartbeatRequest,
  RunnerInstanceResponse,
  RunnerRuntimeSecretsRequest,
  RunnerRuntimeSecretsResponse,
  RunnerTestRunConfigResponse,
  TestRunStatus,
} from '../../../src/backend/dto/runner-api.dto.js';
import { RunnerError } from '../../../src/errors/runner-error.js';
import { DefaultRunnerEventFactory } from '../../../src/events/runner-event-factory.js';
import { DefaultRunnerEventSanitizer } from '../../../src/events/runner-event-sanitizer.js';
import type { RunnerEventSink, RunnerEventSinkResult } from '../../../src/events/runner-event-sink.js';
import { DefaultMaskingPolicy } from '../../../src/masking/masking-policy.js';
import { RecursiveSensitiveDataMasker } from '../../../src/masking/sensitive-data-masker.js';
import { DefaultPromptfooConfigBuilder } from '../../../src/promptfoo/promptfoo-config-builder.js';
import type { PromptfooConfigObject } from '../../../src/promptfoo/promptfoo-adapter.js';
import type { PromptfooExecutor } from '../../../src/promptfoo/promptfoo-executor.js';
import { DefaultPromptfooResultNormalizer } from '../../../src/promptfoo/promptfoo-result-normalizer.js';
import type { CommandConsumer } from '../../../src/redis/command-consumer.js';
import type { RunnerCommandDelivery, RunnerCommandMessage } from '../../../src/redis/command-message.js';
import { DefaultRunnerService, type DefaultRunnerServiceOptions } from '../../../src/runner/runner-service.js';
import {
  promptfooFakeSecret,
  singleChatbotInput,
  successfulRawResult,
  targetTimeoutRawResult,
} from '../../fixtures/promptfoo/promptfoo-fixtures.js';

const command: RunnerCommandMessage = {
  commandId: '6c0f4306-883b-43b6-94fd-826d14c33074',
  testRunId: '27a47393-169d-4f01-92a0-59cf2dc10f8a',
  commandType: 'START_RUN',
  createdAt: '2026-07-09T10:00:00.000Z',
};

describe('DefaultRunnerService', () => {
  it('runs one START_RUN through register, heartbeat, config, secrets, promptfoo, events, and ack', async () => {
    const harness = createHarness();

    await harness.service.start();
    await harness.service.runOnce();

    expect(harness.backend.registerRequests).toEqual([
      { runnerKey: 'runner-local', hostname: 'runner-host', version: '0.0.0-test' },
    ]);
    expect(harness.backend.heartbeats.map((heartbeat) => heartbeat.status)).toEqual(['IDLE', 'BUSY', 'IDLE']);
    expect(harness.backend.configCalls).toEqual([{ testRunId: command.testRunId, page: 0, size: 100 }]);
    expect(harness.backend.secretRequests).toEqual([
      {
        testRunId: command.testRunId,
        request: { secretIds: ['7e1e7b32-6875-4308-88d8-aa56b18ff7e8'] },
      },
    ]);
    expect(harness.executor.executedConfigs).toHaveLength(1);
    expect(harness.sink.events.map((event) => event.eventType)).toEqual([
      'RUN_STARTED',
      'TESTCASE_COMPLETED',
      'RUN_COMPLETED',
    ]);
    expect(harness.consumer.ackedIds).toEqual(['1700000000000-0']);
  });

  it('loads every backend config page before executing Promptfoo', async () => {
    const firstPage = testRunConfigResponse({ page: 0, hasNext: true });
    const secondRow = { ...firstPage.rows[0]!, id: 102, rowIndex: 1, rowHash: 'row-hash-2' };
    const secondPage = testRunConfigResponse({ page: 1, hasNext: false, rows: [secondRow] });
    const harness = createHarness({ configPages: [firstPage, secondPage] });

    await harness.service.start();
    await harness.service.runOnce();

    expect(harness.backend.configCalls).toEqual([
      { testRunId: command.testRunId, page: 0, size: 100 },
      { testRunId: command.testRunId, page: 1, size: 100 },
    ]);
    expect(harness.executor.executedConfigs[0]?.tests).toHaveLength(2);
  });

  it('masks resolved runtime secrets before sending testcase events', async () => {
    const rawResult = successfulRawResult() as { results: Array<Record<string, unknown>> };
    rawResult.results[0] = {
      ...rawResult.results[0],
      response: { body: { answer: `secret=${promptfooFakeSecret}` } },
    };
    const harness = createHarness({ rawResult });

    await harness.service.start();
    await harness.service.runOnce();

    expect(JSON.stringify(harness.sink.events)).not.toContain(promptfooFakeSecret);
    expect(JSON.stringify(harness.sink.events)).toContain('****MASKED****');
  });

  it('does not acknowledge a command before the final event succeeds', async () => {
    const harness = createHarness({ failFinalEvent: true });

    await harness.service.start();

    await expect(harness.service.runOnce()).rejects.toThrow('Runner event delivery failed');
    expect(harness.sink.events.map((event) => event.eventType)).toEqual([
      'RUN_STARTED',
      'TESTCASE_COMPLETED',
      'RUN_COMPLETED',
    ]);
    expect(harness.consumer.ackedIds).toEqual([]);
  });

  it('does not log or emit resolved runtime secrets', async () => {
    const harness = createHarness();

    await harness.service.start();
    await harness.service.runOnce();

    expect(JSON.stringify(harness.sink.events)).not.toContain(promptfooFakeSecret);
    expect(JSON.stringify(harness.backend.heartbeats)).not.toContain(promptfooFakeSecret);
    expect(JSON.stringify(harness.consumer.ackedIds)).not.toContain(promptfooFakeSecret);
  });

  it('does not rerun a duplicate command when backend state is already terminal', async () => {
    const harness = createHarness({ controlStatus: 'COMPLETED' });

    await harness.service.start();
    await harness.service.runOnce();

    expect(harness.backend.configCalls).toEqual([]);
    expect(harness.backend.secretRequests).toEqual([]);
    expect(harness.executor.executedConfigs).toEqual([]);
    expect(harness.sink.events).toEqual([]);
    expect(harness.consumer.ackedIds).toEqual(['1700000000000-0']);
  });

  it('keeps event order stable for one run', async () => {
    const harness = createHarness();

    await harness.service.start();
    await harness.service.runOnce();

    expect(harness.sink.events.map((event) => `${event.eventId}:${event.eventType}`)).toEqual([
      'evt-1:RUN_STARTED',
      'evt-2:TESTCASE_COMPLETED',
      'evt-3:RUN_COMPLETED',
    ]);
  });

  it('sends RUN_CANCELLED when cancellation is already requested before execution', async () => {
    const harness = createHarness({
      controlStates: [{ testRunId: command.testRunId, status: 'CANCEL_REQUESTED', cancellationRequested: true }],
    });

    await harness.service.start();
    await harness.service.runOnce();

    expect(harness.backend.configCalls).toEqual([]);
    expect(harness.executor.executedConfigs).toEqual([]);
    expect(harness.sink.events.map((event) => event.eventType)).toEqual(['RUN_CANCELLED']);
    expect(harness.consumer.ackedIds).toEqual(['1700000000000-0']);
  });

  it('stops before the next testcase when cancellation is observed during result emission', async () => {
    const harness = createHarness({
      controlStates: [
        { testRunId: command.testRunId, status: 'RUNNING', cancellationRequested: false },
        { testRunId: command.testRunId, status: 'CANCEL_REQUESTED', cancellationRequested: true },
      ],
      rawResult: twoSuccessfulRawResults(),
    });

    await harness.service.start();
    await harness.service.runOnce();

    expect(harness.sink.events.map((event) => event.eventType)).toEqual([
      'RUN_STARTED',
      'TESTCASE_COMPLETED',
      'RUN_CANCELLED',
    ]);
    expect(harness.consumer.ackedIds).toEqual(['1700000000000-0']);
  });

  it('allows completion when cancellation is only observable after all testcase work is finished', async () => {
    const harness = createHarness({
      controlStates: [
        { testRunId: command.testRunId, status: 'RUNNING', cancellationRequested: false },
        { testRunId: command.testRunId, status: 'CANCEL_REQUESTED', cancellationRequested: true },
      ],
    });

    await harness.service.start();
    await harness.service.runOnce();

    expect(harness.sink.events.map((event) => event.eventType)).toEqual([
      'RUN_STARTED',
      'TESTCASE_COMPLETED',
      'RUN_COMPLETED',
    ]);
  });

  it('retries event delivery with the same eventId', async () => {
    const harness = createHarness({
      sinkFailures: [
        {
          eventType: 'RUN_STARTED',
          failures: 1,
          error: new RunnerError('backend temporarily unavailable', {
            category: 'backend.http',
            retryable: true,
            status: 503,
          }),
        },
      ],
    });

    await harness.service.start();
    await harness.service.runOnce();

    expect(harness.sink.events.map((event) => `${event.eventId}:${event.eventType}`)).toEqual([
      'evt-1:RUN_STARTED',
      'evt-1:RUN_STARTED',
      'evt-2:TESTCASE_COMPLETED',
      'evt-3:RUN_COMPLETED',
    ]);
  });

  it('reports retry budget exhaustion safely without acknowledging the command', async () => {
    const harness = createHarness({
      eventRetryMaxAttempts: 2,
      sinkFailures: [
        {
          eventType: 'RUN_STARTED',
          failures: 2,
          error: new RunnerError(`backend unavailable Authorization: Bearer ${promptfooFakeSecret}`, {
            category: 'backend.http',
            retryable: true,
            status: 503,
          }),
        },
      ],
    });

    await harness.service.start();

    await expect(harness.service.runOnce()).rejects.toThrow('Runner event delivery failed');
    expect(harness.sink.events.map((event) => `${event.eventId}:${event.eventType}`)).toEqual([
      'evt-1:RUN_STARTED',
      'evt-1:RUN_STARTED',
    ]);
    expect(harness.consumer.ackedIds).toEqual([]);
  });

  it('does not retry non-retryable 401 event failures', async () => {
    const harness = createHarness({
      eventRetryMaxAttempts: 3,
      sinkFailures: [
        {
          eventType: 'RUN_STARTED',
          failures: 3,
          error: new RunnerError('backend auth failed', {
            category: 'backend.http',
            retryable: false,
            status: 401,
          }),
        },
      ],
    });

    await harness.service.start();

    await expect(harness.service.runOnce()).rejects.toThrow('Runner event delivery failed');
    expect(harness.sink.events.map((event) => event.eventType)).toEqual(['RUN_STARTED']);
    expect(harness.consumer.ackedIds).toEqual([]);
  });

  it('maps run timeout to RUN_FAILED', async () => {
    const harness = createHarness({
      runTimeoutMs: 1,
      executorMode: 'never-resolve',
    });

    await harness.service.start();
    await harness.service.runOnce();

    expect(harness.sink.events.map((event) => event.eventType)).toEqual(['RUN_STARTED', 'RUN_FAILED']);
    expect(harness.sink.events.at(-1)?.payload).toEqual({
      error: 'Run execution timed out after 1ms',
      errorCategory: 'RUN_TIMEOUT',
    });
    expect(harness.consumer.ackedIds).toEqual(['1700000000000-0']);
    expect(harness.executor.lastSignal?.aborted).toBe(true);
  });

  it('emits Promptfoo progress while execution is active', async () => {
    const harness = createHarness({ executorMode: 'report-progress' });

    await harness.service.start();
    await harness.service.runOnce();

    expect(harness.sink.events.map((event) => event.eventType)).toEqual([
      'RUN_STARTED',
      'RUN_PROGRESS',
      'TESTCASE_COMPLETED',
      'RUN_COMPLETED',
    ]);
    expect(harness.sink.events[1]?.payload).toEqual({ completed: 1, total: 1 });
  });

  it('aborts Promptfoo and sends RUN_CANCELLED when backend cancellation is observed', async () => {
    const harness = createHarness({
      executorMode: 'wait-for-abort',
      controlPollIntervalMs: 1,
      controlStates: [
        { testRunId: command.testRunId, status: 'RUNNING', cancellationRequested: false },
        { testRunId: command.testRunId, status: 'CANCEL_REQUESTED', cancellationRequested: true },
      ],
    });

    await harness.service.start();
    await harness.service.runOnce();

    expect(harness.executor.lastSignal?.aborted).toBe(true);
    expect(harness.sink.events.map((event) => event.eventType)).toEqual(['RUN_STARTED', 'RUN_CANCELLED']);
  });

  it('maps target timeout to testcase-level error instead of run-level failure', async () => {
    const harness = createHarness({ rawResult: targetTimeoutRawResult() });

    await harness.service.start();
    await harness.service.runOnce();

    expect(harness.sink.events.map((event) => event.eventType)).toEqual([
      'RUN_STARTED',
      'TESTCASE_ERROR',
      'RUN_COMPLETED',
    ]);
  });
});

interface HarnessOptions {
  readonly controlStatus?: TestRunStatus;
  readonly controlStates?: readonly RunnerControlStateResponse[];
  readonly failFinalEvent?: boolean;
  readonly sinkFailures?: readonly EventFailureRule[];
  readonly rawResult?: unknown;
  readonly executorMode?: 'normal' | 'never-resolve' | 'report-progress' | 'wait-for-abort';
  readonly eventRetryMaxAttempts?: number;
  readonly runTimeoutMs?: number;
  readonly configPages?: readonly RunnerTestRunConfigResponse[];
  readonly controlPollIntervalMs?: number;
}

interface Harness {
  readonly service: DefaultRunnerService;
  readonly backend: FakeBackendClient;
  readonly consumer: FakeCommandConsumer;
  readonly sink: FakeRunnerEventSink;
  readonly executor: FakePromptfooExecutor;
}

function createHarness(options: HarnessOptions = {}): Harness {
  const backend = new FakeBackendClient(
    options.controlStatus ?? 'QUEUED',
    options.controlStates ?? [],
    options.configPages ?? [testRunConfigResponse()],
  );
  const consumer = new FakeCommandConsumer([{ streamMessageId: '1700000000000-0', message: command }]);
  const sink = new FakeRunnerEventSink(options.failFinalEvent === true, options.sinkFailures ?? []);
  const executor = new FakePromptfooExecutor(options.rawResult ?? successfulRawResult(), options.executorMode ?? 'normal');
  let eventIndex = 0;
  const eventFactory = new DefaultRunnerEventFactory({
    eventIdGenerator: () => {
      eventIndex += 1;
      return `evt-${eventIndex}`;
    },
    now: () => '2026-07-09T10:00:00.000Z',
  });

  let serviceOptions: DefaultRunnerServiceOptions = {
    runnerKey: 'runner-local',
    hostname: 'runner-host',
    version: '0.0.0-test',
    backendClient: backend,
    commandConsumer: consumer,
    eventSink: sink,
    eventFactory,
    eventSanitizer: new DefaultRunnerEventSanitizer(),
    configBuilder: new DefaultPromptfooConfigBuilder({
      masker: new RecursiveSensitiveDataMasker(new DefaultMaskingPolicy({ knownSecrets: [promptfooFakeSecret] })),
    }),
    promptfooExecutor: executor,
    resultNormalizer: new DefaultPromptfooResultNormalizer(),
    configPageSize: 100,
    eventRetryMaxAttempts: options.eventRetryMaxAttempts ?? (options.failFinalEvent === true ? 1 : 3),
    controlPollIntervalMs: options.controlPollIntervalMs ?? 1_000,
  };
  if (options.runTimeoutMs !== undefined) {
    serviceOptions = { ...serviceOptions, runTimeoutMs: options.runTimeoutMs };
  }

  const service = new DefaultRunnerService(serviceOptions);

  return { service, backend, consumer, sink, executor };
}

class FakeBackendClient implements BackendClient {
  public readonly registerRequests: RegisterRunnerInstanceRequest[] = [];
  public readonly heartbeats: RunnerHeartbeatRequest[] = [];
  public readonly configCalls: Array<{ readonly testRunId: string; readonly page: number; readonly size: number }> = [];
  public readonly secretRequests: Array<{
    readonly testRunId: string;
    readonly request: RunnerRuntimeSecretsRequest;
  }> = [];
  private readonly controlStates: RunnerControlStateResponse[];

  public constructor(
    private readonly controlStatus: TestRunStatus,
    controlStates: readonly RunnerControlStateResponse[],
    private readonly configPages: readonly RunnerTestRunConfigResponse[],
  ) {
    this.controlStates = [...controlStates];
  }

  public async registerInstance(request: RegisterRunnerInstanceRequest): Promise<RunnerInstanceResponse> {
    this.registerRequests.push(request);
    return runnerInstanceResponse(request.runnerKey, 'IDLE');
  }

  public async heartbeat(request: RunnerHeartbeatRequest): Promise<RunnerInstanceResponse> {
    this.heartbeats.push(request);
    return runnerInstanceResponse(request.runnerKey, request.status ?? 'IDLE');
  }

  public async getTestRunConfig(testRunId: string, page: number, size: number): Promise<RunnerTestRunConfigResponse> {
    this.configCalls.push({ testRunId, page, size });
    return this.configPages[page] ?? testRunConfigResponse({ page, hasNext: false, rows: [] });
  }

  public async resolveRuntimeSecrets(
    testRunId: string,
    request: RunnerRuntimeSecretsRequest,
  ): Promise<RunnerRuntimeSecretsResponse> {
    this.secretRequests.push({ testRunId, request });
    return { secrets: { '7e1e7b32-6875-4308-88d8-aa56b18ff7e8': promptfooFakeSecret } };
  }

  public async getControlState(testRunId: string): Promise<RunnerControlStateResponse> {
    const nextState = this.controlStates.shift();
    if (nextState !== undefined) {
      return nextState;
    }
    return { testRunId, status: this.controlStatus, cancellationRequested: false };
  }

  public async sendEvent(event: RunnerEventRequest): Promise<RunnerEventIngestionResponse> {
    return {
      eventId: event.eventId,
      testRunId: event.testRunId,
      sequenceNo: 1,
      status: 'APPLIED',
    };
  }
}

class FakeCommandConsumer implements CommandConsumer {
  public readonly ackedIds: string[] = [];

  public constructor(private readonly deliveries: RunnerCommandDelivery[]) {}

  public async readNextCommand(): Promise<RunnerCommandDelivery | null> {
    return this.deliveries.shift() ?? null;
  }

  public async ackCommand(streamMessageId: string): Promise<void> {
    this.ackedIds.push(streamMessageId);
  }
}

interface EventFailureRule {
  readonly eventType: RunnerEventRequest['eventType'];
  readonly failures: number;
  readonly error: Error;
}

class FakeRunnerEventSink implements RunnerEventSink {
  public readonly events: RunnerEventRequest[] = [];
  private readonly failuresByEventType = new Map<RunnerEventRequest['eventType'], EventFailureRule>();
  private readonly attemptsByEventType = new Map<RunnerEventRequest['eventType'], number>();

  public constructor(
    private readonly failFinalEvent: boolean,
    failureRules: readonly EventFailureRule[],
  ) {
    for (const rule of failureRules) {
      this.failuresByEventType.set(rule.eventType, rule);
    }
  }

  public async send(event: RunnerEventRequest): Promise<RunnerEventSinkResult> {
    this.events.push(event);
    if (this.failFinalEvent && event.eventType === 'RUN_COMPLETED') {
      throw new Error('final event failed');
    }
    const rule = this.failuresByEventType.get(event.eventType);
    if (rule !== undefined) {
      const attempts = (this.attemptsByEventType.get(event.eventType) ?? 0) + 1;
      this.attemptsByEventType.set(event.eventType, attempts);
      if (attempts <= rule.failures) {
        throw rule.error;
      }
    }

    return {
      transport: 'http',
      response: {
        eventId: event.eventId,
        testRunId: event.testRunId,
        sequenceNo: this.events.length,
        status: 'APPLIED',
      },
    };
  }
}

class FakePromptfooExecutor implements PromptfooExecutor {
  public readonly executedConfigs: PromptfooConfigObject[] = [];
  public lastSignal: AbortSignal | undefined;

  public constructor(
    private readonly rawResult: unknown,
    private readonly mode: 'normal' | 'never-resolve' | 'report-progress' | 'wait-for-abort',
  ) {}

  public async execute(
    config: PromptfooConfigObject | Record<string, unknown>,
    options?: { readonly signal?: AbortSignal; readonly onProgress?: (completed: number, total: number) => void },
  ): Promise<unknown> {
    this.executedConfigs.push(config as PromptfooConfigObject);
    this.lastSignal = options?.signal;
    if (this.mode === 'never-resolve') {
      return new Promise(() => {});
    }
    if (this.mode === 'report-progress') {
      options?.onProgress?.(1, 1);
    }
    if (this.mode === 'wait-for-abort') {
      return new Promise((_resolve, reject) => {
        options?.signal?.addEventListener('abort', () => reject(new Error('Promptfoo execution aborted')), {
          once: true,
        });
      });
    }
    return this.rawResult;
  }
}

function runnerInstanceResponse(runnerKey: string, status: RunnerInstanceResponse['status']): RunnerInstanceResponse {
  return {
    id: '508b6a0a-a345-4ee1-aaea-9aa9b82398f8',
    runnerKey,
    hostname: 'runner-host',
    version: '0.0.0-test',
    status,
    currentCommandId: null,
    currentTestRunId: null,
    lastHeartbeatAt: '2026-07-09T10:00:00.000Z',
    registeredAt: '2026-07-09T10:00:00.000Z',
    updatedAt: '2026-07-09T10:00:00.000Z',
  };
}

function testRunConfigResponse(
  options: {
    readonly page?: number;
    readonly hasNext?: boolean;
    readonly rows?: RunnerTestRunConfigResponse['rows'];
  } = {},
): RunnerTestRunConfigResponse {
  const input = singleChatbotInput();
  return {
    testRunId: input.testRunId,
    projectId: 'b7ab5b79-9d2d-478a-ab98-2d4e08d80371',
    runType: 'SINGLE_TARGET',
    status: 'QUEUED',
    datasetId: '3b98f5f7-0b86-42e4-8151-5a8a9921fbb9',
    datasetVersionId: 'e7f6b0b3-c343-43cd-9e9c-2a7508f6d7c6',
    datasetSchemaId: 'e6978c48-a151-497e-8452-8493f90c6a59',
    datasetSchemaVersion: 1,
    progressTotal: input.rows.length,
    page: options.page ?? 0,
    size: 100,
    hasNext: options.hasNext ?? false,
    domainSnapshot: {},
    promptfooSnapshot: input.promptfooSnapshot,
    targets: [...input.targets],
    evaluators: [...input.evaluators],
    rows: options.rows ?? [...input.rows],
  };
}

function twoSuccessfulRawResults(): unknown {
  const rawResult = successfulRawResult();
  if (!isRawResultRecord(rawResult)) {
    return rawResult;
  }
  const first = rawResult.results[0];
  if (!isRecord(first)) {
    return rawResult;
  }
  return {
    results: [
      first,
      {
        ...first,
        datasetRowId: 102,
        request: { body: { question: 'Second question?' } },
        response: { body: { answer: 'Second answer' } },
      },
    ],
  };
}

function isRawResultRecord(value: unknown): value is { readonly results: readonly unknown[] } {
  return isRecord(value) && Array.isArray(value.results);
}

function isRecord(value: unknown): value is Readonly<Record<string, unknown>> {
  return typeof value === 'object' && value !== null;
}
