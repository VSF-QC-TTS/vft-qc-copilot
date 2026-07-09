import { describe, expect, it } from 'vitest';

import {
  RUNNER_COMMAND_TYPES,
  RUNNER_EVENT_TYPES,
  TEST_RUN_STATUSES,
  TEST_RUN_TARGET_TYPES,
  TEST_RUN_TYPES,
  type RunnerEventRequest,
} from '../../../src/backend/dto/runner-api.dto.js';

describe('runner API DTO constants', () => {
  it('exports backend runner enum values used by the scaffold', () => {
    const event: RunnerEventRequest = {
      eventId: 'evt-001',
      testRunId: '27a47393-169d-4f01-92a0-59cf2dc10f8a',
      eventType: 'RUN_STARTED',
      payload: { status: 'RUNNING' },
    };

    expect(event.eventType).toBe('RUN_STARTED');
    expect(RUNNER_COMMAND_TYPES).toContain('START_RUN');
    expect(RUNNER_EVENT_TYPES).toContain('TESTCASE_COMPLETED');
    expect(TEST_RUN_STATUSES).toContain('CANCEL_REQUESTED');
    expect(TEST_RUN_TARGET_TYPES).toContain('CHATBOT_TARGET');
    expect(TEST_RUN_TYPES).toContain('COMPARISON');
  });
});
