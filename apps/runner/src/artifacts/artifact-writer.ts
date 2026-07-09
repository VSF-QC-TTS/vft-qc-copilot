import { mkdir, writeFile } from 'node:fs/promises';

import { buildArtifactMetadata, type ArtifactMetadata } from './artifact-metadata.js';
import type { ArtifactPaths } from './artifact-paths.js';
import type { HashUtil } from './hash-util.js';
import type { SensitiveDataMasker } from '../masking/sensitive-data-masker.js';

export interface ArtifactWriter {
  writeJsonArtifact(testRunId: string, fileName: string, value: unknown): Promise<ArtifactMetadata>;
}

export interface SanitizedJsonArtifactWriterOptions {
  readonly paths: ArtifactPaths;
  readonly masker: SensitiveDataMasker;
  readonly hashUtil: HashUtil;
}

export class SanitizedJsonArtifactWriter implements ArtifactWriter {
  public constructor(private readonly options: SanitizedJsonArtifactWriterOptions) {}

  public async writeJsonArtifact(testRunId: string, fileName: string, value: unknown): Promise<ArtifactMetadata> {
    const sanitized = this.options.masker.mask(value);
    const content = `${JSON.stringify(sanitized, null, 2)}\n`;
    const artifactPath = this.options.paths.artifactPath(testRunId, fileName);

    await mkdir(this.options.paths.runDirectory(testRunId), { recursive: true });
    await writeFile(artifactPath, content, { encoding: 'utf8' });

    return buildArtifactMetadata({
      artifactType: 'json',
      fileName,
      contentHash: this.options.hashUtil.sha256(content),
    });
  }
}
