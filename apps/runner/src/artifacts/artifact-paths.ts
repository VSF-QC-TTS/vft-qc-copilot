import { join } from 'node:path';

export interface ArtifactPaths {
  runDirectory(testRunId: string): string;
  artifactPath(testRunId: string, fileName: string): string;
}

export class DefaultArtifactPaths implements ArtifactPaths {
  public constructor(private readonly rootDir: string) {}

  public runDirectory(testRunId: string): string {
    return join(this.rootDir, testRunId);
  }

  public artifactPath(testRunId: string, fileName: string): string {
    return join(this.runDirectory(testRunId), fileName);
  }
}
