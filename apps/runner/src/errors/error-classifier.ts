export interface ErrorClassifier {
  isRetryable(error: unknown): boolean;
}
