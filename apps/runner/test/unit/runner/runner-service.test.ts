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
import { DefaultRunnerEventFactory } from '../../../src/events/runner-event-factory.js';
import type { RunnerEventSink, RunnerEventSinkResult } from '../../../src/events/runner-event-sink.js';
import { DefaultMaskingPolicy } from '../../../src/masking/masking-policy.js';
import { RecursiveSensitiveDataMasker } from '../../../src/masking/sensitive-data-masker.js';
import { DefaultPromptfooConfigBuilder } from '../../../src/promptfoo/promptfoo-config-builder.js';
import type { PromptfooConfigObject } from '../../../src/promptfoo/promptfoo-adapter.js';
import type { PromptfooExecutor } from '../../../src/promptfoo/promptfoo-executor.js';
import { DefaultPromptfooResultNormalizer } from '../../../src/promptfoo/promptfoo-result-normalizer.js';
import type { CommandConsumer } from '../../../src/redis/command-consumer.js';
import type { RunnerCommandDelivery, RunnerCommandMessage } from '../../../src/redis/command-message.js';
import { DefaultRunnerService } from '../../../src/runner/runner-service.js';
import { promptfooFakeSecret, singleChatbotInput, successfulRawResult } from '../../fixtures/promptfoo/promptfoo-fixtures.js';

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

  it('does not acknowledge a command before the final event succeeds', async () => {
    const harness = createHarness({ failFinalEvent: true });

    await harness.service.start();

    await expect(harness.service.runOnce()).rejects.toThrow('final event failed');
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
});

interface HarnessOptions {
  readonly controlStatus?: TestRunStatus;
  readonly failFinalEvent?: boolean;
}

interface Harness {
  readonly service: DefaultRunnerService;
  readonly backend: FakeBackendClient;
  readonly consumer: FakeCommandConsumer;
  readonly sink: FakeRunnerEventSink;
  readonly executor: FakePromptfooExecutor;
}

function createHarness(options: HarnessOptions = {}): Harness {
  const backend = new FakeBackendClient(options.controlStatus ?? 'QUEUED');
  const consumer = new FakeCommandConsumer([{ streamMessageId: '1700000000000-0', message: command }]);
  const sink = new FakeRunnerEventSink(options.failFinalEvent === true);
  const executor = new FakePromptfooExecutor();
  let eventIndex = 0;
  const eventFactory = new DefaultRunnerEventFactory({
    eventIdGenerator: () => {
      eventIndex += 1;
      return `evt-${eventIndex}`;
    },
    now: () => '2026-07-09T10:00:00.000Z',
  });

  const service = new DefaultRunnerService({
    runnerKey: 'runner-local',
    hostname: 'runner-host',
    version: '0.0.0-test',
    backendClient: backend,
    commandConsumer: consumer,
    eventSink: sink,
    eventFactory,
    configBuilder: new DefaultPromptfooConfigBuilder({
      masker: new RecursiveSensitiveDataMasker(new DefaultMaskingPolicy({ knownSecrets: [promptfooFakeSecret] })),
    }),
    promptfooExecutor: executor,
    resultNormalizer: new DefaultPromptfooResultNormalizer(),
    configPageSize: 100,
  });

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

  public constructor(private readonly controlStatus: TestRunStatus) {}

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
    return testRunConfigResponse();
  }

  public async resolveRuntimeSecrets(
    testRunId: string,
    request: RunnerRuntimeSecretsRequest,
  ): Promise<RunnerRuntimeSecretsResponse> {
    this.secretRequests.push({ testRunId, request });
    return { secrets: { '7e1e7b32-6875-4308-88d8-aa56b18ff7e8': promptfooFakeSecret } };
  }

  public async getControlState(testRunId: string): Promise<RunnerControlStateResponse> {
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

class FakeRunnerEventSink implements RunnerEventSink {
  public readonly events: RunnerEventRequest[] = [];

  public constructor(private readonly failFinalEvent: boolean) {}

  public async send(event: RunnerEventRequest): Promise<RunnerEventSinkResult> {
    this.events.push(event);
    if (this.failFinalEvent && event.eventType === 'RUN_COMPLETED') {
      throw new Error('final event failed');
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

  public async execute(config: PromptfooConfigObject | Record<string, unknown>): Promise<unknown> {
    this.executedConfigs.push(config as PromptfooConfigObject);
    return successfulRawResult();
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

function testRunConfigResponse(): RunnerTestRunConfigResponse {
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
    domainSnapshot: {},
    promptfooSnapshot: input.promptfooSnapshot,
    targets: [...input.targets],
    evaluators: [...input.evaluators],
    rows: [...input.rows],
  };
}
