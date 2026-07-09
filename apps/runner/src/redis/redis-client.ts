export interface RedisLifecycle {
  connect(): Promise<void>;
  disconnect(): Promise<void>;
}
