export interface PromptfooResultNormalizer {
  normalize(rawResult: unknown): readonly Record<string, unknown>[];
}
