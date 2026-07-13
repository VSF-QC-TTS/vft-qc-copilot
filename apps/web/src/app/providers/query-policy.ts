import { isApiError } from '@/shared/api/api-error';

const MAX_QUERY_RETRIES = 1;

export function shouldRetryQuery(failureCount: number, error: unknown): boolean {
  if (failureCount > MAX_QUERY_RETRIES) {
    return false;
  }

  if (!isApiError(error)) {
    return failureCount <= MAX_QUERY_RETRIES;
  }

  if (error.authenticationError || error.permissionError) {
    return false;
  }

  if (error.status >= 400 && error.status < 500) {
    return false;
  }

  return error.networkError || error.status >= 500;
}
