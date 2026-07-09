import type { BackendClient } from '../backend/backend-client.js';
import type { RunnerTestRunConfigResponse, TestRunStatus } from '../backend/dto/runner-api.dto.js';
import { isRetryableBackendError } from '../errors/error-classifier.js';
import { RunnerError } from '../errors/runner-error.js';
import type { RunnerEventFactory } from '../events/runner-event-factory.js';
import { sendEventWithRetry, type RunnerEventSink } from '../events/runner-event-sink.js';
import type {
  PromptfooAdapterInput,
  PromptfooCaseResult,
  PromptfooConfigObject,
  PromptfooGeneratedConfig,
} from '../promptfoo/promptfoo-adapter.js';
import type { PromptfooConfigBuilder } from '../promptfoo/promptfoo-config-builder.js';
import type { PromptfooExecutor } from '../promptfoo/promptfoo-executor.js';
import type { PromptfooResultNormalizer } from '../promptfoo/promptfoo-result-normalizer.js';
import type { CommandConsumer } from '../redis/command-consumer.js';
import type { RunnerCommandDelivery, RunnerCommandMessage } from '../redis/command-message.js';
import { sleep, withTimeout } from '../utils/sleep.js';

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
  readonly backendRetryMaxAttempts?: number;
  readonly eventRetryMaxAttempts?: number;
  readonly retryDelayMs?: number;
  readonly runTimeoutMs?: number;
}

const SECRET_REF_PATTERN = /\$\{secret:([0-9a-fA-F-]{36})\}/gu;
const TERMINAL_STATUSES = new Set<TestRunStatus>(['COMPLETED', 'FAILED', 'CANCELLED']);

export class DefaultRunnerService implements RunnerService {
  private readonly configPageSize: number;
  private readonly backendRetryMaxAttempts: number;
  private readonly eventRetryMaxAttempts: number;
  private readonly retryDelayMs: number;
  private readonly runTimeoutMs: number | undefined;
  private registered = false;
  private activeTestRunId: string | undefined;
  private localCancellationRequested = false;

  public constructor(private readonly options: DefaultRunnerServiceOptions) {
    this.configPageSize = options.configPageSize ?? 100;
    this.backendRetryMaxAttempts = normalizeAttempts(options.backendRetryMaxAttempts ?? 3);
    this.eventRetryMaxAttempts = normalizeAttempts(options.eventRetryMaxAttempts ?? 3);
    this.retryDelayMs = options.retryDelayMs ?? 0;
    this.runTimeoutMs = options.runTimeoutMs;
  }

  public async start(): Promise<void> {
    if (this.registered) {
      return;
    }

    await this.callBackend(() => this.options.backendClient.registerInstance({
      runnerKey: this.options.runnerKey,
      hostname: this.options.hostname,
      version: this.options.version,
    }));
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
    if (command.commandType === 'CANCEL_RUN') {
      if (this.activeTestRunId === command.testRunId) {
        this.localCancellationRequested = true;
      }
      return;
    }
    if (this.activeTestRunId !== undefined) {
      return;
    }

    this.activeTestRunId = command.testRunId;
    this.localCancellationRequested = false;
    await this.sendBusyHeartbeat(command);
    try {
      const controlState = await this.getControlState(command.testRunId);
      if (TERMINAL_STATUSES.has(controlState.status)) {
        return;
      }
      if (controlState.cancellationRequested || controlState.status === 'CANCEL_REQUESTED') {
        await this.sendRunCancelled(command, 'cancellation requested before runner execution');
        return;
      }

      const configResponse = await this.callBackend(() =>
        this.options.backendClient.getTestRunConfig(command.testRunId, 0, this.configPageSize),
      );
      const generatedConfig = await this.buildPromptfooConfig(configResponse);

      await this.emitEvent(
        this.options.eventFactory.createRunStarted({
          testRunId: command.testRunId,
          commandId: command.commandId,
        }),
      );

      const rawResult = await this.executePromptfoo(generatedConfig.configObject);
      const results = this.options.resultNormalizer.normalize(rawResult);
      for (let index = 0; index < results.length; index += 1) {
        const result = results[index];
        if (result === undefined) {
          continue;
        }
        await this.sendTestcaseEvent(command, result);
        if (index < results.length - 1 && (await this.isCancellationRequested(command.testRunId))) {
          await this.sendRunCancelled(command, 'cancellation requested during runner execution');
          return;
        }
      }

      await this.emitEvent(
        this.options.eventFactory.createRunCompleted({
          testRunId: command.testRunId,
          commandId: command.commandId,
          summary: summarizeResults(results),
        }),
      );
    } catch (error) {
      if (isEventDeliveryError(error)) {
        throw error;
      }
      await this.sendRunFailed(command, error);
    } finally {
      this.activeTestRunId = undefined;
      this.localCancellationRequested = false;
      await this.sendIdleHeartbeat();
    }
  }

  private async buildPromptfooConfig(configResponse: RunnerTestRunConfigResponse): Promise<PromptfooGeneratedConfig> {
    const secretIds = collectSecretIds(configResponse);
    const runtimeSecretsById =
      secretIds.length === 0
        ? {}
        : (
            await this.callBackend(() =>
              this.options.backendClient.resolveRuntimeSecrets(configResponse.testRunId, {
                secretIds,
              }),
            )
          ).secrets;

    return this.options.configBuilder.build(toPromptfooInput(configResponse, runtimeSecretsById));
  }

  private async executePromptfoo(configObject: PromptfooConfigObject): Promise<unknown> {
    const execution = this.options.promptfooExecutor.execute(configObject);
    if (this.runTimeoutMs === undefined) {
      return execution;
    }

    return withTimeout(execution, {
      ms: this.runTimeoutMs,
      message: `Run execution timed out after ${this.runTimeoutMs}ms`,
      category: 'runner.timeout',
    });
  }

  private async getControlState(testRunId: string): Promise<Awaited<ReturnType<BackendClient['getControlState']>>> {
    return this.callBackend(() => this.options.backendClient.getControlState(testRunId));
  }

  private async isCancellationRequested(testRunId: string): Promise<boolean> {
    if (this.localCancellationRequested) {
      return true;
    }

    const controlState = await this.getControlState(testRunId);
    return controlState.cancellationRequested || controlState.status === 'CANCEL_REQUESTED';
  }

  private async sendRunCancelled(command: RunnerCommandMessage, reason: string): Promise<void> {
    await this.emitEvent(
      this.options.eventFactory.createRunCancelled({
        testRunId: command.testRunId,
        commandId: command.commandId,
        reason,
      }),
    );
  }

  private async sendRunFailed(command: RunnerCommandMessage, error: unknown): Promise<void> {
    await this.emitEvent(
      this.options.eventFactory.createRunFailed({
        testRunId: command.testRunId,
        commandId: command.commandId,
        error: runFailureMessage(error),
        errorCategory: runFailureCategory(error),
      }),
    );
  }

  private async emitEvent(event: Parameters<RunnerEventSink['send']>[0]): Promise<void> {
    try {
      await sendEventWithRetry(this.options.eventSink, event, { maxAttempts: this.eventRetryMaxAttempts });
    } catch (error) {
      const status = error instanceof RunnerError ? error.status : undefined;
      throw new RunnerError('Runner event delivery failed', {
        category: 'runner.event.delivery',
        retryable: isRetryableBackendError(error),
        cause: error,
        ...(status === undefined ? {} : { status }),
      });
    }
  }

  private async callBackend<TValue>(operation: () => Promise<TValue>): Promise<TValue> {
    let attempt = 0;
    while (attempt < this.backendRetryMaxAttempts) {
      attempt += 1;
      try {
        return await operation();
      } catch (error) {
        if (attempt >= this.backendRetryMaxAttempts || !isRetryableBackendError(error)) {
          throw error;
        }
        if (this.retryDelayMs > 0) {
          await sleep(this.retryDelayMs);
        }
      }
    }

    throw new RunnerError('Runner backend retry attempts exhausted', {
      category: 'runner.backend.retry_exhausted',
      retryable: false,
    });
  }

  private async sendTestcaseEvent(command: RunnerCommandMessage, result: PromptfooCaseResult): Promise<void> {
    const eventInput = {
      testRunId: command.testRunId,
      commandId: command.commandId,
      ...result,
    };

    if (isErrorStatus(result.status)) {
      await this.emitEvent(this.options.eventFactory.createTestcaseError(eventInput));
      return;
    }

    await this.emitEvent(this.options.eventFactory.createTestcaseCompleted(eventInput));
  }

  private async sendBusyHeartbeat(command: RunnerCommandMessage): Promise<void> {
    await this.callBackend(() => this.options.backendClient.heartbeat({
      runnerKey: this.options.runnerKey,
      status: 'BUSY',
      currentCommandId: command.commandId,
      currentTestRunId: command.testRunId,
    }));
  }

  private async sendIdleHeartbeat(): Promise<void> {
    await this.callBackend(() => this.options.backendClient.heartbeat({
      runnerKey: this.options.runnerKey,
      status: 'IDLE',
      currentCommandId: null,
      currentTestRunId: null,
    }));
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

function isEventDeliveryError(error: unknown): boolean {
  return error instanceof RunnerError && error.category === 'runner.event.delivery';
}

function runFailureMessage(error: unknown): string {
  if (error instanceof RunnerError && error.category === 'runner.timeout') {
    return error.message;
  }
  return 'Runner execution failed';
}

function runFailureCategory(error: unknown): string {
  if (error instanceof RunnerError && error.category === 'runner.timeout') {
    return 'RUN_TIMEOUT';
  }
  if (error instanceof RunnerError && error.category === 'runner.backend.retry_exhausted') {
    return 'BACKEND_RETRY_EXHAUSTED';
  }
  return 'RUNNER_EXECUTION_FAILED';
}

function normalizeAttempts(value: number): number {
  if (!Number.isFinite(value) || value < 1) {
    return 1;
  }
  return Math.floor(value);
}

function isRecord(value: unknown): value is Readonly<Record<string, unknown>> {
  return typeof value === 'object' && value !== null;
}
