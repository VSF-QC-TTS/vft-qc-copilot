import type { RunnerCommandDelivery } from './command-message.js';

export interface PendingRecovery {
  claimStaleCommands(): Promise<readonly RunnerCommandDelivery[]>;
}

export class NoopPendingRecovery implements PendingRecovery {
  public async claimStaleCommands(): Promise<readonly RunnerCommandDelivery[]> {
    return [];
  }
}
