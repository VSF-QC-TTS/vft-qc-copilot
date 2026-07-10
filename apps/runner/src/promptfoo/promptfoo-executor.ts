import type { EvaluateOptions, EvaluateTestSuite } from 'promptfoo';

import type { PromptfooConfigObject } from './promptfoo-adapter.js';

export interface PromptfooExecutor {
  execute(
    config: PromptfooConfigObject | Record<string, unknown>,
    options?: PromptfooExecutionOptions,
  ): Promise<unknown>;
}

export interface PromptfooExecutionOptions {
  readonly signal?: AbortSignal;
  readonly onProgress?: (completed: number, total: number) => void;
}

export type PromptfooEvaluate = (testSuite: EvaluateTestSuite, options?: EvaluateOptions) => Promise<unknown>;

export class PromptfooLibraryExecutor implements PromptfooExecutor {
  public constructor(private readonly evaluateFn: PromptfooEvaluate = defaultPromptfooEvaluate) {}

  public async execute(
    config: PromptfooConfigObject | Record<string, unknown>,
    options: PromptfooExecutionOptions = {},
  ): Promise<unknown> {
    const evalRecord = await this.evaluateFn(toEvaluateTestSuite(config), {
      cache: false,
      showProgressBar: false,
      ...(options.signal === undefined ? {} : { abortSignal: options.signal }),
      ...(options.onProgress === undefined
        ? {}
        : { progressCallback: (completed: number, total: number) => options.onProgress?.(completed, total) }),
    });
    if (hasEvaluateSummary(evalRecord)) {
      return evalRecord.toEvaluateSummary();
    }
    return evalRecord;
  }
}

function toEvaluateTestSuite(config: PromptfooConfigObject | Record<string, unknown>): EvaluateTestSuite {
  return config as unknown as EvaluateTestSuite;
}

async function defaultPromptfooEvaluate(testSuite: EvaluateTestSuite, options?: EvaluateOptions): Promise<unknown> {
  const promptfoo = await import('promptfoo');
  return promptfoo.evaluate(testSuite, options);
}

function hasEvaluateSummary(value: unknown): value is { toEvaluateSummary(): Promise<unknown> } {
  return typeof value === 'object' && value !== null && 'toEvaluateSummary' in value && typeof value.toEvaluateSummary === 'function';
}
