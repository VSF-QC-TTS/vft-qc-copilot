export interface ArtifactMetadata {
  readonly artifactType: string;
  readonly fileName: string;
  readonly contentHash: string;
  readonly sanitized: true;
}

export interface ArtifactMetadataInput {
  readonly artifactType: string;
  readonly fileName: string;
  readonly contentHash: string;
}

export function buildArtifactMetadata(input: ArtifactMetadataInput): ArtifactMetadata {
  return {
    artifactType: input.artifactType,
    fileName: input.fileName,
    contentHash: input.contentHash,
    sanitized: true,
  };
}
