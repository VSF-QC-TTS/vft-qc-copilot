export interface PromptfooExecutor {
  execute(config: Record<string, unknown>): AsyncIterable<unknown>;
}
