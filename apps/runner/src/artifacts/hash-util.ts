import { createHash } from 'node:crypto';

export interface HashUtil {
  sha256(value: string | Uint8Array): string;
}

export class Sha256HashUtil implements HashUtil {
  public sha256(value: string | Uint8Array): string {
    return `sha256:${createHash('sha256').update(value).digest('hex')}`;
  }
}
