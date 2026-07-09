export const MASKED_VALUE = '****MASKED****';

const DEFAULT_SENSITIVE_FIELDS = [
  'authorization',
  'cookie',
  'set-cookie',
  'x-api-key',
  'api_key',
  'apikey',
  'access_token',
  'refresh_token',
  'id_token',
  'client_secret',
  'password',
  'secret',
  'token',
  'bearer',
  'credential',
  'x-auth-token',
  'proxy-authorization',
] as const;

export interface MaskingPolicy {
  readonly knownSecrets: readonly string[];
  isSensitiveField(fieldName: string): boolean;
  isTokenLikeValue(value: string): boolean;
}

export interface MaskingPolicyOptions {
  readonly knownSecrets?: readonly string[];
  readonly sensitiveFields?: readonly string[];
}

export class DefaultMaskingPolicy implements MaskingPolicy {
  public readonly knownSecrets: readonly string[];
  private readonly sensitiveFields: ReadonlySet<string>;

  public constructor(options: MaskingPolicyOptions = {}) {
    this.knownSecrets = options.knownSecrets ?? [];
    this.sensitiveFields = new Set([...(options.sensitiveFields ?? DEFAULT_SENSITIVE_FIELDS)].map(normalizeFieldName));
  }

  public isSensitiveField(fieldName: string): boolean {
    const normalized = normalizeFieldName(fieldName);
    if (this.sensitiveFields.has(normalized)) {
      return true;
    }
    return [...this.sensitiveFields].some((field) => normalized.includes(field));
  }

  public isTokenLikeValue(value: string): boolean {
    return /Bearer\s+[A-Za-z0-9._~+/=-]{8,}/u.test(value) || /(?:token|secret|password|api[_-]?key)=[^\s,;]+/iu.test(value);
  }
}

function normalizeFieldName(fieldName: string): string {
  return fieldName.trim().toLowerCase();
}
