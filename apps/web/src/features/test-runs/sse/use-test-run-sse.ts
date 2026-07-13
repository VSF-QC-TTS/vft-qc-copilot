import { useEffect, useMemo, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';

import { testRunKeys } from '@/features/test-runs/hooks/use-test-runs';
import { createTestRunSseQueryBridge } from '@/features/test-runs/sse/test-run-sse-query-bridge';
import {
  connectTestRunSse,
  type TestRunSseConnectionState,
  type TestRunSseStopReason,
} from '@/features/test-runs/sse/test-run-sse-transport';
import { accessTokenStore, apiBaseUrl, refreshAccessToken } from '@/shared/api/api';

type RealtimeState = 'idle' | TestRunSseConnectionState;

export interface TestRunSseState {
  connectionState: RealtimeState;
  lastSequenceNo: number;
  stopReason?: TestRunSseStopReason;
}

interface UseTestRunSseOptions {
  testRunId: string;
  projectId: string;
  enabled: boolean;
}

const FALLBACK_POLLING_INTERVAL_MS = 5_000;

export function useTestRunSse({
  testRunId,
  projectId,
  enabled,
}: UseTestRunSseOptions): TestRunSseState {
  const queryClient = useQueryClient();
  const [connectionState, setConnectionState] = useState<RealtimeState>('idle');
  const [lastSequenceNo, setLastSequenceNo] = useState(0);
  const [stopReason, setStopReason] = useState<TestRunSseStopReason | undefined>();

  const bridge = useMemo(() => {
    if (!testRunId || !projectId) {
      return undefined;
    }

    return createTestRunSseQueryBridge({ queryClient, projectId, testRunId });
  }, [projectId, queryClient, testRunId]);

  useEffect(() => {
    if (!enabled || !bridge) {
      setConnectionState('idle');
      setStopReason(undefined);
      return undefined;
    }

    const controller = new AbortController();
    setStopReason(undefined);

    const activeBridge = bridge;

    void connectTestRunSse({
      baseUrl: apiBaseUrl,
      testRunId,
      getAccessToken: () => accessTokenStore.getAccessToken(),
      refreshAccessToken: async () => {
        const accessToken = await refreshAccessToken();
        accessTokenStore.setAccessToken(accessToken);
        return accessToken;
      },
      signal: controller.signal,
      onConnectionStateChange: setConnectionState,
      onEvent(event) {
        setLastSequenceNo(event.envelope.sequenceNo);
        activeBridge.handleEvent(event);
      },
      onGap() {
        activeBridge.invalidateSnapshot();
      },
      onInvalidEvent() {
        activeBridge.invalidateSnapshot();
      },
    })
      .then((result) => {
        if (!controller.signal.aborted) {
          setLastSequenceNo(result.lastSequenceNo);
          setStopReason(result.reason);
          setConnectionState(result.reason === 'terminal' ? 'closed' : 'error');
        }
        return undefined;
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          activeBridge.invalidateSnapshot();
          setConnectionState('error');
        }
        return undefined;
      });

    return () => {
      controller.abort();
      activeBridge.dispose();
    };
  }, [bridge, enabled, testRunId]);

  useEffect(() => {
    if (!enabled || !bridge) {
      return undefined;
    }
    const activeBridge = bridge;

    function recoverOnVisibilityRegain(): void {
      if (document.visibilityState === 'visible') {
        activeBridge.invalidateSnapshot();
      }
    }

    document.addEventListener('visibilitychange', recoverOnVisibilityRegain);
    return () => document.removeEventListener('visibilitychange', recoverOnVisibilityRegain);
  }, [bridge, enabled]);

  useEffect(() => {
    const disconnected =
      enabled && (connectionState === 'reconnecting' || connectionState === 'error');
    if (!disconnected) {
      return undefined;
    }

    const interval = window.setInterval(() => {
      void queryClient.invalidateQueries({ queryKey: testRunKeys.detail(testRunId) });
      void queryClient.invalidateQueries({ queryKey: testRunKeys.results(testRunId) });
    }, FALLBACK_POLLING_INTERVAL_MS);

    return () => window.clearInterval(interval);
  }, [connectionState, enabled, queryClient, testRunId]);

  return { connectionState, lastSequenceNo, ...(stopReason ? { stopReason } : {}) };
}

export function testRunSseConnectionLabel(
  state: RealtimeState,
  stopReason?: TestRunSseStopReason,
): string {
  if (stopReason === 'terminal') {
    return 'Realtime đã đóng';
  }

  switch (state) {
    case 'idle':
      return 'Realtime chưa mở';
    case 'connecting':
      return 'Realtime đang kết nối';
    case 'open':
      return 'Realtime hoạt động';
    case 'reconnecting':
      return 'Đang kết nối lại realtime';
    case 'closed':
      return 'Realtime đã đóng';
    case 'error':
      return 'Realtime tạm ngắt';
    default: {
      const exhaustive: never = state;
      return exhaustive;
    }
  }
}
