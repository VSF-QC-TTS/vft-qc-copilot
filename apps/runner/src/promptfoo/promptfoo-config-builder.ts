import type { PromptfooAdapterInput } from './promptfoo-adapter.js';

export interface PromptfooConfigBuilder {
  build(input: PromptfooAdapterInput): Record<string, unknown>;
}
