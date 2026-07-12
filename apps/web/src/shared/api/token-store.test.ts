import { describe, expect, it } from 'vitest';

import { createTokenStore } from '@/shared/api/token-store';

describe('tokenStore', () => {
  it('keeps the access token in memory and clears it', () => {
    const tokenStore = createTokenStore();

    tokenStore.setAccessToken('access-token');

    expect(tokenStore.getAccessToken()).toBe('access-token');

    tokenStore.clear();

    expect(tokenStore.getAccessToken()).toBeUndefined();
  });
});
