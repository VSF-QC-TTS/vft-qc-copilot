import type { PromptfooConfigObject } from './promptfoo-adapter.js';

export interface PromptfooExecutor {
  execute(config: PromptfooConfigObject | Record<string, unknown>): Promise<unknown>;
}
