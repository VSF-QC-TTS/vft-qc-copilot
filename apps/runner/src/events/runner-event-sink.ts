import type { RunnerEventIngestionResponse, RunnerEventRequest } from '../backend/dto/runner-api.dto.js';

export interface RunnerEventSink {
  send(event: RunnerEventRequest): Promise<RunnerEventIngestionResponse>;
}
