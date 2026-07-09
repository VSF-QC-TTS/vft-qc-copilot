import { mkdtemp, readFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { DefaultArtifactPaths } from '../../../src/artifacts/artifact-paths.js';
import { SanitizedJsonArtifactWriter } from '../../../src/artifacts/artifact-writer.js';
import { Sha256HashUtil } from '../../../src/artifacts/hash-util.js';
import { DefaultMaskingPolicy } from '../../../src/masking/masking-policy.js';
import { RecursiveSensitiveDataMasker } from '../../../src/masking/sensitive-data-masker.js';
import { DefaultPromptfooConfigBuilder } from '../../../src/promptfoo/promptfoo-config-builder.js';
import {
  comparisonInput,
  llmJudgeInput,
  mixedEvaluatorInput,
  promptfooFakeSecret,
  singleChatbotInput,
} from '../../fixtures/promptfoo/promptfoo-fixtures.js';

describe('DefaultPromptfooConfigBuilder', () => {
  it('builds a sanitized CHATBOT_TARGET config preview', () => {
    const builder = createBuilder();

    const generated = builder.build(singleChatbotInput());

    expect(generated.sanitizedPreview).toMatchObject({
      cache: false,
      providers: [
        {
          id: 'vfqc-chatbot:c1350b87-c35c-40b2-bbb1-b6f8815720fd',
          label: 'Sales chatbot',
          config: {
            headers: {
              Authorization: 'Bearer ****MASKED****',
            },
            body: {
              question: '{{question}}',
            },
          },
        },
      ],
      tests: [
        {
          vars: {
            question: 'VinFast VF 8 warranty?',
            expected: 'Warranty information',
          },
          metadata: {
            datasetRowId: 101,
            rowIndex: 0,
            rowHash: 'row-hash-101',
          },
        },
      ],
    });
    expect(JSON.stringify(generated.sanitizedPreview)).not.toContain(promptfooFakeSecret);
  });

  it('creates multiple providers for comparison runs', () => {
    const builder = createBuilder();

    const generated = builder.build(comparisonInput());

    expect(generated.configObject.providers).toHaveLength(2);
    expect(generated.configObject.providers).toEqual([
      expect.objectContaining({ id: 'vfqc-chatbot:c1350b87-c35c-40b2-bbb1-b6f8815720fd' }),
      expect.objectContaining({ id: 'openai:gpt-4.1-mini' }),
    ]);
  });

  it('maps RULE_BASED evaluators to assertions', () => {
    const builder = createBuilder();

    const generated = builder.build(singleChatbotInput());

    expect(generated.configObject.defaultTest.assert).toEqual([
      expect.objectContaining({
        type: 'contains',
        value: 'Warranty',
        metadata: expect.objectContaining({
          evaluatorType: 'RULE_BASED',
          fieldSource: 'dataset:$dataset.expected',
        }),
      }),
    ]);
  });

  it('maps LLM_JUDGE evaluators to rubric assertions', () => {
    const builder = createBuilder();

    const generated = builder.build(llmJudgeInput());

    expect(generated.configObject.defaultTest.assert).toEqual([
      expect.objectContaining({
        type: 'llm-rubric',
        value: 'Answer must match the expected warranty facts.',
        provider: 'openai:gpt-4.1-mini',
      }),
    ]);
  });

  it('combines rule and judge assertions for MIXED evaluators', () => {
    const builder = createBuilder();

    const generated = builder.build(mixedEvaluatorInput());

    expect(generated.configObject.defaultTest.assert).toEqual([
      expect.objectContaining({ type: 'contains' }),
      expect.objectContaining({ type: 'llm-rubric', value: 'Answer must be grounded and concise.' }),
    ]);
  });

  it('writes generated config artifacts without secrets', async () => {
    const rootDir = await mkdtemp(join(tmpdir(), 'vfqc-promptfoo-config-'));
    const input = singleChatbotInput();
    const writer = new SanitizedJsonArtifactWriter({
      paths: new DefaultArtifactPaths(rootDir),
      masker: new RecursiveSensitiveDataMasker(new DefaultMaskingPolicy({ knownSecrets: [promptfooFakeSecret] })),
      hashUtil: new Sha256HashUtil(),
    });
    const builder = createBuilder();

    await writer.writeJsonArtifact(input.testRunId, 'promptfooconfig.json', builder.build(input).sanitizedPreview);

    const content = await readFile(join(rootDir, input.testRunId, 'promptfooconfig.json'), 'utf8');
    expect(content).not.toContain(promptfooFakeSecret);
    expect(content).toContain('****MASKED****');
  });
});

function createBuilder(): DefaultPromptfooConfigBuilder {
  return new DefaultPromptfooConfigBuilder({
    masker: new RecursiveSensitiveDataMasker(new DefaultMaskingPolicy({ knownSecrets: [promptfooFakeSecret] })),
  });
}
