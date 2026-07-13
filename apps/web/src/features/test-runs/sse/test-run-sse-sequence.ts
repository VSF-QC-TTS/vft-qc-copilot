import {
  isTerminalTestRunSseEvent,
  type ParsedTestRunSseEvent,
} from '@/features/test-runs/sse/test-run-sse.types';

export interface TestRunSseSequenceState {
  testRunId: string;
  lastAppliedSequenceNo: number;
  closed: boolean;
}

export type TestRunSseSequenceDecision =
  | { type: 'applied'; state: TestRunSseSequenceState; event: ParsedTestRunSseEvent }
  | { type: 'terminal'; state: TestRunSseSequenceState; event: ParsedTestRunSseEvent }
  | { type: 'duplicate'; state: TestRunSseSequenceState; event: ParsedTestRunSseEvent }
  | {
      type: 'gap';
      state: TestRunSseSequenceState;
      expectedSequenceNo: number;
      actualSequenceNo: number;
    }
  | { type: 'ignored-wrong-run'; state: TestRunSseSequenceState; event: ParsedTestRunSseEvent };

export function createTestRunSseSequenceState(
  testRunId: string,
  lastAppliedSequenceNo = 0,
): TestRunSseSequenceState {
  return { testRunId, lastAppliedSequenceNo, closed: false };
}

export function reduceTestRunSseSequence(
  state: TestRunSseSequenceState,
  event: ParsedTestRunSseEvent,
): TestRunSseSequenceDecision {
  const sequenceNo = event.envelope.sequenceNo;

  if (event.envelope.testRunId !== state.testRunId) {
    return { type: 'ignored-wrong-run', state, event };
  }

  if (sequenceNo <= state.lastAppliedSequenceNo) {
    return { type: 'duplicate', state, event };
  }

  const expectedSequenceNo = state.lastAppliedSequenceNo + 1;
  if (sequenceNo !== expectedSequenceNo) {
    return { type: 'gap', state, expectedSequenceNo, actualSequenceNo: sequenceNo };
  }

  const nextState = {
    testRunId: state.testRunId,
    lastAppliedSequenceNo: sequenceNo,
    closed: isTerminalTestRunSseEvent(event.envelope.eventType),
  };

  if (nextState.closed) {
    return { type: 'terminal', state: nextState, event };
  }

  return { type: 'applied', state: nextState, event };
}
