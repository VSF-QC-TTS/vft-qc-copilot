import pino, { type Logger } from 'pino';

import type { RunnerConfig } from '../config/runner-config.js';

export function createLogger(config: Pick<RunnerConfig, 'logLevel' | 'runnerKey' | 'version'>): Logger {
  return pino({
    level: config.logLevel,
    base: {
      runnerKey: config.runnerKey,
      runnerVersion: config.version,
    },
    redact: {
      paths: [
        'Authorization',
        'authorization',
        'Cookie',
        'cookie',
        '*.password',
        '*.secret',
        '*.token',
        '*.apiKey',
        '*.accessToken',
        '*.refreshToken',
      ],
      censor: '****MASKED****',
    },
  });
}
