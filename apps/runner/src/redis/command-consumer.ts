import type { RunnerCommandDelivery } from './command-message.js';

export interface CommandConsumer {
  readNextCommand(): Promise<RunnerCommandDelivery | null>;
  ackCommand(streamMessageId: string): Promise<void>;
}
