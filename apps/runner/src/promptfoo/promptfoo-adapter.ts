import type { DatasetRowSnapshot, EvaluatorSnapshot, TargetSnapshot } from '../backend/dto/runner-api.dto.js';

export interface PromptfooAdapterInput {
  readonly testRunId: string;
  readonly targets: readonly TargetSnapshot[];
  readonly evaluators: readonly EvaluatorSnapshot[];
  readonly rows: readonly DatasetRowSnapshot[];
  readonly promptfooSnapshot: Record<string, unknown>;
  readonly runtimeSecretsById: Record<string, string>;
}

export interface PromptfooAdapter {
  buildConfig(input: PromptfooAdapterInput): Promise<Record<string, unknown>>;
}
