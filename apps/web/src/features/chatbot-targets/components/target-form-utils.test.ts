import { describe, expect, it } from 'vitest';
import {
  buildCreateRequest,
  emptyTargetFormState,
} from '@/features/chatbot-targets/components/target-form-utils';

describe('target form utils', () => {
  it('builds explicit secret bindings and dataset mappings without raw secret values', () => {
    const state = {
      ...emptyTargetFormState(),
      name: 'VF Chatbot',
      sanitizedCurl: "curl 'https://example.test/chat' -H 'Authorization: ****'",
      headersText: 'Authorization: ****\nContent-Type: application/json',
      secretBindings: [
        {
          location: 'HEADER' as const,
          keyOrPath: 'Authorization',
          secretId: '44444444-4444-4444-8444-444444444444',
        },
      ],
      datasetMappings: [
        { location: 'BODY_JSON' as const, keyOrPath: 'question', columnName: 'question' },
      ],
    };

    const request = buildCreateRequest(state);

    expect(request.rawCurl).toContain('****');
    expect(JSON.stringify(request)).not.toContain('Bearer sk-');
    expect(request.headers).toEqual({
      Authorization: '****',
      'Content-Type': 'application/json',
    });
    expect(request.secretBindings).toEqual([
      {
        location: 'HEADER',
        keyOrPath: 'Authorization',
        secretId: '44444444-4444-4444-8444-444444444444',
      },
    ]);
    expect(request.datasetMappings).toEqual([
      { location: 'BODY_JSON', keyOrPath: 'question', columnName: 'question' },
    ]);
  });
});
