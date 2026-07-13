import { z } from 'zod';

export const testRunSseEventTypeSchema = z.enum([
  'RUN_STARTED',
  'TESTCASE_STARTED',
  'TESTCASE_COMPLETED',
  'RUN_PROGRESS',
  'RUN_COMPLETED',
  'RUN_FAILED',
  'RUN_CANCELLED',
  'STREAM_ERROR',
]);
export type TestRunSseEventType = z.infer<typeof testRunSseEventTypeSchema>;

export const terminalTestRunSseEventTypes: ReadonlyArray<TestRunSseEventType> = [
  'RUN_COMPLETED',
  'RUN_FAILED',
  'RUN_CANCELLED',
];

export const testRunSseEnvelopeSchema = z.object({
  eventId: z.string().min(1),
  sequenceNo: z.number().int().nonnegative(),
  testRunId: z.string().uuid(),
  eventType: testRunSseEventTypeSchema,
  occurredAt: z.string().optional(),
  createdAt: z.string(),
  payload: z.record(z.string(), z.unknown()),
});
export type TestRunSseEnvelope = z.infer<typeof testRunSseEnvelopeSchema>;

export interface ParsedSseEvent {
  id?: string;
  event: string;
  data: string;
  retry?: number;
}

export interface ParsedTestRunSseEvent {
  raw: ParsedSseEvent;
  envelope: TestRunSseEnvelope;
}

export interface InvalidTestRunSseEvent {
  raw: ParsedSseEvent;
  reason: 'invalid-json' | 'invalid-envelope' | 'event-type-mismatch' | 'sequence-id-mismatch';
}

export type TestRunSseParseResult =
  { ok: true; event: ParsedTestRunSseEvent } | { ok: false; invalidEvent: InvalidTestRunSseEvent };

export function isTerminalTestRunSseEvent(eventType: TestRunSseEventType): boolean {
  return terminalTestRunSseEventTypes.includes(eventType);
}

export function parseTestRunSseEvent(raw: ParsedSseEvent): TestRunSseParseResult {
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw.data);
  } catch {
    return { ok: false, invalidEvent: { raw, reason: 'invalid-json' } };
  }

  const envelope = testRunSseEnvelopeSchema.safeParse(parsed);
  if (!envelope.success) {
    return { ok: false, invalidEvent: { raw, reason: 'invalid-envelope' } };
  }

  if (envelope.data.eventType !== raw.event) {
    return { ok: false, invalidEvent: { raw, reason: 'event-type-mismatch' } };
  }

  if (raw.id !== undefined && raw.id !== String(envelope.data.sequenceNo)) {
    return { ok: false, invalidEvent: { raw, reason: 'sequence-id-mismatch' } };
  }

  return { ok: true, event: { raw, envelope: envelope.data } };
}
