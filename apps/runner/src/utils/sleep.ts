import { RunnerError } from '../errors/runner-error.js';

export function sleep(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    if (signal?.aborted === true) {
      resolve();
      return;
    }
    const timeout = setTimeout(resolve, ms);
    signal?.addEventListener(
      'abort',
      () => {
        clearTimeout(timeout);
        resolve();
      },
      { once: true },
    );
  });
}

export interface TimeoutOptions {
  readonly ms: number;
  readonly message: string;
  readonly category: string;
}

export async function withTimeout<TValue>(operation: Promise<TValue>, options: TimeoutOptions): Promise<TValue> {
  if (options.ms <= 0) {
    return operation;
  }

  let timeoutHandle: ReturnType<typeof setTimeout> | undefined;
  const timeout = new Promise<TValue>((_resolve, reject) => {
    timeoutHandle = setTimeout(() => {
      reject(
        new RunnerError(options.message, {
          category: options.category,
          retryable: false,
        }),
      );
    }, options.ms);
  });

  try {
    return await Promise.race([operation, timeout]);
  } finally {
    if (timeoutHandle !== undefined) {
      clearTimeout(timeoutHandle);
    }
  }
}
