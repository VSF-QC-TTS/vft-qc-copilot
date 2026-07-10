import { describe, expect, it } from 'vitest';

import {
  HttpBackendClient,
  type BackendFetch,
  type BackendHttpRequestInit,
  type BackendHttpResponse,
} from '../../../src/backend/backend-client.js';
import { isRetryableBackendError } from '../../../src/errors/error-classifier.js';
import { RunnerError } from '../../../src/errors/runner-error.js';

const testRunId = '27a47393-169d-4f01-92a0-59cf2dc10f8a';

interface RecordedRequest {
  readonly url: URL;
  readonly init: BackendHttpRequestInit;
}

const config = {
  backendBaseUrl: 'https://api.example.test',
  runnerToken: 'runner-token',
};

const instanceResponse = {
  id: 'c1350b87-c35c-40b2-bbb1-b6f8815720fd',
  runnerKey: 'runner-local',
  hostname: 'runner-host',
  version: '0.0.0',
  status: 'IDLE',
  currentCommandId: null,
  currentTestRunId: null,
  lastHeartbeatAt: null,
  registeredAt: '2026-07-09T00:00:00Z',
  updatedAt: '2026-07-09T00:00:00Z',
};

const configResponse = {
  testRunId,
  projectId: 'f559d1e9-0c5f-4ce3-8906-30a15bb02c7a',
  runType: 'SINGLE_TARGET',
  status: 'RUNNING',
  datasetId: 'f538afc3-f5df-4a5e-9d20-86e76e6999be',
  datasetVersionId: '89737237-f57c-4411-a013-1566748dc6d7',
  datasetSchemaId: '068283ff-7567-4b55-af35-f15d6e454c2f',
  datasetSchemaVersion: 1,
  progressTotal: 0,
  page: 2,
  size: 50,
  hasNext: false,
  domainSnapshot: {},
  promptfooSnapshot: {},
  targets: [],
  evaluators: [],
  rows: [],
};

describe('HttpBackendClient', () => {
  it('sends the runner token as a bearer auth header', async () => {
    const { client, requests } = createClient(jsonResponse(instanceResponse));

    await client.registerInstance({ runnerKey: 'runner-local', hostname: 'runner-host', version: '0.0.0' });

    const request = firstRequest(requests);
    expect(request.url.pathname).toBe('/internal/runner/instances/register');
    expect(request.init.method).toBe('POST');
    expect(request.init.headers.Authorization).toBe('Bearer runner-token');
    expect(request.init.headers.Accept).toBe('application/json');
    expect(request.init.body).toBe(
      JSON.stringify({ runnerKey: 'runner-local', hostname: 'runner-host', version: '0.0.0' }),
    );
  });

  it('fetches paged test run config and validates the response', async () => {
    const { client, requests } = createClient(jsonResponse(configResponse));

    const response = await client.getTestRunConfig(testRunId, 2, 50);

    const request = firstRequest(requests);
    expect(response.testRunId).toBe(testRunId);
    expect(request.url.pathname).toBe(`/internal/runner/test-runs/${testRunId}/config`);
    expect(request.url.searchParams.get('page')).toBe('2');
    expect(request.url.searchParams.get('size')).toBe('50');
    expect(request.init.body).toBeUndefined();
  });

  it('throws a sanitized validation error for invalid backend responses', async () => {
    const invalidResponse = { ...configResponse, status: 'PENDING' };
    const { client } = createClient(jsonResponse(invalidResponse));

    await expect(client.getTestRunConfig(testRunId, 0, 100)).rejects.toMatchObject({
      category: 'backend.validation',
      retryable: false,
    });
  });

  it('does not expose runtime secret response bodies in HTTP errors', async () => {
    const response = {
      ok: false,
      status: 500,
      json: async () => ({ secret: 'super-secret-value' }),
    };
    const { client } = createClient(response);

    try {
      await client.resolveRuntimeSecrets(testRunId, { secretIds: ['7e1e7b32-6875-4308-88d8-aa56b18ff7e8'] });
      throw new Error('Expected request to fail');
    } catch (error) {
      expect(error).toBeInstanceOf(RunnerError);
      if (!(error instanceof RunnerError)) {
        throw error;
      }
      expect(error.message).toContain('status=500');
      expect(error.message).not.toContain('super-secret-value');
    }
  });

  it('classifies 401 and 403 backend errors as non-retryable', async () => {
    const unauthorized = await captureRunnerError(jsonResponse({ message: 'Unauthorized' }, 401));
    const forbidden = await captureRunnerError(jsonResponse({ message: 'Forbidden' }, 403));

    expect(isRetryableBackendError(unauthorized)).toBe(false);
    expect(isRetryableBackendError(forbidden)).toBe(false);
  });

  it('classifies 5xx and network errors as retryable', async () => {
    const serverError = await captureRunnerError(jsonResponse({ message: 'Unavailable' }, 503));
    const networkClient = new HttpBackendClient(config, async () => {
      throw new TypeError('fetch failed');
    });

    await expect(networkClient.getControlState(testRunId)).rejects.toMatchObject({
      category: 'backend.network',
      retryable: true,
    });
    expect(isRetryableBackendError(serverError)).toBe(true);
  });
});

function createClient(response: BackendHttpResponse): { readonly client: HttpBackendClient; readonly requests: RecordedRequest[] } {
  const requests: RecordedRequest[] = [];
  const backendFetch: BackendFetch = async (url, init) => {
    requests.push({ url, init });
    return response;
  };

  return {
    client: new HttpBackendClient(config, backendFetch),
    requests,
  };
}

function jsonResponse(payload: unknown, status = 200): BackendHttpResponse {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => payload,
  };
}

function firstRequest(requests: readonly RecordedRequest[]): RecordedRequest {
  const request = requests[0];
  if (request === undefined) {
    throw new Error('Expected at least one request');
  }
  return request;
}

async function captureRunnerError(response: BackendHttpResponse): Promise<RunnerError> {
  const { client } = createClient(response);
  try {
    await client.getControlState(testRunId);
    throw new Error('Expected request to fail');
  } catch (error) {
    if (error instanceof RunnerError) {
      return error;
    }
    throw error;
  }
}
