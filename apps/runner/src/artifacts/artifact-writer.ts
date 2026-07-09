import type { ArtifactMetadata } from './artifact-metadata.js';

export interface ArtifactWriter {
  writeJsonArtifact(testRunId: string, fileName: string, value: unknown): Promise<ArtifactMetadata>;
}
