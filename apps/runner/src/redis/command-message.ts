import type { RunnerCommandType } from '../backend/dto/runner-api.dto.js';

export interface RunnerCommandMessage {
  readonly commandId: string;
  readonly testRunId: string;
  readonly commandType: RunnerCommandType;
  readonly createdAt: string;
}

export interface RunnerCommandDelivery {
  readonly streamMessageId: string;
  readonly message: RunnerCommandMessage;
}
