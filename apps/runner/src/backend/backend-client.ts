import type {
  RegisterRunnerInstanceRequest,
  RunnerControlStateResponse,
  RunnerEventIngestionResponse,
  RunnerEventRequest,
  RunnerHeartbeatRequest,
  RunnerInstanceResponse,
  RunnerRuntimeSecretsRequest,
  RunnerRuntimeSecretsResponse,
  RunnerTestRunConfigResponse,
} from './dto/runner-api.dto.js';
import {
  RunnerControlStateResponseSchema,
  RunnerEventIngestionResponseSchema,
  RunnerInstanceResponseSchema,
  RunnerRuntimeSecretsResponseSchema,
  RunnerTestRunConfigResponseSchema,
} from './dto/runner-api.dto.js';
import type { RunnerConfig } from '../config/runner-config.js';
import { RunnerError } from '../errors/runner-error.js';

export interface BackendClient {
  registerInstance(request: RegisterRunnerInstanceRequest): Promise<RunnerInstanceResponse>;
  heartbeat(request: RunnerHeartbeatRequest): Promise<RunnerInstanceResponse>;
  getTestRunConfig(testRunId: string, page: number, size: number): Promise<RunnerTestRunConfigResponse>;
  resolveRuntimeSecrets(
    testRunId: string,
    request: RunnerRuntimeSecretsRequest,
  ): Promise<RunnerRuntimeSecretsResponse>;
  getControlState(testRunId: string): Promise<RunnerControlStateResponse>;
  sendEvent(event: RunnerEventRequest): Promise<RunnerEventIngestionResponse>;
}

export interface BackendHttpResponse {
  readonly ok: boolean;
  readonly status: number;
  json(): Promise<unknown>;
}

export interface BackendHttpRequestInit {
  readonly method: 'GET' | 'POST';
  readonly headers: Record<string, string>;
  readonly body?: string;
}

export type BackendFetch = (url: URL, init: BackendHttpRequestInit) => Promise<BackendHttpResponse>;

interface ResponseSchema<TResponse> {
  parse(value: unknown): TResponse;
}

export class HttpBackendClient implements BackendClient {
  private readonly backendBaseUrl: string;
  private readonly runnerToken: string;
  private readonly backendFetch: BackendFetch;

  public constructor(config: Pick<RunnerConfig, 'backendBaseUrl' | 'runnerToken'>, backendFetch: BackendFetch = defaultFetch) {
    this.backendBaseUrl = config.backendBaseUrl;
    this.runnerToken = config.runnerToken;
    this.backendFetch = backendFetch;
  }

  public registerInstance(request: RegisterRunnerInstanceRequest): Promise<RunnerInstanceResponse> {
    return this.requestJson('POST', '/internal/runner/instances/register', RunnerInstanceResponseSchema, request);
  }

  public heartbeat(request: RunnerHeartbeatRequest): Promise<RunnerInstanceResponse> {
    return this.requestJson('POST', '/internal/runner/instances/heartbeat', RunnerInstanceResponseSchema, request);
  }

  public getTestRunConfig(testRunId: string, page: number, size: number): Promise<RunnerTestRunConfigResponse> {
    return this.requestJson(
      'GET',
      `/internal/runner/test-runs/${encodeURIComponent(testRunId)}/config`,
      RunnerTestRunConfigResponseSchema,
      undefined,
      { page: String(page), size: String(size) },
    );
  }

  public resolveRuntimeSecrets(
    testRunId: string,
    request: RunnerRuntimeSecretsRequest,
  ): Promise<RunnerRuntimeSecretsResponse> {
    return this.requestJson(
      'POST',
      `/internal/runner/test-runs/${encodeURIComponent(testRunId)}/runtime-secrets`,
      RunnerRuntimeSecretsResponseSchema,
      request,
    );
  }

  public getControlState(testRunId: string): Promise<RunnerControlStateResponse> {
    return this.requestJson(
      'GET',
      `/internal/runner/test-runs/${encodeURIComponent(testRunId)}/control-state`,
      RunnerControlStateResponseSchema,
    );
  }

  public sendEvent(event: RunnerEventRequest): Promise<RunnerEventIngestionResponse> {
    return this.requestJson('POST', '/internal/runner/events', RunnerEventIngestionResponseSchema, event);
  }

  private async requestJson<TResponse>(
    method: BackendHttpRequestInit['method'],
    path: string,
    schema: ResponseSchema<TResponse>,
    body?: unknown,
    query?: Record<string, string>,
  ): Promise<TResponse> {
    const url = this.buildUrl(path, query);
    const request = this.buildRequest(method, body);
    let response: BackendHttpResponse;

    try {
      response = await this.backendFetch(url, request);
    } catch (error) {
      throw new RunnerError(`Backend request failed: ${method} ${path}`, {
        category: 'backend.network',
        retryable: true,
        cause: error,
      });
    }

    if (!response.ok) {
      throw new RunnerError(`Backend request failed: ${method} ${path} status=${response.status}`, {
        category: 'backend.http',
        retryable: isRetryableStatus(response.status),
        status: response.status,
      });
    }

    const payload = await response.json();
    try {
      return schema.parse(payload);
    } catch (error) {
      throw new RunnerError(`Backend response validation failed: ${method} ${path}`, {
        category: 'backend.validation',
        retryable: false,
        cause: error,
      });
    }
  }

  private buildUrl(path: string, query?: Record<string, string>): URL {
    const url = new URL(path, this.backendBaseUrl);
    for (const [key, value] of Object.entries(query ?? {})) {
      url.searchParams.set(key, value);
    }
    return url;
  }

  private buildRequest(method: BackendHttpRequestInit['method'], body?: unknown): BackendHttpRequestInit {
    const headers = {
      Accept: 'application/json',
      Authorization: `Bearer ${this.runnerToken}`,
      'Content-Type': 'application/json',
    };

    if (body === undefined) {
      return { method, headers };
    }

    return { method, headers, body: JSON.stringify(body) };
  }
}

export function createBackendClient(config: Pick<RunnerConfig, 'backendBaseUrl' | 'runnerToken'>): BackendClient {
  return new HttpBackendClient(config);
}

function isRetryableStatus(status: number): boolean {
  return status === 408 || status === 429 || status >= 500;
}

const defaultFetch: BackendFetch = async (url, init) => {
  const response = await fetch(url, init);
  return {
    ok: response.ok,
    status: response.status,
    json: () => response.json() as Promise<unknown>,
  };
};
