export interface ArtifactMetadata {
  readonly artifactType: string;
  readonly fileName: string;
  readonly contentHash: string;
  readonly sanitized: true;
}
