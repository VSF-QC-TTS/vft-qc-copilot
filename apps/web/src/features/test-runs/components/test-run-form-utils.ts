import type { ChatbotTargetResponse } from '@/features/chatbot-targets/types/chatbot-target';
import type { DatasetResponse } from '@/features/datasets/types/dataset';
import type { EvaluatorResponse } from '@/features/evaluators/types/evaluator';
import type { ProviderResponse } from '@/features/providers/types/provider';
import type {
  CreateTestRunRequest,
  RunPreflightRequest,
  RunTargetRequest,
  TestRunType,
} from '@/features/test-runs/types/test-run';

export interface BaselineDraft {
  providerId: string;
  model: string;
  enabled: boolean;
}

export interface TestRunSelection {
  datasetId: string;
  runType: TestRunType;
  chatbotTargetIds: string[];
  baselines: BaselineDraft[];
  evaluatorVersionIds: string[];
  maxRows: string;
}

export function buildRunTargets(
  targets: ChatbotTargetResponse[],
  providers: ProviderResponse[],
  selection: TestRunSelection,
): RunTargetRequest[] {
  const chatbotTargets = selection.chatbotTargetIds
    .map((targetId) => targets.find((target) => target.id === targetId))
    .filter((target): target is ChatbotTargetResponse => Boolean(target))
    .map((target) => ({
      targetType: 'CHATBOT_TARGET' as const,
      chatbotTargetId: target.id,
      name: target.name,
    }));

  const baselines = selection.baselines
    .filter((baseline) => baseline.enabled)
    .map((baseline) => {
      const provider = providers.find((item) => item.id === baseline.providerId);
      return {
        targetType: 'LLM_BASELINE' as const,
        providerId: baseline.providerId,
        model: baseline.model.trim() || provider?.defaultModel || '',
        name: provider ? `${provider.name} baseline` : 'LLM baseline',
      };
    });

  return [...chatbotTargets, ...baselines];
}

export function buildRunEvaluators(evaluators: EvaluatorResponse[], selectedVersionIds: string[]) {
  const availableVersionIds = new Set(
    evaluators
      .map((evaluator) => evaluator.latestVersion?.id)
      .filter((versionId): versionId is string => Boolean(versionId)),
  );
  return selectedVersionIds
    .filter((versionId) => availableVersionIds.has(versionId))
    .map((evaluatorVersionId) => ({ evaluatorVersionId }));
}

export function buildRunOptions(maxRows: string) {
  const trimmed = maxRows.trim();
  if (!trimmed) return undefined;
  const value = Number(trimmed);
  if (!Number.isInteger(value) || value < 1) return undefined;
  return {
    maxRows: value,
    includePromptfooSnapshot: false,
  };
}

export function buildCreateTestRunRequest(
  targets: ChatbotTargetResponse[],
  providers: ProviderResponse[],
  evaluators: EvaluatorResponse[],
  selection: TestRunSelection,
): CreateTestRunRequest {
  return {
    datasetId: selection.datasetId,
    runType: selection.runType,
    targets: buildRunTargets(targets, providers, selection),
    evaluators: buildRunEvaluators(evaluators, selection.evaluatorVersionIds),
    ...(buildRunOptions(selection.maxRows) ? { options: buildRunOptions(selection.maxRows) } : {}),
  };
}

export function buildRunPreflightRequest(
  targets: ChatbotTargetResponse[],
  providers: ProviderResponse[],
  evaluators: EvaluatorResponse[],
  selection: TestRunSelection,
): RunPreflightRequest {
  const request = buildCreateTestRunRequest(targets, providers, evaluators, selection);
  return {
    datasetId: request.datasetId,
    targets: request.targets,
    evaluators: request.evaluators,
    ...(request.options ? { options: request.options } : {}),
  };
}

export function validateRunSelection(
  datasets: DatasetResponse[],
  targets: ChatbotTargetResponse[],
  providers: ProviderResponse[],
  evaluators: EvaluatorResponse[],
  selection: TestRunSelection,
) {
  const errors: string[] = [];
  const dataset = datasets.find((item) => item.id === selection.datasetId);
  const runnableTargets = buildRunTargets(targets, providers, selection);
  const runEvaluators = buildRunEvaluators(evaluators, selection.evaluatorVersionIds);

  if (!dataset) {
    errors.push('Vui lòng chọn Dataset.');
  } else if (!dataset.currentVersionId) {
    errors.push('Dataset cần có active version trước khi chạy.');
  }

  if (selection.runType === 'SINGLE_TARGET' && runnableTargets.length !== 1) {
    errors.push('Single run cần đúng 1 target.');
  }

  if (selection.runType === 'COMPARISON' && runnableTargets.length < 2) {
    errors.push('Comparison run cần ít nhất 2 target.');
  }

  if (runEvaluators.length < 1) {
    errors.push('Vui lòng chọn ít nhất 1 Evaluator version.');
  }

  if (selection.maxRows.trim()) {
    const maxRows = Number(selection.maxRows);
    if (!Number.isInteger(maxRows) || maxRows < 1 || maxRows > 10000) {
      errors.push('Max rows phải là số nguyên từ 1 đến 10000.');
    }
  }

  return errors;
}

export function activeRunDatasets(datasets: DatasetResponse[]) {
  return datasets.filter((dataset) => Boolean(dataset.currentVersionId));
}

export function activeRunTargets(targets: ChatbotTargetResponse[]) {
  return targets.filter((target) => target.status === 'ACTIVE');
}

export function activeRunProviders(providers: ProviderResponse[]) {
  return providers.filter((provider) => provider.status === 'ACTIVE');
}
