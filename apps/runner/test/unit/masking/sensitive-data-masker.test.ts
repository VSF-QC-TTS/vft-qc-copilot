import { describe, expect, it } from 'vitest';

import { DefaultMaskingPolicy } from '../../../src/masking/masking-policy.js';
import { RecursiveSensitiveDataMasker } from '../../../src/masking/sensitive-data-masker.js';

const fakeSecret = 'vfqc_secret_1234567890abcdef';

describe('RecursiveSensitiveDataMasker', () => {
  it('removes known fake secret strings from sanitized output', () => {
    const masker = createMasker([fakeSecret]);

    const masked = masker.mask({ promptfoo: { config: `token=${fakeSecret}` } });

    expect(JSON.stringify(masked)).not.toContain(fakeSecret);
  });

  it('masks Authorization, Cookie, and X-API-Key fields', () => {
    const masker = createMasker();

    const masked = masker.mask({
      Authorization: 'Bearer token-123',
      Cookie: 'session=secret',
      'X-API-Key': 'api-key-123',
    });

    expect(masked).toEqual({
      Authorization: 'Bearer ****MASKED****',
      Cookie: '****MASKED****',
      'X-API-Key': '****MASKED****',
    });
  });

  it('masks nested token, password, and secret fields', () => {
    const masker = createMasker();

    const masked = masker.mask({
      target: {
        headers: {
          access_token: 'access-token-value',
          password: 'password-value',
          client_secret: 'client-secret-value',
        },
      },
    });

    expect(masked).toEqual({
      target: {
        headers: {
          access_token: '****MASKED****',
          password: '****MASKED****',
          client_secret: '****MASKED****',
        },
      },
    });
  });

  it('masks raw cURL Authorization headers embedded in text', () => {
    const masker = createMasker();

    const masked = masker.mask("curl -H 'Authorization: Bearer raw-token-value' https://example.test");

    expect(masked).toBe("curl -H 'Authorization: Bearer ****MASKED****' https://example.test");
  });

  it('masks accidental promptfoo secret values', () => {
    const masker = createMasker([fakeSecret]);

    const masked = masker.mask({
      providers: [
        {
          id: 'openai:gpt-4.1-mini',
          config: {
            apiKey: fakeSecret,
            headers: { Authorization: `Bearer ${fakeSecret}` },
          },
        },
      ],
    });

    const serialized = JSON.stringify(masked);
    expect(serialized).not.toContain(fakeSecret);
    expect(serialized).toContain('****MASKED****');
  });
});

function createMasker(knownSecrets: readonly string[] = []): RecursiveSensitiveDataMasker {
  return new RecursiveSensitiveDataMasker(new DefaultMaskingPolicy({ knownSecrets }));
}
