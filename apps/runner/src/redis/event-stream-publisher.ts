import type { RunnerEventRequest } from '../backend/dto/runner-api.dto.js';

export interface EventStreamPublisher {
  publish(event: RunnerEventRequest): Promise<string>;
}
