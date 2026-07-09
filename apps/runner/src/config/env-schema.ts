import { z } from 'zod';

const LogLevelSchema = z.enum(['trace', 'debug', 'info', 'warn', 'error', 'fatal']);
const EventTransportSchema = z.enum(['redis', 'http']);

const positiveIntegerFromEnv = (defaultValue: number) =>
  z.coerce.number().int().positive().default(defaultValue);

export const runnerEnvSchema = z.object({
  VFQC_RUNNER_KEY: z.string().min(1),
  VFQC_RUNNER_VERSION: z.string().min(1).default('0.0.0'),
  VFQC_RUNNER_HOSTNAME: z.string().min(1).optional(),

  VFQC_BACKEND_BASE_URL: z.url(),
  VFQC_RUNNER_TOKEN: z.string().min(1),

  VFQC_REDIS_URL: z.url().default('redis://localhost:6379'),
  VFQC_REDIS_COMMAND_STREAM: z.string().min(1).default('vfqc:runs:commands'),
  VFQC_REDIS_COMMAND_GROUP: z.string().min(1).default('vfqc-runners'),
  VFQC_REDIS_CONSUMER_NAME: z.string().min(1),
  VFQC_REDIS_EVENTS_STREAM: z.string().min(1).default('vfqc:runs:events'),

  VFQC_EVENT_TRANSPORT: EventTransportSchema.default('redis'),
  VFQC_WORK_DIR: z.string().min(1).default('./.runner-work'),
  VFQC_ARTIFACTS_DIR: z.string().min(1).default('./storage/artifacts'),

  VFQC_HEARTBEAT_INTERVAL_MS: positiveIntegerFromEnv(10_000),
  VFQC_CONTROL_POLL_INTERVAL_MS: positiveIntegerFromEnv(1_000),
  VFQC_COMMAND_BLOCK_MS: positiveIntegerFromEnv(5_000),
  VFQC_COMMAND_MAX_ATTEMPTS: positiveIntegerFromEnv(3),
  VFQC_PENDING_IDLE_THRESHOLD_MS: positiveIntegerFromEnv(60_000),
  VFQC_RECOVERY_INTERVAL_MS: positiveIntegerFromEnv(30_000),

  VFQC_TARGET_REQUEST_TIMEOUT_MS: positiveIntegerFromEnv(60_000),
  VFQC_LLM_JUDGE_TIMEOUT_MS: positiveIntegerFromEnv(120_000),
  VFQC_TESTCASE_TIMEOUT_MS: positiveIntegerFromEnv(180_000),
  VFQC_RUN_TIMEOUT_MS: positiveIntegerFromEnv(21_600_000),

  VFQC_LOG_LEVEL: LogLevelSchema.default('info'),
});

export type RunnerEnv = z.infer<typeof runnerEnvSchema>;
