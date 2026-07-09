import type { PromptfooCaseResult, PromptfooCaseStatus } from './promptfoo-adapter.js';

export interface PromptfooResultNormalizer {
  normalize(rawResult: unknown): readonly PromptfooCaseResult[];
}

export class DefaultPromptfooResultNormalizer implements PromptfooResultNormalizer {
  public normalize(rawResult: unknown): readonly PromptfooCaseResult[] {
    const results = getRawResults(rawResult);
    return results.map((result) => normalizeCaseResult(result));
  }
}

function normalizeCaseResult(raw: Readonly<Record<string, unknown>>): PromptfooCaseResult {
  if (isPromptfooEvaluateResult(raw)) {
    return normalizeEvaluateResult(raw);
  }

  const result: PromptfooCaseResult = {
    testRunTargetId: readRequiredString(raw, 'testRunTargetId'),
    datasetRowId: readRequiredNumber(raw, 'datasetRowId'),
    status: mapStatus(raw),
  };

  addOptional(result, 'score', readNumber(raw, 'score'));
  addOptional(result, 'passed', readBoolean(raw, 'passed'));
  addOptional(result, 'errorCategory', errorCategory(raw, result.status));
  addOptional(result, 'request', readRecord(raw, 'request'));
  addOptional(result, 'response', readRecord(raw, 'response'));
  addOptional(result, 'extractedOutput', readRecord(raw, 'extractedOutput'));
  addOptional(result, 'gradingResult', readRecord(raw, 'gradingResult'));
  addOptional(result, 'componentResults', readRecord(raw, 'componentResults'));
  addOptional(result, 'tokenUsage', readRecord(raw, 'tokenUsage'));
  addOptional(result, 'latencyMs', readNumber(raw, 'latencyMs'));
  addOptional(result, 'machineSummary', readString(raw, 'machineSummary') ?? readString(raw, 'error'));
  return result;
}

function normalizeEvaluateResult(raw: Readonly<Record<string, unknown>>): PromptfooCaseResult {
  const response = readRecord(raw, 'response');
  const result: PromptfooCaseResult = {
    testRunTargetId: readTestRunTargetId(raw),
    datasetRowId: readDatasetRowId(raw),
    status: readBoolean(raw, 'success') === true ? 'PASSED' : evaluateFailureStatus(raw),
  };

  addOptional(result, 'score', readNumber(raw, 'score'));
  addOptional(result, 'passed', readBoolean(raw, 'success'));
  addOptional(result, 'errorCategory', evaluateErrorCategory(raw));
  addOptional(result, 'response', response);
  addOptional(result, 'gradingResult', readRecord(raw, 'gradingResult'));
  addOptional(result, 'tokenUsage', readRecord(response ?? {}, 'tokenUsage') ?? readRecord(raw, 'tokenUsage'));
  addOptional(result, 'latencyMs', readNumber(raw, 'latencyMs'));
  addOptional(result, 'machineSummary', readString(raw, 'error') ?? readString(readRecord(raw, 'gradingResult') ?? {}, 'reason'));
  return result;
}

function mapStatus(raw: Readonly<Record<string, unknown>>): PromptfooCaseStatus {
  const status = readString(raw, 'status');
  const errorKind = readString(raw, 'errorKind');
  if (status === 'timeout') {
    return 'TIMEOUT';
  }
  if (errorKind === 'judge_parse_error') {
    return 'JUDGE_ERROR';
  }
  if (status === 'success') {
    return 'PASSED';
  }
  if (status === 'failure') {
    return 'FAILED';
  }
  return 'ERROR';
}

function errorCategory(raw: Readonly<Record<string, unknown>>, status: PromptfooCaseStatus): string | undefined {
  if (status === 'TIMEOUT') {
    return 'TARGET_TIMEOUT';
  }
  if (readString(raw, 'errorKind') === 'judge_parse_error') {
    return 'JUDGE_PARSE_ERROR';
  }
  return readString(raw, 'errorCategory');
}

function getRawResults(rawResult: unknown): readonly Readonly<Record<string, unknown>>[] {
  if (!isRecord(rawResult)) {
    throw new Error('Promptfoo raw result must be an object');
  }
  const results = rawResult.results;
  if (!Array.isArray(results) || !results.every(isRecord)) {
    throw new Error('Promptfoo raw result missing results array');
  }
  return results;
}

function isPromptfooEvaluateResult(raw: Readonly<Record<string, unknown>>): boolean {
  return isRecord(raw.provider) && isRecord(raw.testCase) && typeof raw.success === 'boolean';
}

function readTestRunTargetId(raw: Readonly<Record<string, unknown>>): string {
  const provider = readRecord(raw, 'provider') ?? {};
  const providerId = readString(provider, 'id') ?? readString(provider, 'label');
  if (providerId !== undefined && providerId.startsWith('vfqc-chatbot:')) {
    return providerId.slice('vfqc-chatbot:'.length);
  }
  if (providerId !== undefined && providerId.startsWith('vfqc-target:')) {
    return providerId.slice('vfqc-target:'.length);
  }
  throw new Error('Promptfoo result missing VFQC provider target id');
}

function readDatasetRowId(raw: Readonly<Record<string, unknown>>): number {
  const testCase = readRecord(raw, 'testCase') ?? {};
  const metadata = readRecord(testCase, 'metadata') ?? {};
  const datasetRowId = readNumber(metadata, 'datasetRowId');
  if (datasetRowId === undefined) {
    throw new Error('Promptfoo result missing datasetRowId metadata');
  }
  return datasetRowId;
}

function evaluateFailureStatus(raw: Readonly<Record<string, unknown>>): PromptfooCaseResult['status'] {
  return readString(raw, 'error') === undefined ? 'FAILED' : 'ERROR';
}

function evaluateErrorCategory(raw: Readonly<Record<string, unknown>>): string | undefined {
  return readString(raw, 'error') === undefined ? undefined : 'PROMPTFOO_ERROR';
}

function readRequiredString(record: Readonly<Record<string, unknown>>, key: string): string {
  const value = readString(record, key);
  if (value === undefined) {
    throw new Error(`Promptfoo result missing string field: ${key}`);
  }
  return value;
}

function readRequiredNumber(record: Readonly<Record<string, unknown>>, key: string): number {
  const value = readNumber(record, key);
  if (value === undefined) {
    throw new Error(`Promptfoo result missing number field: ${key}`);
  }
  return value;
}

function readString(record: Readonly<Record<string, unknown>>, key: string): string | undefined {
  const value = record[key];
  return typeof value === 'string' ? value : undefined;
}

function readNumber(record: Readonly<Record<string, unknown>>, key: string): number | undefined {
  const value = record[key];
  return typeof value === 'number' ? value : undefined;
}

function readBoolean(record: Readonly<Record<string, unknown>>, key: string): boolean | undefined {
  const value = record[key];
  return typeof value === 'boolean' ? value : undefined;
}

function readRecord(record: Readonly<Record<string, unknown>>, key: string): Record<string, unknown> | undefined {
  const value = record[key];
  return isRecord(value) ? { ...value } : undefined;
}

function addOptional<TKey extends keyof PromptfooCaseResult>(
  result: PromptfooCaseResult,
  key: TKey,
  value: PromptfooCaseResult[TKey] | undefined,
): void {
  if (value !== undefined) {
    Object.assign(result, { [key]: value });
  }
}

function isRecord(value: unknown): value is Readonly<Record<string, unknown>> {
  return typeof value === 'object' && value !== null;
}
