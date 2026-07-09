export interface MaskingPolicy {
  isSensitiveField(fieldName: string): boolean;
}
