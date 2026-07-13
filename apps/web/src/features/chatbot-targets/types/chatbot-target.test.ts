import { describe, expect, it } from 'vitest';
import {
  chatbotTargetReadinessSchema,
  chatbotTargetResponseSchema,
  inspectCurlResponseSchema,
} from '@/features/chatbot-targets/types/chatbot-target';

const userSummary = {
  id: '11111111-1111-4111-8111-111111111111',
  email: 'owner@example.test',
  name: 'Owner',
};

describe('chatbot target schemas', () => {
  it('validates backend chatbot target response shape', () => {
    const result = chatbotTargetResponseSchema.safeParse({
      id: '22222222-2222-4222-8222-222222222222',
      projectId: '33333333-3333-4333-8333-333333333333',
      name: 'VF Chatbot',
      description: 'Main target',
      rawCurlSanitized: "curl 'https://example.test' -H 'Authorization: ****'",
      requestConfig: {
        method: 'POST',
        url: 'https://example.test/chat',
        headers: { Authorization: '****' },
        queryParams: {},
        bodyType: 'JSON',
        body: '{"question":"{{question}}"}',
        secretBindings: [
          {
            location: 'HEADER',
            keyOrPath: 'Authorization',
            secretId: '44444444-4444-4444-8444-444444444444',
          },
        ],
        datasetMappings: [
          {
            location: 'BODY_JSON',
            keyOrPath: 'question',
            columnName: 'question',
          },
        ],
      },
      responseExtractionConfig: {
        fields: [{ fieldName: 'answer', source: 'JSON_PATH', path: '$.answer', required: true }],
      },
      latestExampleId: 7,
      status: 'ACTIVE',
      createdBy: userSummary.id,
      createdByUser: userSummary,
      createdAt: '2026-07-14T00:00:00Z',
      updatedAt: '2026-07-14T00:00:00Z',
    });

    expect(result.success).toBe(true);
  });

  it('validates sanitized cURL inspection response', () => {
    const result = inspectCurlResponseSchema.safeParse({
      sanitizedCurl: "curl 'https://example.test' -H 'Authorization: ****'",
      findings: ['Authorization header was sanitized'],
      parsedRequest: {
        method: 'POST',
        url: 'https://example.test/chat',
        headers: { Authorization: '****' },
        queryParams: {},
        bodyType: 'JSON',
        body: '{}',
        secretBindings: [],
        datasetMappings: [],
      },
      secretBindingCandidates: [{ location: 'HEADER', keyOrPath: 'Authorization' }],
    });

    expect(result.success).toBe(true);
  });

  it('keeps readiness blockers server-driven', () => {
    const result = chatbotTargetReadinessSchema.safeParse({
      ready: false,
      blockers: [{ code: 'TARGET_NOT_ACTIVE', message: 'Target must be ACTIVE.' }],
    });

    expect(result.success).toBe(true);
  });
});
