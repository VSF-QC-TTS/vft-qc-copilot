import type { RunnerCommandDelivery } from './command-message.js';

export interface PendingRecovery {
  claimStaleCommands(): Promise<readonly RunnerCommandDelivery[]>;
}
