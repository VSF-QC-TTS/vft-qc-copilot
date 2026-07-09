export interface RunnerErrorOptions {
  readonly category: string;
  readonly retryable?: boolean;
  readonly status?: number;
  readonly cause?: unknown;
}

export class RunnerError extends Error {
  public readonly category: string;
  public readonly retryable?: boolean;
  public readonly status?: number;

  public constructor(
    message: string,
    options: RunnerErrorOptions,
  ) {
    super(message, options.cause === undefined ? undefined : { cause: options.cause });
    this.name = 'RunnerError';
    this.category = options.category;
    if (options.retryable !== undefined) {
      this.retryable = options.retryable;
    }
    if (options.status !== undefined) {
      this.status = options.status;
    }
  }
}
