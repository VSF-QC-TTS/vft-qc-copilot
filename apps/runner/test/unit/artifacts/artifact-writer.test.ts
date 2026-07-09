import { mkdtemp, readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';

import { describe, expect, it } from 'vitest';

import { DefaultArtifactPaths } from '../../../src/artifacts/artifact-paths.js';
import { SanitizedJsonArtifactWriter } from '../../../src/artifacts/artifact-writer.js';
import { Sha256HashUtil } from '../../../src/artifacts/hash-util.js';
import { DefaultRunnerEventFactory } from '../../../src/events/runner-event-factory.js';
import { DefaultMaskingPolicy } from '../../../src/masking/masking-policy.js';
import { RecursiveSensitiveDataMasker } from '../../../src/masking/sensitive-data-masker.js';

const testRunId = '27a47393-169d-4f01-92a0-59cf2dc10f8a';
const fakeSecret = 'vfqc_secret_1234567890abcdef';

describe('SanitizedJsonArtifactWriter', () => {
  it('hashes sanitized bytes and never writes raw secrets', async () => {
    const rootDir = await mkdtemp(join(tmpdir(), 'vfqc-runner-artifacts-'));
    const hashUtil = new Sha256HashUtil();
    const writer = new SanitizedJsonArtifactWriter({
      paths: new DefaultArtifactPaths(rootDir),
      masker: new RecursiveSensitiveDataMasker(new DefaultMaskingPolicy({ knownSecrets: [fakeSecret] })),
      hashUtil,
    });

    const metadata = await writer.writeJsonArtifact(testRunId, 'result.json', {
      response: { body: `token=${fakeSecret}` },
    });

    const artifactPath = join(rootDir, testRunId, 'result.json');
    const content = await readFile(artifactPath, 'utf8');
    expect(content).not.toContain(fakeSecret);
    expect(metadata.contentHash).toBe(hashUtil.sha256(content));
  });

  it('builds sanitized artifact metadata without public download URLs', async () => {
    const rootDir = await mkdtemp(join(tmpdir(), 'vfqc-runner-artifacts-'));
    const writer = new SanitizedJsonArtifactWriter({
      paths: new DefaultArtifactPaths(rootDir),
      masker: new RecursiveSensitiveDataMasker(new DefaultMaskingPolicy()),
      hashUtil: new Sha256HashUtil(),
    });

    const metadata = await writer.writeJsonArtifact(testRunId, 'promptfoo-result.json', { ok: true });

    expect(metadata).toEqual({
      artifactType: 'json',
      fileName: 'promptfoo-result.json',
      contentHash: metadata.contentHash,
      sanitized: true,
    });
  });

  it('keeps ARTIFACT_CREATED events metadata-only', () => {
    const factory = new DefaultRunnerEventFactory({
      eventIdGenerator: () => 'evt-artifact-created',
      now: () => '2026-07-09T10:00:00.000Z',
    });

    const event = factory.createArtifactCreated({
      testRunId,
      artifactType: 'json',
      name: 'result.json',
      uri: 'local://result.json',
      sanitized: true,
      contentHash: 'sha256:abc123',
      content: 'raw-content-must-not-pass',
    } as Parameters<typeof factory.createArtifactCreated>[0] & { readonly content: string });

    expect(event.payload).toEqual({
      artifactType: 'json',
      name: 'result.json',
      uri: 'local://result.json',
      sanitized: true,
      contentHash: 'sha256:abc123',
    });
  });
});
