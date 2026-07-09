import { describe, expect, it } from 'vitest';

import { loadRunnerConfig } from '../../../src/config/runner-config.js';

describe('loadRunnerConfig', () => {
  it('parses a minimal local runner config with redis event transport defaults', () => {
    const env = {
      VFQC_RUNNER_KEY: 'runner-local-01',
      VFQC_BACKEND_BASE_URL: 'http://localhost:8080',
      VFQC_RUNNER_TOKEN: 'dev-runner-token',
      VFQC_REDIS_CONSUMER_NAME: 'runner-local-01',
    };

    const config = loadRunnerConfig(env);

    expect(config).toMatchObject({
      runnerKey: 'runner-local-01',
      backendBaseUrl: 'http://localhost:8080',
      eventTransport: 'redis',
      commandStream: 'vfqc:runs:commands',
      eventsStream: 'vfqc:runs:events',
    });
  });
});
