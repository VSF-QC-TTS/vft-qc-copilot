import type { QueryClient } from '@tanstack/react-query';

import { testRunKeys } from '@/features/test-runs/hooks/use-test-runs';
import {
  testRunStatusSchema,
  type TestRunDetailResponse,
} from '@/features/test-runs/types/test-run';
import type { ParsedTestRunSseEvent } from '@/features/test-runs/sse/test-run-sse.types';

interface TestRunSseQueryBridgeOptions {
  queryClient: QueryClient;
  projectId: string;
  testRunId: string;
  resultInvalidationDebounceMs?: number;
  setTimeout?: Window['setTimeout'];
  clearTimeout?: Window['clearTimeout'];
}

export interface TestRunSseQueryBridge {
  handleEvent(event: ParsedTestRunSseEvent): void;
  invalidateSnapshot(): void;
  invalidateResults(): void;
  dispose(): void;
}

export function createTestRunSseQueryBridge({
  queryClient,
  projectId,
  testRunId,
  resultInvalidationDebounceMs = 500,
  setTimeout: setTimeoutImplementation = window.setTimeout.bind(window),
  clearTimeout: clearTimeoutImplementation = window.clearTimeout.bind(window),
}: TestRunSseQueryBridgeOptions): TestRunSseQueryBridge {
  let resultInvalidationTimer: number | undefined;

  function invalidateDetail(): void {
    void queryClient.invalidateQueries({ queryKey: testRunKeys.detail(testRunId) });
  }

  function invalidateProjectRuns(): void {
    void queryClient.invalidateQueries({ queryKey: testRunKeys.project(projectId) });
  }

  function invalidateResults(): void {
    void queryClient.invalidateQueries({ queryKey: testRunKeys.results(testRunId) });
  }

  function invalidateResultsDebounced(): void {
    if (resultInvalidationTimer !== undefined) {
      return;
    }

    resultInvalidationTimer = setTimeoutImplementation(() => {
      resultInvalidationTimer = undefined;
      invalidateResults();
    }, resultInvalidationDebounceMs);
  }

  function updateProgress(event: ParsedTestRunSseEvent): void {
    queryClient.setQueryData<TestRunDetailResponse | undefined>(
      testRunKeys.detail(testRunId),
      (current) => {
        if (!current) {
          return current;
        }

        const status = readStatus(event.envelope.payload.status) ?? current.status;
        const completed = readNumber(event.envelope.payload.completed) ?? current.progressCompleted;
        const total = readNumber(event.envelope.payload.total) ?? current.progressTotal;

        return {
          ...current,
          status,
          progressCompleted: completed,
          progressTotal: total,
          summaryMetrics: {
            ...current.summaryMetrics,
            ...event.envelope.payload,
          },
          updatedAt: event.envelope.createdAt,
        };
      },
    );
  }

  return {
    handleEvent(event) {
      switch (event.envelope.eventType) {
        case 'RUN_STARTED':
          invalidateDetail();
          invalidateProjectRuns();
          break;
        case 'RUN_PROGRESS':
          updateProgress(event);
          break;
        case 'TESTCASE_STARTED':
        case 'TESTCASE_COMPLETED':
          invalidateResultsDebounced();
          break;
        case 'RUN_COMPLETED':
        case 'RUN_FAILED':
        case 'RUN_CANCELLED':
          updateProgress(event);
          invalidateDetail();
          invalidateProjectRuns();
          invalidateResults();
          break;
        case 'STREAM_ERROR':
          invalidateDetail();
          invalidateResults();
          break;
      }
    },
    invalidateSnapshot() {
      invalidateDetail();
      invalidateProjectRuns();
      invalidateResults();
    },
    invalidateResults,
    dispose() {
      if (resultInvalidationTimer !== undefined) {
        clearTimeoutImplementation(resultInvalidationTimer);
        resultInvalidationTimer = undefined;
      }
    },
  };
}

function readNumber(value: unknown): number | undefined {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
}

function readStatus(value: unknown): TestRunDetailResponse['status'] | undefined {
  const result = testRunStatusSchema.safeParse(value);
  return result.success ? result.data : undefined;
}
