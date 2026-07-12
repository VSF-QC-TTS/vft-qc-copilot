import { describe, expect, it } from 'vitest';

import { apiClient, accessTokenStore } from '@/shared/api/api';

describe('shared API runtime', () => {
  it('exposes one client and one in-memory token store for feature APIs', () => {
    expect(Object.keys(apiClient).sort()).toEqual(['requestBlob', 'requestJson', 'requestVoid']);
    expect(accessTokenStore.getAccessToken()).toBeUndefined();
  });
});
