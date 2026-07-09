import type { RunnerCommandMessage } from '../redis/command-message.js';

export interface RunnerService {
  handleCommand(command: RunnerCommandMessage): Promise<void>;
}
