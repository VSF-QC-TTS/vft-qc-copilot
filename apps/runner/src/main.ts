import { config as loadDotenv } from 'dotenv';

import { loadRunnerConfig } from './config/runner-config.js';
import { createLogger } from './logging/logger.js';

export interface BootstrapResult {
  readonly runnerKey: string;
  readonly eventTransport: 'redis' | 'http';
}

export function bootstrap(env: NodeJS.ProcessEnv = process.env): BootstrapResult {
  loadDotenv();
  const config = loadRunnerConfig(env);
  const logger = createLogger(config);
  logger.info({ eventTransport: config.eventTransport }, 'Runner bootstrap complete');
  return {
    runnerKey: config.runnerKey,
    eventTransport: config.eventTransport,
  };
}

export async function main(): Promise<void> {
  bootstrap();
}

if (process.env.NODE_ENV !== 'test') {
  await main();
}
