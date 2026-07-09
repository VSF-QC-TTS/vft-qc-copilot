import { RunnerError } from './runner-error.js';

export interface ErrorClassifier {
  isRetryable(error: unknown): boolean;
}

export function isRetryableBackendError(error: unknown): boolean {
  if (error instanceof RunnerError && error.retryable !== undefined) {
    return error.retryable;
  }

  return error instanceof TypeError;
}

export class BackendErrorClassifier implements ErrorClassifier {
  public isRetryable(error: unknown): boolean {
    return isRetryableBackendError(error);
  }
}
