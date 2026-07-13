import { Input } from '@/shared/components/ui/input';
import { Label } from '@/shared/components/ui/label';
import { Textarea } from '@/shared/components/ui/textarea';
import type { LlmJudgeDraft } from '@/features/evaluators/components/evaluator-rule-utils';
import type { ProviderResponse } from '@/features/providers/types/provider';

export interface EvaluatorLlmJudgeEditorProps {
  draft: LlmJudgeDraft;
  providers: ProviderResponse[];
  disabled: boolean;
  showRuleThreshold: boolean;
  onChange: (draft: LlmJudgeDraft) => void;
}

export function EvaluatorLlmJudgeEditor({
  draft,
  providers,
  disabled,
  showRuleThreshold,
  onChange,
}: EvaluatorLlmJudgeEditorProps) {
  const update = (patch: Partial<LlmJudgeDraft>) => onChange({ ...draft, ...patch });

  return (
    <section className="space-y-4 rounded-lg border bg-muted/30 p-4">
      <div>
        <h2 className="text-lg font-semibold">LLM Judge</h2>
        <p className="text-sm text-muted-foreground">
          Cấu hình rubric, input template và ngưỡng điểm theo contract backend.
        </p>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="space-y-2 lg:col-span-2">
          <Label htmlFor="llm-rubric">Rubric</Label>
          <Textarea
            id="llm-rubric"
            className="min-h-28"
            value={draft.rubric}
            disabled={disabled}
            placeholder="Mô tả tiêu chí chấm điểm"
            onChange={(event) => update({ rubric: event.target.value })}
          />
        </div>

        <div className="space-y-2 lg:col-span-2">
          <Label htmlFor="llm-input-template">Input template</Label>
          <Textarea
            id="llm-input-template"
            className="min-h-28 font-mono text-sm"
            value={draft.inputTemplate}
            disabled={disabled}
            placeholder="Question: {{question}}..."
            onChange={(event) => update({ inputTemplate: event.target.value })}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-3">
          <div className="space-y-2">
            <Label htmlFor="llm-threshold">Ngưỡng Judge</Label>
            <Input
              id="llm-threshold"
              type="number"
              min={0}
              max={1}
              step={0.05}
              value={draft.threshold}
              disabled={disabled}
              onChange={(event) => update({ threshold: event.target.value })}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="llm-score-min">Score min</Label>
            <Input
              id="llm-score-min"
              type="number"
              value={draft.scoreMin}
              disabled={disabled}
              onChange={(event) => update({ scoreMin: event.target.value })}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="llm-score-max">Score max</Label>
            <Input
              id="llm-score-max"
              type="number"
              value={draft.scoreMax}
              disabled={disabled}
              onChange={(event) => update({ scoreMax: event.target.value })}
            />
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-2">
            <Label>Output schema</Label>
            <div className="flex h-9 items-center rounded-md border bg-background px-3 text-sm">
              VFQC_JUDGE_V1
            </div>
          </div>
          <div className="space-y-2">
            <Label>Pass condition</Label>
            <div className="flex h-9 items-center rounded-md border bg-background px-3 text-sm">
              SCORE_AT_LEAST_THRESHOLD
            </div>
          </div>
        </div>
      </div>

      {showRuleThreshold ? (
        <div className="max-w-xs space-y-2">
          <Label htmlFor="mixed-rule-threshold">Ngưỡng rules trong Mixed</Label>
          <Input
            id="mixed-rule-threshold"
            type="number"
            min={0}
            max={1}
            step={0.05}
            value={draft.ruleThreshold}
            disabled={disabled}
            onChange={(event) => update({ ruleThreshold: event.target.value })}
          />
        </div>
      ) : null}

      <div className="grid gap-4 md:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="llm-provider">Provider mặc định</Label>
          <select
            id="llm-provider"
            className="h-9 w-full rounded-md border border-input bg-background px-3 text-sm disabled:cursor-not-allowed disabled:opacity-50"
            value={draft.defaultJudgeProviderId}
            disabled={disabled}
            onChange={(event) => update({ defaultJudgeProviderId: event.target.value })}
          >
            <option value="">Không chọn provider mặc định</option>
            {providers.map((provider) => (
              <option key={provider.id} value={provider.id}>
                {provider.name} · {provider.defaultModel}
              </option>
            ))}
          </select>
          {providers.length === 0 ? (
            <p className="text-xs text-muted-foreground">
              Chưa có provider ACTIVE trong Workspace. Evaluator vẫn có thể lưu nhưng TestRun cần
              provider hợp lệ.
            </p>
          ) : null}
        </div>

        <div className="space-y-2">
          <Label htmlFor="llm-model">Model mặc định</Label>
          <Input
            id="llm-model"
            value={draft.defaultJudgeModel}
            disabled={disabled}
            placeholder="gpt-4.1-mini"
            onChange={(event) => update({ defaultJudgeModel: event.target.value })}
          />
        </div>
      </div>
    </section>
  );
}
