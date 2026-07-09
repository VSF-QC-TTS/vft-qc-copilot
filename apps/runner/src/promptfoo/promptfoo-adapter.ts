import type { DatasetRowSnapshot, EvaluatorSnapshot, TargetSnapshot } from '../backend/dto/runner-api.dto.js';

export interface PromptfooAdapterInput {
  readonly testRunId: string;
  readonly targets: readonly TargetSnapshot[];
  readonly evaluators: readonly EvaluatorSnapshot[];
  readonly rows: readonly DatasetRowSnapshot[];
  readonly promptfooSnapshot: Record<string, unknown>;
  readonly runtimeSecretsById: Record<string, string>;
}

export interface PromptfooConfigObject {
  readonly cache: false;
  readonly providers: readonly PromptfooProviderConfig[];
  readonly tests: readonly PromptfooTestConfig[];
  readonly defaultTest: PromptfooDefaultTestConfig;
  readonly metadata: Record<string, unknown>;
}

export interface PromptfooProviderConfig {
  readonly id: string;
  readonly label: string;
  readonly config: Record<string, unknown>;
}

export interface PromptfooTestConfig {
  readonly vars: Record<string, unknown>;
  readonly metadata: {
    readonly datasetRowId: number;
    readonly rowIndex: number;
    readonly rowHash: string;
  };
}

export interface PromptfooDefaultTestConfig {
  readonly assert: readonly PromptfooAssertionConfig[];
}

export interface PromptfooAssertionConfig {
  readonly type: string;
  readonly value?: unknown;
  readonly provider?: string;
  readonly metadata: Record<string, unknown>;
}

export interface PromptfooGeneratedConfig {
  readonly configObject: PromptfooConfigObject;
  readonly sanitizedPreview: PromptfooConfigObject;
  readonly referencedSecretIds: readonly string[];
}

export type PromptfooCaseStatus = 'PASSED' | 'FAILED' | 'ERROR' | 'JUDGE_ERROR' | 'TIMEOUT';

export interface PromptfooCaseResult {
  readonly testRunTargetId: string;
  readonly datasetRowId: number;
  readonly status: PromptfooCaseStatus;
  readonly score?: number;
  readonly passed?: boolean;
  readonly errorCategory?: string;
  readonly request?: Record<string, unknown>;
  readonly response?: Record<string, unknown>;
  readonly extractedOutput?: Record<string, unknown>;
  readonly gradingResult?: Record<string, unknown>;
  readonly componentResults?: Record<string, unknown>;
  readonly tokenUsage?: Record<string, unknown>;
  readonly latencyMs?: number;
  readonly machineSummary?: string;
}

export interface PromptfooRunSummary {
  readonly total: number;
  readonly passed: number;
  readonly failed: number;
  readonly errored: number;
  readonly durationMs?: number;
}

export interface PromptfooAdapter {
  buildConfig(input: PromptfooAdapterInput): Promise<PromptfooGeneratedConfig>;
}
