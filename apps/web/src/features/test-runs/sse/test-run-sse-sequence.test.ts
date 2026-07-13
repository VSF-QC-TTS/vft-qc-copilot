import { describe, expect, it } from 'vitest';

import {
  createTestRunSseSequenceState,
  reduceTestRunSseSequence,
} from '@/features/test-runs/sse/test-run-sse-sequence';
import {
  parseTestRunSseEvent,
  type ParsedSseEvent,
} from '@/features/test-runs/sse/test-run-sse.types';

const TEST_RUN_ID = '11111111-1111-4111-8111-111111111111';

describe('TestRun SSE sequence reducer', () => {
  it('applies the next sequence and ignores duplicates or older events', () => {
    const initial = createTestRunSseSequenceState(TEST_RUN_ID);
    const first = reduceTestRunSseSequence(initial, event(1, 'RUN_STARTED'));
    expect(first.type).toBe('applied');
    expect(first.state.lastAppliedSequenceNo).toBe(1);

    expect(reduceTestRunSseSequence(first.state, event(1, 'RUN_STARTED')).type).toBe('duplicate');
    expect(reduceTestRunSseSequence(first.state, event(0, 'RUN_STARTED')).type).toBe('duplicate');
  });

  it('detects sequence gaps without mutating the cursor', () => {
    const state = createTestRunSseSequenceState(TEST_RUN_ID, 1);
    const result = reduceTestRunSseSequence(state, event(3, 'RUN_PROGRESS'));

    expect(result).toMatchObject({
      type: 'gap',
      expectedSequenceNo: 2,
      actualSequenceNo: 3,
      state,
    });
  });

  it('ignores wrong TestRun IDs and marks terminal events closed', () => {
    const state = createTestRunSseSequenceState(TEST_RUN_ID);
    const wrongRun = reduceTestRunSseSequence(
      state,
      event(1, 'RUN_STARTED', '22222222-2222-4222-8222-222222222222'),
    );
    const terminal = reduceTestRunSseSequence(state, event(1, 'RUN_COMPLETED'));

    expect(wrongRun.type).toBe('ignored-wrong-run');
    expect(terminal.type).toBe('terminal');
    expect(terminal.state.closed).toBe(true);
  });

  it('rejects invalid JSON and event name or id mismatches before sequence handling', () => {
    const invalidJson = parseTestRunSseEvent({ event: 'RUN_STARTED', data: '{invalid' });
    const typeMismatch = parseTestRunSseEvent(rawEvent(1, 'RUN_PROGRESS', 'RUN_STARTED'));
    const idMismatch = parseTestRunSseEvent({ ...rawEvent(1, 'RUN_STARTED'), id: '2' });

    expect(invalidJson).toMatchObject({ ok: false, invalidEvent: { reason: 'invalid-json' } });
    expect(typeMismatch).toMatchObject({
      ok: false,
      invalidEvent: { reason: 'event-type-mismatch' },
    });
    expect(idMismatch).toMatchObject({
      ok: false,
      invalidEvent: { reason: 'sequence-id-mismatch' },
    });
  });
});

function event(sequenceNo: number, eventType: ParsedSseEvent['event'], testRunId = TEST_RUN_ID) {
  const parsed = parseTestRunSseEvent(rawEvent(sequenceNo, eventType, eventType, testRunId));
  if (!parsed.ok) {
    throw new Error(`Invalid test fixture: ${parsed.invalidEvent.reason}`);
  }
  return parsed.event;
}

function rawEvent(
  sequenceNo: number,
  sseEventType: ParsedSseEvent['event'],
  envelopeEventType = sseEventType,
  testRunId = TEST_RUN_ID,
): ParsedSseEvent {
  return {
    id: String(sequenceNo),
    event: sseEventType,
    data: JSON.stringify({
      eventId: `event-${sequenceNo}`,
      sequenceNo,
      testRunId,
      eventType: envelopeEventType,
      createdAt: '2026-07-03T10:00:00+07:00',
      payload: {},
    }),
  };
}
