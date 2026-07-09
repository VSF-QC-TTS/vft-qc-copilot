import type { BackendClient } from '../backend/backend-client.js';
import type { RunnerEventRequest } from '../backend/dto/runner-api.dto.js';
import type { HttpRunnerEventSinkResult, RunnerEventSink } from './runner-event-sink.js';

export class HttpRunnerEventSink implements RunnerEventSink {
  public constructor(private readonly backendClient: BackendClient) {}

  public async send(event: RunnerEventRequest): Promise<HttpRunnerEventSinkResult> {
    const response = await this.backendClient.sendEvent(event);
    return {
      transport: 'http',
      response,
    };
  }
}
