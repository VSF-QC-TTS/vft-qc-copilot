export interface HeartbeatService {
  start(): void;
  stop(): Promise<void>;
}
