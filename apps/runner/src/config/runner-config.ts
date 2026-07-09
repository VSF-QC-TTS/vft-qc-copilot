import { runnerEnvSchema, type RunnerEnv } from './env-schema.js';

export interface RunnerConfig {
  readonly runnerKey: string;
  readonly version: string;
  readonly hostname?: string;
  readonly backendBaseUrl: string;
  readonly runnerToken: string;
  readonly redisUrl: string;
  readonly commandStream: string;
  readonly commandGroup: string;
  readonly consumerName: string;
  readonly eventsStream: string;
  readonly eventTransport: 'redis' | 'http';
  readonly workDir: string;
  readonly artifactsDir: string;
  readonly heartbeatIntervalMs: number;
  readonly controlPollIntervalMs: number;
  readonly commandBlockMs: number;
  readonly commandMaxAttempts: number;
  readonly pendingIdleThresholdMs: number;
  readonly recoveryIntervalMs: number;
  readonly targetRequestTimeoutMs: number;
  readonly llmJudgeTimeoutMs: number;
  readonly testcaseTimeoutMs: number;
  readonly runTimeoutMs: number;
  readonly logLevel: RunnerEnv['VFQC_LOG_LEVEL'];
}

export function loadRunnerConfig(env: NodeJS.ProcessEnv = process.env): RunnerConfig {
  const parsed = runnerEnvSchema.parse(env);
  return {
    runnerKey: parsed.VFQC_RUNNER_KEY,
    version: parsed.VFQC_RUNNER_VERSION,
    ...(parsed.VFQC_RUNNER_HOSTNAME === undefined ? {} : { hostname: parsed.VFQC_RUNNER_HOSTNAME }),
    backendBaseUrl: parsed.VFQC_BACKEND_BASE_URL,
    runnerToken: parsed.VFQC_RUNNER_TOKEN,
    redisUrl: parsed.VFQC_REDIS_URL,
    commandStream: parsed.VFQC_REDIS_COMMAND_STREAM,
    commandGroup: parsed.VFQC_REDIS_COMMAND_GROUP,
    consumerName: parsed.VFQC_REDIS_CONSUMER_NAME,
    eventsStream: parsed.VFQC_REDIS_EVENTS_STREAM,
    eventTransport: parsed.VFQC_EVENT_TRANSPORT,
    workDir: parsed.VFQC_WORK_DIR,
    artifactsDir: parsed.VFQC_ARTIFACTS_DIR,
    heartbeatIntervalMs: parsed.VFQC_HEARTBEAT_INTERVAL_MS,
    controlPollIntervalMs: parsed.VFQC_CONTROL_POLL_INTERVAL_MS,
    commandBlockMs: parsed.VFQC_COMMAND_BLOCK_MS,
    commandMaxAttempts: parsed.VFQC_COMMAND_MAX_ATTEMPTS,
    pendingIdleThresholdMs: parsed.VFQC_PENDING_IDLE_THRESHOLD_MS,
    recoveryIntervalMs: parsed.VFQC_RECOVERY_INTERVAL_MS,
    targetRequestTimeoutMs: parsed.VFQC_TARGET_REQUEST_TIMEOUT_MS,
    llmJudgeTimeoutMs: parsed.VFQC_LLM_JUDGE_TIMEOUT_MS,
    testcaseTimeoutMs: parsed.VFQC_TESTCASE_TIMEOUT_MS,
    runTimeoutMs: parsed.VFQC_RUN_TIMEOUT_MS,
    logLevel: parsed.VFQC_LOG_LEVEL,
  };
}
