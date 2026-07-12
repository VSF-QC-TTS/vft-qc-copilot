import { z } from 'zod';

import { ApiContractError, normalizeErrorResponse } from '@/shared/api/api-error';
import type { CsrfTokenManager } from '@/shared/api/csrf';

const refreshResponseSchema = z.object({
  accessToken: z.string().min(1),
  tokenType: z.string(),
  expiresIn: z.number().int().positive(),
});

interface CreateRefreshAccessTokenOptions {
  baseUrl: string;
  csrf: CsrfTokenManager;
  fetch?: typeof fetch;
}

export function createRefreshAccessToken({
  baseUrl,
  csrf,
  fetch: fetchImplementation = globalThis.fetch.bind(globalThis),
}: CreateRefreshAccessTokenOptions): () => Promise<string> {
  return async () => {
    const csrfToken = await csrf.getToken();
    const response = await fetchImplementation(`${baseUrl}/api/v1/auth/refresh`, {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'X-XSRF-TOKEN': csrfToken,
      },
      method: 'POST',
    });

    if (!response.ok) {
      throw await normalizeErrorResponse(response);
    }

    let json: unknown;
    try {
      json = JSON.parse(await response.text());
    } catch (error) {
      throw new ApiContractError('Refresh response is not valid JSON', { cause: error });
    }

    const result = refreshResponseSchema.safeParse(json);
    if (!result.success) {
      throw new ApiContractError('Refresh response does not match its contract', {
        cause: result.error,
      });
    }

    return result.data.accessToken;
  };
}
