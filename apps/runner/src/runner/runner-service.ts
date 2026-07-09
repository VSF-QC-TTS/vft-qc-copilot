import type { BackendClient } from '../backend/backend-client.js';
import type { RunnerTestRunConfigResponse, TestRunStatus } from '../backend/dto/runner-api.dto.js';
import type { RunnerEventFactory } from '../events/runner-event-factory.js';
import type { RunnerEventSink } from '../events/runner-event-sink.js';
import type { PromptfooAdapterInput, PromptfooCaseResult, PromptfooGeneratedConfig } from '../promptfoo/promptfoo-adapter.js';
import type { PromptfooConfigBuilder } from '../promptfoo/promptfoo-config-builder.js';
import type { PromptfooExecutor } from '../promptfoo/promptfoo-executor.js';
import type { PromptfooResultNormalizer } from '../promptfoo/promptfoo-result-normalizer.js';
import type { CommandConsumer } from '../redis/command-consumer.js';
import type { RunnerCommandDelivery, RunnerCommandMessage } from '../redis/command-message.js';

export interface RunnerService {
  start(): Promise<void>;
  runOnce(): Promise<void>;
  handleCommand(command: RunnerCommandMessage): Promise<void>;
}

export interface DefaultRunnerServiceOptions {
  readonly runnerKey: string;
  readonly hostname?: string;
  readonly version?: string;
  readonly backendClient: BackendClient;
  readonly commandConsumer: CommandConsumer;
  readonly eventSink: RunnerEventSink;
  readonly eventFactory: RunnerEventFactory;
  readonly configBuilder: PromptfooConfigBuilder;
  readonly promptfooExecutor: PromptfooExecutor;
  readonly resultNormalizer: PromptfooResultNormalizer;
  readonly configPageSize?: number;
}

const SECRET_REF_PATTERN = /\$\{secret:([0-9a-fA-F-]{36})\}/gu;
const TERMINAL_STATUSES = new Set<TestRunStatus>(['COMPLETED', 'FAILED', 'CANCELLED']);

export class DefaultRunnerService implements RunnerService {
  private readonly configPageSize: number;
  private registered = false;
  private activeTestRunId: string | undefined;

  public constructor(private readonly options: DefaultRunnerServiceOptions) {
    this.configPageSize = options.configPageSize ?? 100;
  }

  public async start(): Promise<void> {
    if (this.registered) {
      return;
    }

    await this.options.backendClient.registerInstance({
      runnerKey: this.options.runnerKey,
      hostname: this.options.hostname,
      version: this.options.version,
    });
    this.registered = true;
    await this.sendIdleHeartbeat();
  }

  public async runOnce(): Promise<void> {
    await this.start();
    if (this.activeTestRunId !== undefined) {
      return;
    }

    const delivery = await this.options.commandConsumer.readNextCommand();
    if (delivery === null) {
      return;
    }

    await this.handleDelivery(delivery);
  }

  public async handleCommand(command: RunnerCommandMessage): Promise<void> {
    await this.start();
    await this.executeCommand(command);
  }

  private async handleDelivery(delivery: RunnerCommandDelivery): Promise<void> {
    await this.executeCommand(delivery.message);
    await this.options.commandConsumer.ackCommand(delivery.streamMessageId);
  }

  private async executeCommand(command: RunnerCommandMessage): Promise<void> {
    if (command.commandType !== 'START_RUN') {
      return;
    }
    if (this.activeTestRunId !== undefined) {
      return;
    }

    this.activeTestRunId = command.testRunId;
    await this.sendBusyHeartbeat(command);
    try {
      const controlState = await this.options.backendClient.getControlState(command.testRunId);
      if (TERMINAL_STATUSES.has(controlState.status)) {
        return;
      }
      if (controlState.cancellationRequested || controlState.status === 'CANCEL_REQUESTED') {
        await this.options.eventSink.send(
          this.options.eventFactory.createRunCancelled({
            testRunId: command.testRunId,
            commandId: command.commandId,
            reason: 'cancellation requested before runner execution',
          }),
        );
        return;
      }

      const configResponse = await this.options.backendClient.getTestRunConfig(command.testRunId, 0, this.configPageSize);
      const generatedConfig = await this.buildPromptfooConfig(configResponse);

      await this.options.eventSink.send(
        this.options.eventFactory.createRunStarted({
          testRunId: command.testRunId,
          commandId: command.commandId,
        }),
      );

      const rawResult = await this.options.promptfooExecutor.execute(generatedConfig.configObject);
      const results = this.options.resultNormalizer.normalize(rawResult);
      for (const result of results) {
        await this.sendTestcaseEvent(command, result);
      }

      await this.options.eventSink.send(
        this.options.eventFactory.createRunCompleted({
          testRunId: command.testRunId,
          commandId: command.commandId,
          summary: summarizeResults(results),
        }),
      );
    } finally {
      this.activeTestRunId = undefined;
      await this.sendIdleHeartbeat();
    }
  }

  private async buildPromptfooConfig(configResponse: RunnerTestRunConfigResponse): Promise<PromptfooGeneratedConfig> {
    const secretIds = collectSecretIds(configResponse);
    const runtimeSecretsById =
      secretIds.length === 0
        ? {}
        : (
            await this.options.backendClient.resolveRuntimeSecrets(configResponse.testRunId, {
              secretIds,
            })
          ).secrets;

    return this.options.configBuilder.build(toPromptfooInput(configResponse, runtimeSecretsById));
  }

  private async sendTestcaseEvent(command: RunnerCommandMessage, result: PromptfooCaseResult): Promise<void> {
    const eventInput = {
      testRunId: command.testRunId,
      commandId: command.commandId,
      ...result,
    };

    if (isErrorStatus(result.status)) {
      await this.options.eventSink.send(this.options.eventFactory.createTestcaseError(eventInput));
      return;
    }

    await this.options.eventSink.send(this.options.eventFactory.createTestcaseCompleted(eventInput));
  }

  private async sendBusyHeartbeat(command: RunnerCommandMessage): Promise<void> {
    await this.options.backendClient.heartbeat({
      runnerKey: this.options.runnerKey,
      status: 'BUSY',
      currentCommandId: command.commandId,
      currentTestRunId: command.testRunId,
    });
  }

  private async sendIdleHeartbeat(): Promise<void> {
    await this.options.backendClient.heartbeat({
      runnerKey: this.options.runnerKey,
      status: 'IDLE',
      currentCommandId: null,
      currentTestRunId: null,
    });
  }
}

function toPromptfooInput(
  configResponse: RunnerTestRunConfigResponse,
  runtimeSecretsById: Record<string, string>,
): PromptfooAdapterInput {
  return {
    testRunId: configResponse.testRunId,
    targets: configResponse.targets,
    evaluators: configResponse.evaluators,
    rows: configResponse.rows,
    promptfooSnapshot: configResponse.promptfooSnapshot,
    runtimeSecretsById,
  };
}

function collectSecretIds(configResponse: RunnerTestRunConfigResponse): string[] {
  const secretIds = new Set<string>();
  collectSecretIdsFromValue(configResponse.targets, secretIds);
  collectSecretIdsFromValue(configResponse.evaluators, secretIds);
  collectSecretIdsFromValue(configResponse.promptfooSnapshot, secretIds);
  return [...secretIds].sort();
}

function collectSecretIdsFromValue(value: unknown, secretIds: Set<string>): void {
  if (typeof value === 'string') {
    for (const match of value.matchAll(SECRET_REF_PATTERN)) {
      const secretId = match[1];
      if (secretId !== undefined) {
        secretIds.add(secretId);
      }
    }
    return;
  }

  if (Array.isArray(value)) {
    for (const item of value) {
      collectSecretIdsFromValue(item, secretIds);
    }
    return;
  }

  if (isRecord(value)) {
    for (const item of Object.values(value)) {
      collectSecretIdsFromValue(item, secretIds);
    }
  }
}

function summarizeResults(results: readonly PromptfooCaseResult[]): Record<string, unknown> {
  let passed = 0;
  let failed = 0;
  let errored = 0;
  for (const result of results) {
    if (result.status === 'PASSED') {
      passed += 1;
    } else if (result.status === 'FAILED') {
      failed += 1;
    } else {
      errored += 1;
    }
  }

  return {
    total: results.length,
    passed,
    failed,
    errored,
  };
}

function isErrorStatus(status: PromptfooCaseResult['status']): boolean {
  return status === 'ERROR' || status === 'JUDGE_ERROR' || status === 'TIMEOUT';
}

function isRecord(value: unknown): value is Readonly<Record<string, unknown>> {
  return typeof value === 'object' && value !== null;
}
