import type { RunnerEventRequest } from '../backend/dto/runner-api.dto.js';
import { RunnerEventRequestSchema } from '../backend/dto/runner-api.dto.js';
import { DefaultMaskingPolicy } from '../masking/masking-policy.js';
import { RecursiveSensitiveDataMasker } from '../masking/sensitive-data-masker.js';

export interface RunnerEventSanitizer {
  sanitize(event: RunnerEventRequest, knownSecrets: readonly string[]): RunnerEventRequest;
}

export class DefaultRunnerEventSanitizer implements RunnerEventSanitizer {
  public sanitize(event: RunnerEventRequest, knownSecrets: readonly string[]): RunnerEventRequest {
    const masker = new RecursiveSensitiveDataMasker(new DefaultMaskingPolicy({ knownSecrets }));
    return RunnerEventRequestSchema.parse({
      ...event,
      payload: masker.mask(event.payload),
    });
  }
}
