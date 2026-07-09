import type { RunnerEventRequest } from '../backend/dto/runner-api.dto.js';

export interface RunnerEventFactory {
  createRunStarted(testRunId: string, commandId: string): RunnerEventRequest;
}
