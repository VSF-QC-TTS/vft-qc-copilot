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
