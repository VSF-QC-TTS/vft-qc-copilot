import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Label } from '@/shared/components/ui/label';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/components/ui/table';
import {
  emptyRuleDraft,
  expectedValueRequired,
  type ExpectedValueKind,
  type FieldSourceOption,
  type RuleDraft,
} from '@/features/evaluators/components/evaluator-rule-utils';
import {
  evaluatorOperatorSchema,
  type EvaluatorOperator,
  type EvaluatorOperatorResponse,
} from '@/features/evaluators/types/evaluator';

export interface EvaluatorRuleBuilderProps {
  rules: RuleDraft[];
  fieldSources: FieldSourceOption[];
  operators: EvaluatorOperatorResponse[];
  disabled: boolean;
  onChange: (rules: RuleDraft[]) => void;
}

const expectedKinds: ExpectedValueKind[] = [
  'literal',
  'datasetVariable',
  'number',
  'boolean',
  'json',
];

export function EvaluatorRuleBuilder({
  rules,
  fieldSources,
  operators,
  disabled,
  onChange,
}: EvaluatorRuleBuilderProps) {
  const firstFieldSource = fieldSources[0]?.value ?? 'response.statusCode';

  const updateRule = (index: number, patch: Partial<RuleDraft>) => {
    onChange(
      rules.map((rule, ruleIndex) =>
        ruleIndex === index ? normalizeRulePatch(rule, patch) : rule,
      ),
    );
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold">Rules kiểm tra</h2>
          <p className="text-sm text-muted-foreground">
            Rule-based Evaluator không hỗ trợ custom JavaScript.
          </p>
        </div>
        <Button
          type="button"
          variant="outline"
          disabled={disabled}
          onClick={() => onChange([...rules, emptyRuleDraft(firstFieldSource)])}
        >
          Thêm rule
        </Button>
      </div>

      <div className="overflow-x-auto rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-[70px]">#</TableHead>
              <TableHead>Nguồn dữ liệu</TableHead>
              <TableHead>Toán tử</TableHead>
              <TableHead>Giá trị kỳ vọng</TableHead>
              <TableHead>Trọng số</TableHead>
              <TableHead>Bắt buộc</TableHead>
              <TableHead className="w-[90px]"></TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rules.map((rule, index) => (
              <TableRow key={`${rule.fieldSource}-${String(index)}`}>
                <TableCell className="text-muted-foreground">{index + 1}</TableCell>
                <TableCell>
                  <select
                    aria-label={`Field source rule ${index + 1}`}
                    className="h-9 min-w-[220px] rounded-md border border-input bg-background px-3 text-sm"
                    value={rule.fieldSource}
                    disabled={disabled}
                    onChange={(event) => updateRule(index, { fieldSource: event.target.value })}
                  >
                    {fieldSources.map((fieldSource) => (
                      <option key={fieldSource.value} value={fieldSource.value}>
                        {fieldSource.label}
                      </option>
                    ))}
                  </select>
                </TableCell>
                <TableCell>
                  <select
                    aria-label={`Operator rule ${index + 1}`}
                    className="h-9 min-w-[190px] rounded-md border border-input bg-background px-3 text-sm"
                    value={rule.operator}
                    disabled={disabled}
                    onChange={(event) =>
                      updateRule(index, {
                        operator: evaluatorOperatorSchema.parse(event.target.value),
                      })
                    }
                  >
                    {operators.map((operator) => (
                      <option key={operator.operator} value={operator.operator}>
                        {operator.label}
                      </option>
                    ))}
                  </select>
                </TableCell>
                <TableCell>
                  {expectedValueRequired(rule.operator) ? (
                    <div className="grid min-w-[280px] gap-2 md:grid-cols-[130px_minmax(0,1fr)]">
                      <select
                        aria-label={`Expected type rule ${index + 1}`}
                        className="h-9 rounded-md border border-input bg-background px-3 text-sm"
                        value={rule.expectedKind}
                        disabled={disabled}
                        onChange={(event) =>
                          updateRule(index, { expectedKind: parseExpectedKind(event.target.value) })
                        }
                      >
                        {expectedKinds.map((kind) => (
                          <option key={kind} value={kind}>
                            {expectedKindLabel(kind)}
                          </option>
                        ))}
                      </select>
                      {rule.expectedKind === 'boolean' ? (
                        <select
                          aria-label={`Expected value rule ${index + 1}`}
                          className="h-9 rounded-md border border-input bg-background px-3 text-sm"
                          value={rule.expectedRaw}
                          disabled={disabled}
                          onChange={(event) =>
                            updateRule(index, { expectedRaw: event.target.value })
                          }
                        >
                          <option value="true">true</option>
                          <option value="false">false</option>
                        </select>
                      ) : (
                        <Input
                          aria-label={`Expected value rule ${index + 1}`}
                          value={rule.expectedRaw}
                          disabled={disabled}
                          placeholder={
                            rule.expectedKind === 'datasetVariable'
                              ? '$dataset.expected'
                              : 'Giá trị'
                          }
                          onChange={(event) =>
                            updateRule(index, { expectedRaw: event.target.value })
                          }
                        />
                      )}
                    </div>
                  ) : (
                    <span className="text-sm text-muted-foreground">Không cần expected value</span>
                  )}
                </TableCell>
                <TableCell>
                  <Input
                    aria-label={`Weight rule ${index + 1}`}
                    className="w-[90px]"
                    type="number"
                    min={0.1}
                    step={0.1}
                    value={rule.weight}
                    disabled={disabled}
                    onChange={(event) => updateRule(index, { weight: event.target.value })}
                  />
                </TableCell>
                <TableCell>
                  <input
                    aria-label={`Required rule ${index + 1}`}
                    type="checkbox"
                    checked={rule.required}
                    disabled={disabled}
                    onChange={(event) => updateRule(index, { required: event.target.checked })}
                  />
                </TableCell>
                <TableCell>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    disabled={disabled || rules.length <= 1}
                    onClick={() => onChange(rules.filter((_, ruleIndex) => ruleIndex !== index))}
                  >
                    Xóa
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      <div className="rounded-md border bg-muted p-3 text-sm text-muted-foreground">
        <Label>Tóm tắt rules</Label>
        <ul className="mt-2 list-disc space-y-1 pl-4">
          {rules.map((rule, index) => (
            <li key={`${rule.fieldSource}-summary-${String(index)}`}>
              {rule.fieldSource} · {operatorText(rule.operator)}
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}

function normalizeRulePatch(rule: RuleDraft, patch: Partial<RuleDraft>): RuleDraft {
  const next = { ...rule, ...patch };
  if (patch.operator && !expectedValueRequired(patch.operator)) {
    return { ...next, expectedRaw: '' };
  }
  if (patch.expectedKind === 'number') return { ...next, expectedRaw: '200' };
  if (patch.expectedKind === 'boolean') return { ...next, expectedRaw: 'true' };
  if (patch.expectedKind === 'json') return { ...next, expectedRaw: '{}' };
  if (patch.expectedKind === 'datasetVariable')
    return { ...next, expectedRaw: '$dataset.expected' };
  return next;
}

function parseExpectedKind(value: string): ExpectedValueKind {
  if (
    value === 'literal' ||
    value === 'datasetVariable' ||
    value === 'number' ||
    value === 'boolean' ||
    value === 'json'
  ) {
    return value;
  }
  return 'literal';
}

function expectedKindLabel(kind: ExpectedValueKind) {
  if (kind === 'datasetVariable') return 'Biến dataset';
  if (kind === 'number') return 'Số';
  if (kind === 'boolean') return 'Boolean';
  if (kind === 'json') return 'JSON';
  return 'Chuỗi';
}

function operatorText(operator: EvaluatorOperator) {
  return operator.toLowerCase().replaceAll('_', ' ');
}
