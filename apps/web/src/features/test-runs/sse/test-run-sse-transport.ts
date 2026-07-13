import { normalizeErrorResponse } from '@/shared/api/api-error';
import { createSseStreamParser } from '@/features/test-runs/sse/sse-parser';
import {
  parseTestRunSseEvent,
  type InvalidTestRunSseEvent,
  type ParsedTestRunSseEvent,
  type ParsedSseEvent,
} from '@/features/test-runs/sse/test-run-sse.types';
import {
  createTestRunSseSequenceState,
  reduceTestRunSseSequence,
  type TestRunSseSequenceDecision,
} from '@/features/test-runs/sse/test-run-sse-sequence';

const DEFAULT_MAX_RECONNECT_ATTEMPTS = 5;

export type TestRunSseConnectionState = 'connecting' | 'open' | 'reconnecting' | 'closed' | 'error';

export type TestRunSseStopReason =
  | 'terminal'
  | 'aborted'
  | 'forbidden'
  | 'not-found'
  | 'authentication-failed'
  | 'replay-unavailable'
  | 'sequence-gap'
  | 'invalid-event'
  | 'max-reconnects'
  | 'stream-unavailable';

export interface TestRunSseTransportResult {
  reason: TestRunSseStopReason;
  lastSequenceNo: number;
}

export interface TestRunSseTransportOptions {
  baseUrl: string;
  testRunId: string;
  getAccessToken: () => string | undefined;
  refreshAccessToken?: () => Promise<string>;
  fetch?: typeof fetch;
  signal?: AbortSignal;
  initialLastSequenceNo?: number;
  useReplayQueryParameter?: boolean;
  maxReconnectAttempts?: number;
  reconnectDelay?: (attempt: number) => number;
  sleep?: (ms: number, signal?: AbortSignal) => Promise<void>;
  onConnectionStateChange?: (state: TestRunSseConnectionState) => void;
  onHeartbeat?: (comment: string) => void;
  onEvent?: (event: ParsedTestRunSseEvent) => void;
  onDuplicate?: (event: ParsedTestRunSseEvent) => void;
  onGap?: (expectedSequenceNo: number, actualSequenceNo: number) => void;
  onInvalidEvent?: (event: InvalidTestRunSseEvent) => void;
  onError?: (error: unknown) => void;
}

export async function connectTestRunSse(
  options: TestRunSseTransportOptions,
): Promise<TestRunSseTransportResult> {
  const fetchImplementation = options.fetch ?? globalThis.fetch.bind(globalThis);
  const maxReconnectAttempts = options.maxReconnectAttempts ?? DEFAULT_MAX_RECONNECT_ATTEMPTS;
  const reconnectDelay = options.reconnectDelay ?? createSseReconnectDelay;
  const sleep = options.sleep ?? waitForReconnect;
  let state = createTestRunSseSequenceState(options.testRunId, options.initialLastSequenceNo ?? 0);
  let refreshedAccessToken: string | undefined;
  let authenticationRetried = false;

  async function runConnectionAttempt(
    reconnectAttempt: number,
  ): Promise<TestRunSseTransportResult> {
    if (options.signal?.aborted) {
      options.onConnectionStateChange?.('closed');
      return { reason: 'aborted', lastSequenceNo: state.lastAppliedSequenceNo };
    }

    options.onConnectionStateChange?.(reconnectAttempt === 0 ? 'connecting' : 'reconnecting');

    const response = await fetchSse({
      baseUrl: options.baseUrl,
      testRunId: options.testRunId,
      lastSequenceNo: state.lastAppliedSequenceNo,
      accessToken: refreshedAccessToken ?? options.getAccessToken(),
      useReplayQueryParameter: options.useReplayQueryParameter ?? false,
      signal: options.signal,
      fetch: fetchImplementation,
    }).catch((error: unknown) => {
      if (isAbortError(error) || options.signal?.aborted) {
        return undefined;
      }
      options.onError?.(error);
      return null;
    });

    if (response === undefined || options.signal?.aborted) {
      options.onConnectionStateChange?.('closed');
      return { reason: 'aborted', lastSequenceNo: state.lastAppliedSequenceNo };
    }

    if (response === null) {
      const reconnectResult = await reconnectOrStop({
        attempt: reconnectAttempt + 1,
        maxReconnectAttempts,
        reconnectDelay,
        sleep,
        signal: options.signal,
        ...(options.onConnectionStateChange
          ? { onConnectionStateChange: options.onConnectionStateChange }
          : {}),
      });
      if (!reconnectResult.reconnect) {
        return { reason: reconnectResult.reason, lastSequenceNo: state.lastAppliedSequenceNo };
      }
      return runConnectionAttempt(reconnectResult.attempt);
    }

    if (response.status === 401 && options.refreshAccessToken && !authenticationRetried) {
      authenticationRetried = true;
      try {
        refreshedAccessToken = await options.refreshAccessToken();
      } catch (error) {
        options.onError?.(error);
        options.onConnectionStateChange?.('error');
        return { reason: 'authentication-failed', lastSequenceNo: state.lastAppliedSequenceNo };
      }
      return runConnectionAttempt(reconnectAttempt);
    }

    if (response.status === 401) {
      options.onConnectionStateChange?.('error');
      return { reason: 'authentication-failed', lastSequenceNo: state.lastAppliedSequenceNo };
    }

    if (response.status === 403) {
      options.onConnectionStateChange?.('error');
      return { reason: 'forbidden', lastSequenceNo: state.lastAppliedSequenceNo };
    }

    if (response.status === 404) {
      options.onConnectionStateChange?.('error');
      return { reason: 'not-found', lastSequenceNo: state.lastAppliedSequenceNo };
    }

    if (response.status === 409) {
      await normalizeErrorResponse(response);
      options.onConnectionStateChange?.('error');
      return { reason: 'replay-unavailable', lastSequenceNo: state.lastAppliedSequenceNo };
    }

    if (!response.ok) {
      options.onError?.(await normalizeErrorResponse(response));
      const reconnectResult = await reconnectOrStop({
        attempt: reconnectAttempt + 1,
        maxReconnectAttempts,
        reconnectDelay,
        sleep,
        signal: options.signal,
        ...(options.onConnectionStateChange
          ? { onConnectionStateChange: options.onConnectionStateChange }
          : {}),
      });
      if (!reconnectResult.reconnect) {
        return { reason: reconnectResult.reason, lastSequenceNo: state.lastAppliedSequenceNo };
      }
      return runConnectionAttempt(reconnectResult.attempt);
    }

    authenticationRetried = false;
    options.onConnectionStateChange?.('open');

    const streamResult = await readSseResponse(response, {
      ...(options.onHeartbeat ? { onHeartbeat: options.onHeartbeat } : {}),
      onRawEvent(rawEvent) {
        const parsed = parseTestRunSseEvent(rawEvent);
        if (!parsed.ok) {
          options.onInvalidEvent?.(parsed.invalidEvent);
          return { terminal: true, reason: 'invalid-event' };
        }

        const decision = reduceTestRunSseSequence(state, parsed.event);
        const handlingResult = handleSequenceDecision(decision, options);
        state = handlingResult.state;
        return handlingResult;
      },
    });

    if (streamResult.terminal) {
      options.onConnectionStateChange?.('closed');
      return { reason: streamResult.reason, lastSequenceNo: state.lastAppliedSequenceNo };
    }

    if (options.signal?.aborted) {
      options.onConnectionStateChange?.('closed');
      return { reason: 'aborted', lastSequenceNo: state.lastAppliedSequenceNo };
    }

    const reconnectResult = await reconnectOrStop({
      attempt: reconnectAttempt + 1,
      maxReconnectAttempts,
      reconnectDelay,
      sleep,
      signal: options.signal,
      ...(options.onConnectionStateChange
        ? { onConnectionStateChange: options.onConnectionStateChange }
        : {}),
    });
    if (!reconnectResult.reconnect) {
      return { reason: reconnectResult.reason, lastSequenceNo: state.lastAppliedSequenceNo };
    }
    return runConnectionAttempt(reconnectResult.attempt);
  }

  return runConnectionAttempt(0);
}

export function createTestRunSseUrl(
  baseUrl: string,
  testRunId: string,
  lastSequenceNo: number,
  useReplayQueryParameter: boolean,
): string {
  const url = new URL(`/api/v1/test-runs/${testRunId}/events`, baseUrl);
  if (useReplayQueryParameter && lastSequenceNo > 0) {
    url.searchParams.set('afterSequenceNo', String(lastSequenceNo));
  }
  return url.toString();
}

export function createSseReconnectDelay(attempt: number): number {
  const cappedAttempt = Math.max(1, Math.min(attempt, 6));
  return Math.min(30_000, 500 * 2 ** (cappedAttempt - 1));
}

async function fetchSse({
  baseUrl,
  testRunId,
  lastSequenceNo,
  accessToken,
  useReplayQueryParameter,
  signal,
  fetch: fetchImplementation,
}: {
  baseUrl: string;
  testRunId: string;
  lastSequenceNo: number;
  accessToken: string | undefined;
  useReplayQueryParameter: boolean;
  signal: AbortSignal | undefined;
  fetch: typeof fetch;
}): Promise<Response> {
  const headers = new Headers({
    Accept: 'text/event-stream',
    'Cache-Control': 'no-cache',
  });

  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  }

  if (!useReplayQueryParameter && lastSequenceNo > 0) {
    headers.set('Last-Event-ID', String(lastSequenceNo));
  }

  return fetchImplementation(
    createTestRunSseUrl(baseUrl, testRunId, lastSequenceNo, useReplayQueryParameter),
    {
      credentials: 'include',
      headers,
      method: 'GET',
      ...(signal ? { signal } : {}),
    },
  );
}

async function readSseResponse(
  response: Response,
  handlers: {
    onHeartbeat?: (comment: string) => void;
    onRawEvent: (event: ParsedSseEvent) => StreamEventHandlingResult;
  },
): Promise<StreamEventHandlingResult> {
  if (!response.body) {
    return { terminal: false, reason: 'stream-unavailable' };
  }

  let streamResult: StreamEventHandlingResult = { terminal: false, reason: 'stream-unavailable' };
  const reader = response.body.getReader();
  const parser = createSseStreamParser({
    ...(handlers.onHeartbeat ? { onComment: handlers.onHeartbeat } : {}),
    onEvent(rawEvent) {
      const eventResult = handlers.onRawEvent(rawEvent);
      if (eventResult.terminal) {
        streamResult = eventResult;
      }
    },
  });

  async function readNextChunk(): Promise<StreamEventHandlingResult> {
    if (streamResult.terminal) {
      await reader.cancel().catch(() => undefined);
      return streamResult;
    }

    const readResult = await reader.read();
    if (readResult.done) {
      parser.flush();
      if (streamResult.terminal) {
        await reader.cancel().catch(() => undefined);
      }
      return streamResult;
    }

    parser.feed(readResult.value);
    return readNextChunk();
  }

  return readNextChunk();
}

interface StreamEventHandlingResult {
  terminal: boolean;
  reason: TestRunSseStopReason;
}

function handleSequenceDecision(
  decision: TestRunSseSequenceDecision,
  options: TestRunSseTransportOptions,
): StreamEventHandlingResult & { state: TestRunSseSequenceDecision['state'] } {
  switch (decision.type) {
    case 'applied':
      options.onEvent?.(decision.event);
      return { terminal: false, reason: 'stream-unavailable', state: decision.state };
    case 'terminal':
      options.onEvent?.(decision.event);
      return { terminal: true, reason: 'terminal', state: decision.state };
    case 'duplicate':
      options.onDuplicate?.(decision.event);
      return { terminal: false, reason: 'stream-unavailable', state: decision.state };
    case 'gap':
      options.onGap?.(decision.expectedSequenceNo, decision.actualSequenceNo);
      return { terminal: true, reason: 'sequence-gap', state: decision.state };
    case 'ignored-wrong-run':
      return { terminal: false, reason: 'stream-unavailable', state: decision.state };
    default: {
      const exhaustive: never = decision;
      return exhaustive;
    }
  }
}

async function reconnectOrStop({
  attempt,
  maxReconnectAttempts,
  reconnectDelay,
  sleep,
  signal,
  onConnectionStateChange,
}: {
  attempt: number;
  maxReconnectAttempts: number;
  reconnectDelay: (attempt: number) => number;
  sleep: (ms: number, signal?: AbortSignal) => Promise<void>;
  signal: AbortSignal | undefined;
  onConnectionStateChange?: (state: TestRunSseConnectionState) => void;
}): Promise<
  { reconnect: true; attempt: number } | { reconnect: false; reason: 'aborted' | 'max-reconnects' }
> {
  if (signal?.aborted) {
    onConnectionStateChange?.('closed');
    return { reconnect: false, reason: 'aborted' };
  }

  if (attempt > maxReconnectAttempts) {
    onConnectionStateChange?.('error');
    return { reconnect: false, reason: 'max-reconnects' };
  }

  onConnectionStateChange?.('reconnecting');
  await sleep(reconnectDelay(attempt), signal);
  if (signal?.aborted) {
    onConnectionStateChange?.('closed');
    return { reconnect: false, reason: 'aborted' };
  }

  return { reconnect: true, attempt };
}

function waitForReconnect(ms: number, signal?: AbortSignal): Promise<void> {
  if (ms <= 0) {
    return Promise.resolve();
  }

  return new Promise((resolve) => {
    const timeout = window.setTimeout(resolve, ms);
    signal?.addEventListener(
      'abort',
      () => {
        window.clearTimeout(timeout);
        resolve();
      },
      { once: true },
    );
  });
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError';
}
