export interface CancellationService {
  readonly signal: AbortSignal;
  isCancellationRequested(): boolean;
}
