import { describe, expect, it, vi } from 'vitest';

import type { RunnerConfig } from '../../src/config/runner-config.js';
import { HttpRunnerEventSink } from '../../src/events/http-runner-event-sink.js';
import { RedisRunnerEventSink } from '../../src/events/redis-runner-event-sink.js';
import {
  DefaultRunnerRuntime,
  createRunnerEventSink,
  installShutdownHandlers,
  type ProcessSignalTarget,
  type RunnerRuntimeLogger,
} from '../../src/main.js';
import type { RunnerService } from '../../src/runner/runner-service.js';

describe('main module', () => {
  it('imports without bootstrapping runner side effects in test mode', async () => {
    const consoleSpy = vi.spyOn(process.stdout, 'write');

    const module = await import('../../src/main.js');

    expect(module.bootstrap).toBeTypeOf('function');
    expect(consoleSpy).not.toHaveBeenCalled();
  });

  it('runs the worker loop until the shutdown signal is aborted', async () => {
    const controller = new AbortController();
    const service = new FakeRunnerService(() => {
      if (service.runOnceCalls === 2) {
        controller.abort();
      }
    });
    const commandClient = new FakeLifecycle('command');
    const eventClient = new FakeLifecycle('event');
    const runtime = new DefaultRunnerRuntime({
      service,
      resources: [commandClient, eventClient],
      logger: new FakeLogger(),
    });

    await runtime.start(controller.signal);

    expect(commandClient.calls).toEqual(['command:connect', 'command:disconnect']);
    expect(eventClient.calls).toEqual(['event:connect', 'event:disconnect']);
    expect(service.startCalls).toBe(1);
    expect(service.runOnceCalls).toBe(2);
  });

  it('creates Redis or HTTP event sinks from VFQC_EVENT_TRANSPORT', () => {
    expect(createRunnerEventSink({ ...configFixture(), eventTransport: 'redis' }, {} as never, {} as never)).toBeInstanceOf(
      RedisRunnerEventSink,
    );
    expect(createRunnerEventSink({ ...configFixture(), eventTransport: 'http' }, {} as never, {} as never)).toBeInstanceOf(
      HttpRunnerEventSink,
    );
  });

  it('installs SIGINT and SIGTERM shutdown handlers', async () => {
    const runtime = new FakeRuntime();
    const processTarget = new FakeProcessSignalTarget();

    installShutdownHandlers(runtime, new FakeLogger(), processTarget);
    await processTarget.emit('SIGINT');
    await processTarget.emit('SIGTERM');

    expect(runtime.stopCalls).toBe(2);
    expect(processTarget.signals).toEqual(['SIGINT', 'SIGTERM']);
  });
});

class FakeRunnerService implements RunnerService {
  public startCalls = 0;
  public runOnceCalls = 0;

  public constructor(private readonly afterRunOnce: () => void) {}

  public async start(): Promise<void> {
    this.startCalls += 1;
  }

  public async runOnce(): Promise<void> {
    this.runOnceCalls += 1;
    this.afterRunOnce();
  }

  public async handleCommand(): Promise<void> {
    throw new Error('Unexpected call');
  }
}

class FakeLifecycle {
  public readonly calls: string[] = [];

  public constructor(private readonly name: string) {}

  public async connect(): Promise<void> {
    this.calls.push(`${this.name}:connect`);
  }

  public async disconnect(): Promise<void> {
    this.calls.push(`${this.name}:disconnect`);
  }
}

class FakeLogger implements RunnerRuntimeLogger {
  public info(): void {}

  public warn(): void {}

  public error(): void {}
}

class FakeRuntime {
  public stopCalls = 0;

  public async stop(): Promise<void> {
    this.stopCalls += 1;
  }
}

type SignalListener = () => void | Promise<void>;

class FakeProcessSignalTarget implements ProcessSignalTarget {
  public readonly signals: string[] = [];
  private readonly listeners = new Map<string, SignalListener>();

  public on(signal: 'SIGINT' | 'SIGTERM', listener: SignalListener): this {
    this.signals.push(signal);
    this.listeners.set(signal, listener);
    return this;
  }

  public async emit(signal: 'SIGINT' | 'SIGTERM'): Promise<void> {
    const listener = this.listeners.get(signal);
    if (listener !== undefined) {
      await listener();
    }
  }
}

function configFixture(): RunnerConfig {
  return {
    runnerKey: 'runner-local',
    version: '0.0.0-test',
    hostname: 'localhost',
    backendBaseUrl: 'http://localhost:8080',
    runnerToken: 'dev-runner-token',
    redisUrl: 'redis://localhost:6379',
    commandStream: 'vfqc:runs:commands',
    commandGroup: 'vfqc-runners',
    consumerName: 'runner-local',
    eventsStream: 'vfqc:runs:events',
    eventTransport: 'redis',
    workDir: '.runner-work',
    artifactsDir: 'storage/artifacts',
    heartbeatIntervalMs: 10_000,
    controlPollIntervalMs: 1_000,
    commandBlockMs: 5_000,
    commandMaxAttempts: 3,
    pendingIdleThresholdMs: 60_000,
    recoveryIntervalMs: 30_000,
    targetRequestTimeoutMs: 60_000,
    llmJudgeTimeoutMs: 120_000,
    testcaseTimeoutMs: 180_000,
    runTimeoutMs: 21_600_000,
    logLevel: 'info',
  };
}
