import { describe, expect, it } from 'vitest';

import { shouldRetryQuery } from '@/app/providers/query-policy';
import { ApiError } from '@/shared/api/api-error';

describe('query policy', () => {
  it('does not retry auth, permission, or validation errors', () => {
    expect(shouldRetryQuery(0, new ApiError({ status: 401 }))).toBe(false);
    expect(shouldRetryQuery(0, new ApiError({ status: 403 }))).toBe(false);
    expect(shouldRetryQuery(0, new ApiError({ status: 422 }))).toBe(false);
  });

  it('retries transient errors once', () => {
    expect(shouldRetryQuery(0, new ApiError({ status: 0, networkError: true }))).toBe(true);
    expect(shouldRetryQuery(1, new ApiError({ status: 500 }))).toBe(true);
    expect(shouldRetryQuery(2, new ApiError({ status: 500 }))).toBe(false);
  });
});
