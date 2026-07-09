import { config as loadDotenv } from 'dotenv';
import type { Logger } from 'pino';

import { createBackendClient, type BackendClient } from './backend/backend-client.js';
import type { RunnerConfig } from './config/runner-config.js';
import { loadRunnerConfig } from './config/runner-config.js';
import { HttpRunnerEventSink } from './events/http-runner-event-sink.js';
import { DefaultRunnerEventFactory } from './events/runner-event-factory.js';
import { RedisRunnerEventSink } from './events/redis-runner-event-sink.js';
import type { RunnerEventSink } from './events/runner-event-sink.js';
import { createLogger } from './logging/logger.js';
import { DefaultMaskingPolicy } from './masking/masking-policy.js';
import { RecursiveSensitiveDataMasker } from './masking/sensitive-data-masker.js';
import { DefaultPromptfooConfigBuilder } from './promptfoo/promptfoo-config-builder.js';
import { PromptfooLibraryExecutor } from './promptfoo/promptfoo-executor.js';
import { DefaultPromptfooResultNormalizer } from './promptfoo/promptfoo-result-normalizer.js';
import { RedisCommandConsumer } from './redis/command-consumer.js';
import { RedisEventStreamPublisher } from './redis/event-stream-publisher.js';
import {
  createRedisCommandStreamClient,
  createRedisEventStreamClient,
  type RedisLifecycle,
  type RedisEventStreamClient,
} from './redis/redis-client.js';
import { DefaultRunnerService, type RunnerService } from './runner/runner-service.js';
import { sleep } from './utils/sleep.js';

export interface BootstrapResult {
  readonly runnerKey: string;
  readonly eventTransport: 'redis' | 'http';
}

export interface RunnerRuntimeLogger {
  info(object: Record<string, unknown>, message: string): void;
  warn(object: Record<string, unknown>, message: string): void;
  error(object: Record<string, unknown>, message: string): void;
}

export interface RunnerRuntime {
  start(signal?: AbortSignal): Promise<void>;
  stop(): Promise<void>;
}

export interface DefaultRunnerRuntimeOptions {
  readonly service: RunnerService;
  readonly resources: readonly RedisLifecycle[];
  readonly logger: RunnerRuntimeLogger;
  readonly loopErrorDelayMs?: number;
}

export interface ProcessSignalTarget {
  on(signal: 'SIGINT' | 'SIGTERM', listener: () => void | Promise<void>): unknown;
}

export class DefaultRunnerRuntime implements RunnerRuntime {
  private stopping = false;
  private connected = false;
  private readonly loopErrorDelayMs: number;

  public constructor(private readonly options: DefaultRunnerRuntimeOptions) {
    this.loopErrorDelayMs = options.loopErrorDelayMs ?? 1_000;
  }

  public async start(signal?: AbortSignal): Promise<void> {
    await this.connectResources();
    await this.options.service.start();
    try {
      while (!this.stopping && signal?.aborted !== true) {
        try {
          await this.options.service.runOnce();
        } catch (error) {
          this.options.logger.error({ errorType: errorType(error) }, 'Runner loop iteration failed');
          if (this.loopErrorDelayMs > 0) {
            await sleep(this.loopErrorDelayMs);
          }
        }
      }
    } finally {
      await this.stop();
    }
  }

  public async stop(): Promise<void> {
    this.stopping = true;
    if (!this.connected) {
      return;
    }

    await Promise.all(this.options.resources.map((resource) => resource.disconnect()));
    this.connected = false;
  }

  private async connectResources(): Promise<void> {
    if (this.connected) {
      return;
    }

    await Promise.all(this.options.resources.map((resource) => resource.connect()));
    this.connected = true;
  }
}

export function bootstrap(env: NodeJS.ProcessEnv = process.env): BootstrapResult {
  loadDotenv();
  const config = loadRunnerConfig(env);
  const logger = createLogger(config);
  logger.info({ eventTransport: config.eventTransport }, 'Runner bootstrap complete');
  return {
    runnerKey: config.runnerKey,
    eventTransport: config.eventTransport,
  };
}

export function createRunnerRuntime(config: RunnerConfig, logger: Logger): RunnerRuntime {
  const backendClient = createBackendClient(config);
  const commandClient = createRedisCommandStreamClient(config.redisUrl);
  const commandConsumer = new RedisCommandConsumer(commandClient, {
    stream: config.commandStream,
    group: config.commandGroup,
    consumerName: config.consumerName,
    blockMs: config.commandBlockMs,
  });
  const eventClient = config.eventTransport === 'redis' ? createRedisEventStreamClient(config.redisUrl) : undefined;
  const eventSink = createRunnerEventSink(config, backendClient, eventClient);
  const eventFactory = new DefaultRunnerEventFactory();
  const configBuilder = new DefaultPromptfooConfigBuilder({
    masker: new RecursiveSensitiveDataMasker(new DefaultMaskingPolicy()),
  });
  const service = new DefaultRunnerService({
    runnerKey: config.runnerKey,
    ...(config.hostname === undefined ? {} : { hostname: config.hostname }),
    version: config.version,
    backendClient,
    commandConsumer,
    eventSink,
    eventFactory,
    configBuilder,
    promptfooExecutor: new PromptfooLibraryExecutor(),
    resultNormalizer: new DefaultPromptfooResultNormalizer(),
    configPageSize: 100,
    backendRetryMaxAttempts: config.commandMaxAttempts,
    eventRetryMaxAttempts: config.commandMaxAttempts,
    runTimeoutMs: config.runTimeoutMs,
  });
  const resources = eventClient === undefined ? [commandClient] : [commandClient, eventClient];
  return new DefaultRunnerRuntime({ service, resources, logger });
}

export function createRunnerEventSink(
  config: Pick<RunnerConfig, 'eventTransport' | 'eventsStream'>,
  backendClient: BackendClient,
  eventClient?: RedisEventStreamClient,
): RunnerEventSink {
  if (config.eventTransport === 'http') {
    return new HttpRunnerEventSink(backendClient);
  }
  if (eventClient === undefined) {
    throw new Error('Redis event transport requires a Redis event stream client');
  }
  return new RedisRunnerEventSink(new RedisEventStreamPublisher(eventClient, { stream: config.eventsStream }));
}

export function installShutdownHandlers(
  runtime: Pick<RunnerRuntime, 'stop'>,
  logger: RunnerRuntimeLogger,
  processTarget: ProcessSignalTarget = process,
): void {
  const shutdown = async (signal: 'SIGINT' | 'SIGTERM'): Promise<void> => {
    logger.info({ signal }, 'Runner shutdown requested');
    try {
      await runtime.stop();
    } catch (error) {
      logger.error({ signal, errorType: errorType(error) }, 'Runner shutdown failed');
    }
  };
  processTarget.on('SIGINT', () => shutdown('SIGINT'));
  processTarget.on('SIGTERM', () => shutdown('SIGTERM'));
}

export async function main(): Promise<void> {
  loadDotenv();
  const config = loadRunnerConfig();
  const logger = createLogger(config);
  const runtime = createRunnerRuntime(config, logger);
  installShutdownHandlers(runtime, logger);
  logger.info({ eventTransport: config.eventTransport }, 'Runner runtime starting');
  await runtime.start();
}

if (process.env.NODE_ENV !== 'test') {
  await main();
}

function errorType(error: unknown): string {
  return error instanceof Error ? error.name : typeof error;
}
