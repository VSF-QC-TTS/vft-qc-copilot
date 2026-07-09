import type { RunnerCommandMessage } from '../redis/command-message.js';

export interface RunnerExecutionContext {
  readonly command: RunnerCommandMessage;
  readonly startedAt: string;
}
