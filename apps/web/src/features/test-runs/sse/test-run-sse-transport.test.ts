import { describe, expect, it, vi } from 'vitest';

import {
  connectTestRunSse,
  createSseReconnectDelay,
} from '@/features/test-runs/sse/test-run-sse-transport';

const TEST_RUN_ID = '11111111-1111-4111-8111-111111111111';

describe('TestRun SSE transport', () => {
  it('uses fetch with Authorization and Last-Event-ID headers', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(streamResponse(frame(6, 'RUN_COMPLETED')));

    const result = await connectTestRunSse({
      baseUrl: 'http://localhost:8080',
      testRunId: TEST_RUN_ID,
      getAccessToken: () => 'access-token',
      fetch: fetchMock,
      initialLastSequenceNo: 5,
    });

    const requestInit = fetchMock.mock.calls[0]?.[1];
    const headers = new Headers(requestInit?.headers);
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      `http://localhost:8080/api/v1/test-runs/${TEST_RUN_ID}/events`,
    );
    expect(headers.get('Accept')).toBe('text/event-stream');
    expect(headers.get('Authorization')).toBe('Bearer access-token');
    expect(headers.get('Last-Event-ID')).toBe('5');
    expect(result).toEqual({ reason: 'terminal', lastSequenceNo: 6 });
  });

  it('can send replay cursor as afterSequenceNo fallback query parameter', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(streamResponse(frame(6, 'RUN_COMPLETED')));

    await connectTestRunSse({
      baseUrl: 'http://localhost:8080',
      testRunId: TEST_RUN_ID,
      getAccessToken: () => 'access-token',
      fetch: fetchMock,
      initialLastSequenceNo: 5,
      useReplayQueryParameter: true,
    });

    const requestInit = fetchMock.mock.calls[0]?.[1];
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      `http://localhost:8080/api/v1/test-runs/${TEST_RUN_ID}/events?afterSequenceNo=5`,
    );
    expect(new Headers(requestInit?.headers).get('Last-Event-ID')).toBeNull();
  });

  it('reconnects after a non-terminal network close from the latest applied sequence', async () => {
    const events: string[] = [];
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(streamResponse(frame(1, 'RUN_STARTED')))
      .mockResolvedValueOnce(streamResponse(frame(2, 'RUN_COMPLETED')));
    const sleep = vi.fn<() => Promise<void>>().mockResolvedValue(undefined);

    const result = await connectTestRunSse({
      baseUrl: 'http://localhost:8080',
      testRunId: TEST_RUN_ID,
      getAccessToken: () => 'access-token',
      fetch: fetchMock,
      sleep,
      onEvent: (event) => events.push(event.envelope.eventType),
    });

    expect(result).toEqual({ reason: 'terminal', lastSequenceNo: 2 });
    expect(sleep).toHaveBeenCalledWith(500, undefined);
    expect(new Headers(fetchMock.mock.calls[1]?.[1]?.headers).get('Last-Event-ID')).toBe('1');
    expect(events).toEqual(['RUN_STARTED', 'RUN_COMPLETED']);
  });

  it('refreshes once after 401 and reconnects with the last sequence', async () => {
    let token = 'stale-token';
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(streamResponse(frame(3, 'RUN_COMPLETED')));
    const refreshAccessToken = vi.fn<() => Promise<string>>(async () => {
      token = 'fresh-token';
      return token;
    });

    const result = await connectTestRunSse({
      baseUrl: 'http://localhost:8080',
      testRunId: TEST_RUN_ID,
      getAccessToken: () => token,
      refreshAccessToken,
      fetch: fetchMock,
      initialLastSequenceNo: 2,
    });

    expect(refreshAccessToken).toHaveBeenCalledOnce();
    expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get('Authorization')).toBe(
      'Bearer stale-token',
    );
    expect(new Headers(fetchMock.mock.calls[1]?.[1]?.headers).get('Authorization')).toBe(
      'Bearer fresh-token',
    );
    expect(new Headers(fetchMock.mock.calls[1]?.[1]?.headers).get('Last-Event-ID')).toBe('2');
    expect(result).toEqual({ reason: 'terminal', lastSequenceNo: 3 });
  });

  it('stops on refresh failure, 403, 404, replay unavailable, and intentional abort', async () => {
    const refreshFailure = await connectTestRunSse({
      baseUrl: 'http://localhost:8080',
      testRunId: TEST_RUN_ID,
      getAccessToken: () => 'stale-token',
      refreshAccessToken: vi.fn<() => Promise<string>>().mockRejectedValue(new Error('refresh')),
      fetch: vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 401 })),
    });
    const forbidden = await connectTestRunSse({
      baseUrl: 'http://localhost:8080',
      testRunId: TEST_RUN_ID,
      getAccessToken: () => 'token',
      fetch: vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 403 })),
    });
    const notFound = await connectTestRunSse({
      baseUrl: 'http://localhost:8080',
      testRunId: TEST_RUN_ID,
      getAccessToken: () => 'token',
      fetch: vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 404 })),
    });
    const replayUnavailable = await connectTestRunSse({
      baseUrl: 'http://localhost:8080',
      testRunId: TEST_RUN_ID,
      getAccessToken: () => 'token',
      fetch: vi.fn<typeof fetch>().mockResolvedValue(
        new Response(JSON.stringify({ status: 409, code: 'SSE_REPLAY_UNAVAILABLE' }), {
          status: 409,
          headers: { 'Content-Type': 'application/problem+json' },
        }),
      ),
    });
    const controller = new AbortController();
    controller.abort();
    const abortedFetch = vi.fn<typeof fetch>();
    const aborted = await connectTestRunSse({
      baseUrl: 'http://localhost:8080',
      testRunId: TEST_RUN_ID,
      getAccessToken: () => 'token',
      fetch: abortedFetch,
      signal: controller.signal,
    });

    expect(refreshFailure.reason).toBe('authentication-failed');
    expect(forbidden.reason).toBe('forbidden');
    expect(notFound.reason).toBe('not-found');
    expect(replayUnavailable.reason).toBe('replay-unavailable');
    expect(aborted.reason).toBe('aborted');
    expect(abortedFetch).not.toHaveBeenCalled();
  });

  it('backs off on 5xx and stops on max reconnects', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 503 }));
    const sleep = vi.fn<() => Promise<void>>().mockResolvedValue(undefined);

    const result = await connectTestRunSse({
      baseUrl: 'http://localhost:8080',
      testRunId: TEST_RUN_ID,
      getAccessToken: () => 'token',
      fetch: fetchMock,
      sleep,
      maxReconnectAttempts: 2,
    });

    expect(result).toEqual({ reason: 'max-reconnects', lastSequenceNo: 0 });
    expect(sleep).toHaveBeenCalledTimes(2);
  });

  it('avoids duplicate event application across reconnects', async () => {
    const duplicates: number[] = [];
    const applied: number[] = [];
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(streamResponse(frame(1, 'RUN_STARTED')))
      .mockResolvedValueOnce(
        streamResponse(`${frame(1, 'RUN_STARTED')}${frame(2, 'RUN_COMPLETED')}`),
      );
    const sleep = vi.fn<() => Promise<void>>().mockResolvedValue(undefined);

    const result = await connectTestRunSse({
      baseUrl: 'http://localhost:8080',
      testRunId: TEST_RUN_ID,
      getAccessToken: () => 'token',
      fetch: fetchMock,
      sleep,
      maxReconnectAttempts: 2,
      onEvent: (event) => applied.push(event.envelope.sequenceNo),
      onDuplicate: (event) => duplicates.push(event.envelope.sequenceNo),
    });

    expect(result).toEqual({ reason: 'terminal', lastSequenceNo: 2 });
    expect(sleep).toHaveBeenCalledTimes(1);
    expect(applied).toEqual([1, 2]);
    expect(duplicates).toEqual([1]);
  });

  it('stops and reports recovery when a sequence gap is detected', async () => {
    const onGap = vi.fn<(expected: number, actual: number) => void>();
    const result = await connectTestRunSse({
      baseUrl: 'http://localhost:8080',
      testRunId: TEST_RUN_ID,
      getAccessToken: () => 'token',
      fetch: vi.fn<typeof fetch>().mockResolvedValue(streamResponse(frame(3, 'RUN_PROGRESS'))),
      onGap,
    });

    expect(result).toEqual({ reason: 'sequence-gap', lastSequenceNo: 0 });
    expect(onGap).toHaveBeenCalledWith(1, 3);
  });

  it('keeps heartbeat separate from sequence and exposes deterministic backoff values', async () => {
    const comments: string[] = [];
    const result = await connectTestRunSse({
      baseUrl: 'http://localhost:8080',
      testRunId: TEST_RUN_ID,
      getAccessToken: () => 'token',
      fetch: vi
        .fn<typeof fetch>()
        .mockResolvedValue(streamResponse(`: ping\n\n${frame(1, 'RUN_COMPLETED')}`)),
      onHeartbeat: (comment) => comments.push(comment),
    });

    expect(comments).toEqual(['ping']);
    expect(result).toEqual({ reason: 'terminal', lastSequenceNo: 1 });
    expect(createSseReconnectDelay(1)).toBe(500);
    expect(createSseReconnectDelay(3)).toBe(2000);
    expect(createSseReconnectDelay(20)).toBe(16_000);
  });
});

function streamResponse(body: string): Response {
  return new Response(
    new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(new TextEncoder().encode(body));
        controller.close();
      },
    }),
    { status: 200, headers: { 'Content-Type': 'text/event-stream' } },
  );
}

function frame(sequenceNo: number, eventType: string): string {
  return [
    `id:${sequenceNo}`,
    `event:${eventType}`,
    `data:${JSON.stringify({
      eventId: `event-${sequenceNo}`,
      sequenceNo,
      testRunId: TEST_RUN_ID,
      eventType,
      createdAt: '2026-07-03T10:00:00+07:00',
      payload: {},
    })}`,
    '',
    '',
  ].join('\n');
}
