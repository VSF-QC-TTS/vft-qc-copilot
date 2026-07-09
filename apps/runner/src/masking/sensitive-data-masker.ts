import { MASKED_VALUE, type MaskingPolicy } from './masking-policy.js';

export interface SensitiveDataMasker {
  mask(value: unknown): unknown;
}

export class RecursiveSensitiveDataMasker implements SensitiveDataMasker {
  public constructor(private readonly policy: MaskingPolicy) {}

  public mask(value: unknown): unknown {
    return this.maskValue(value, undefined);
  }

  private maskValue(value: unknown, fieldName: string | undefined): unknown {
    if (typeof value === 'string') {
      return this.maskString(value, fieldName);
    }
    if (Array.isArray(value)) {
      return value.map((item) => this.maskValue(item, fieldName));
    }
    if (isRecord(value)) {
      return this.maskRecord(value);
    }
    return value;
  }

  private maskRecord(record: Readonly<Record<string, unknown>>): Record<string, unknown> {
    const masked: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(record)) {
      if (this.policy.isSensitiveField(key)) {
        masked[key] = maskSensitiveFieldValue(key, value);
      } else {
        masked[key] = this.maskValue(value, key);
      }
    }
    return masked;
  }

  private maskString(value: string, fieldName: string | undefined): string {
    if (fieldName !== undefined && this.policy.isSensitiveField(fieldName)) {
      return maskSensitiveText(fieldName, value);
    }

    let masked = value;
    for (const secret of this.policy.knownSecrets) {
      if (secret.length > 0) {
        masked = masked.split(secret).join(MASKED_VALUE);
      }
    }
    masked = maskAuthorizationText(masked);
    if (this.policy.isTokenLikeValue(masked)) {
      masked = maskTokenAssignments(masked);
    }
    return masked;
  }
}

function maskSensitiveFieldValue(fieldName: string, value: unknown): string {
  if (typeof value === 'string') {
    return maskSensitiveText(fieldName, value);
  }
  return MASKED_VALUE;
}

function maskSensitiveText(fieldName: string, value: string): string {
  if (fieldName.toLowerCase() === 'authorization') {
    return maskAuthorizationText(value);
  }
  return MASKED_VALUE;
}

function maskAuthorizationText(value: string): string {
  return value.replace(/(Authorization:\s*Bearer\s+)[^\s'"]+/giu, `$1${MASKED_VALUE}`).replace(/^Bearer\s+.+$/iu, `Bearer ${MASKED_VALUE}`);
}

function maskTokenAssignments(value: string): string {
  return value.replace(/((?:token|secret|password|api[_-]?key)=)[^\s,;'"]+/giu, `$1${MASKED_VALUE}`);
}

function isRecord(value: unknown): value is Readonly<Record<string, unknown>> {
  return typeof value === 'object' && value !== null;
}
