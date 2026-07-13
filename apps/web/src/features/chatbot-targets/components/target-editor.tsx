import { useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Textarea } from '@/shared/components/ui/textarea';
import { notifyError, notifySuccess } from '@/shared/lib/notifications';
import type { SecretResponse } from '@/features/secrets/types/secret';
import {
  bindingLocationSchema,
  chatbotTargetStatusSchema,
  extractionSourceSchema,
  targetBodyTypeSchema,
} from '@/features/chatbot-targets/types/chatbot-target';
import type {
  BindingLocation,
  InspectCurlResponse,
  SecretBindingCandidate,
} from '@/features/chatbot-targets/types/chatbot-target';
import {
  applyRequestConfig,
  bindingLabel,
  type ChatbotTargetFormState,
  type ExtractionFieldDraft,
} from '@/features/chatbot-targets/components/target-form-utils';

const bindingLocations: BindingLocation[] = ['HEADER', 'QUERY', 'BODY_JSON'];

export interface TargetEditorProps {
  value: ChatbotTargetFormState;
  onChange: (value: ChatbotTargetFormState) => void;
  activeSecrets: SecretResponse[];
  canEdit: boolean;
  isPending: boolean;
  isInspecting: boolean;
  onInspectCurl: (rawCurl: string) => Promise<InspectCurlResponse>;
  onSubmit: () => Promise<void>;
}

export function TargetEditor({
  value,
  onChange,
  activeSecrets,
  canEdit,
  isPending,
  isInspecting,
  onInspectCurl,
  onSubmit,
}: TargetEditorProps) {
  const rawCurlRef = useRef<HTMLTextAreaElement | null>(null);
  const [hasRawCurlInput, setHasRawCurlInput] = useState(false);

  const disabled = !canEdit || isPending;

  const update = (patch: Partial<ChatbotTargetFormState>) => onChange({ ...value, ...patch });

  const handleInspectCurl = async () => {
    const rawCurl = rawCurlRef.current?.value ?? '';
    try {
      const inspected = await onInspectCurl(rawCurl);
      const candidates = inspected.secretBindingCandidates ?? [];
      const nextBindings =
        candidates.length > 0 ? candidates.map(candidateToBindingDraft) : value.secretBindings;
      onChange({
        ...applyRequestConfig(value, inspected.parsedRequest),
        sanitizedCurl: inspected.sanitizedCurl,
        secretBindings: nextBindings,
      });
      if (rawCurlRef.current) {
        rawCurlRef.current.value = '';
      }
      setHasRawCurlInput(false);
      notifySuccess('Đã phân tích cURL và ẩn giá trị nhạy cảm');
    } catch {
      notifyError('Không thể phân tích cURL');
    }
  };

  return (
    <form onSubmit={(event) => void handleSubmit(event, onSubmit)} className="space-y-6">
      <section className="rounded-lg border p-5">
        <h2 className="text-base font-semibold">Thông tin cơ bản</h2>
        <div className="mt-4 grid gap-4 md:grid-cols-2">
          <div className="space-y-2">
            <label htmlFor="target-name" className="text-sm font-medium">
              Tên target
            </label>
            <Input
              id="target-name"
              value={value.name}
              onChange={(event) => update({ name: event.target.value })}
              disabled={disabled}
              placeholder="Ví dụ: VF Chatbot"
            />
          </div>
          <div className="space-y-2">
            <label htmlFor="target-status" className="text-sm font-medium">
              Trạng thái
            </label>
            <select
              id="target-status"
              value={value.status}
              onChange={(event) =>
                update({ status: chatbotTargetStatusSchema.parse(event.target.value) })
              }
              disabled={disabled}
              className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
            >
              <option value="DRAFT">Bản nháp</option>
              <option value="READY">Sẵn sàng</option>
              <option value="ACTIVE">Hoạt động</option>
              <option value="DISABLED">Vô hiệu hóa</option>
            </select>
          </div>
          <div className="space-y-2 md:col-span-2">
            <label htmlFor="target-description" className="text-sm font-medium">
              Mô tả
            </label>
            <Textarea
              id="target-description"
              value={value.description}
              onChange={(event) => update({ description: event.target.value })}
              disabled={disabled}
              placeholder="Mô tả ngắn về API chatbot"
            />
          </div>
        </div>
      </section>

      <section className="rounded-lg border p-5">
        <div className="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
          <div>
            <h2 className="text-base font-semibold">Phân tích cURL</h2>
            <p className="text-sm text-muted-foreground">
              Raw cURL chỉ gửi tới backend inspect; frontend chỉ giữ bản đã sanitize.
            </p>
          </div>
          <Button
            type="button"
            variant="outline"
            onClick={() => void handleInspectCurl()}
            disabled={disabled || isInspecting || !hasRawCurlInput}
          >
            Phân tích cURL
          </Button>
        </div>
        <Textarea
          ref={rawCurlRef}
          onChange={(event) => setHasRawCurlInput(event.target.value.trim().length > 0)}
          disabled={disabled || isInspecting}
          placeholder="Dán cURL tại đây. Giá trị Authorization/Cookie sẽ không được hiển thị lại sau inspect."
          className="mt-4 min-h-[120px] font-mono text-xs"
        />
        {value.sanitizedCurl && (
          <div className="mt-4 rounded-md bg-muted p-3">
            <p className="mb-2 text-xs font-medium text-muted-foreground">cURL đã sanitize</p>
            <pre className="whitespace-pre-wrap break-words text-xs">{value.sanitizedCurl}</pre>
          </div>
        )}
      </section>

      <section className="rounded-lg border p-5">
        <h2 className="text-base font-semibold">Request configuration</h2>
        <div className="mt-4 grid gap-4 md:grid-cols-[140px_1fr]">
          <div className="space-y-2">
            <label htmlFor="target-method" className="text-sm font-medium">
              Method
            </label>
            <Input
              id="target-method"
              value={value.method}
              onChange={(event) => update({ method: event.target.value })}
              disabled={disabled}
            />
          </div>
          <div className="space-y-2">
            <label htmlFor="target-url" className="text-sm font-medium">
              URL
            </label>
            <Input
              id="target-url"
              value={value.url}
              onChange={(event) => update({ url: event.target.value })}
              disabled={disabled}
              placeholder="https://example.test/api/chat"
            />
          </div>
        </div>
        <div className="mt-4 grid gap-4 md:grid-cols-2">
          <KeyValueTextArea
            label="Headers"
            value={value.headersText}
            onChange={(headersText) => update({ headersText })}
            disabled={disabled}
          />
          <KeyValueTextArea
            label="Query params"
            value={value.queryText}
            onChange={(queryText) => update({ queryText })}
            disabled={disabled}
          />
        </div>
        <div className="mt-4 grid gap-4 md:grid-cols-[160px_1fr]">
          <div className="space-y-2">
            <label htmlFor="target-body-type" className="text-sm font-medium">
              Body type
            </label>
            <select
              id="target-body-type"
              value={value.bodyType}
              onChange={(event) =>
                update({ bodyType: targetBodyTypeSchema.parse(event.target.value) })
              }
              disabled={disabled}
              className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
            >
              <option value="NONE">Không có</option>
              <option value="JSON">JSON</option>
              <option value="TEXT">Text</option>
            </select>
          </div>
          <div className="space-y-2">
            <label htmlFor="target-body" className="text-sm font-medium">
              Body
            </label>
            <Textarea
              id="target-body"
              value={value.body}
              onChange={(event) => update({ body: event.target.value })}
              disabled={disabled || value.bodyType === 'NONE'}
              className="min-h-[120px] font-mono text-xs"
            />
          </div>
        </div>
      </section>

      <BindingsEditor
        title="Secret bindings"
        description="Bind candidate hoặc key/path với secret đã lưu. Không nhập secret plaintext."
        rows={value.secretBindings}
        activeSecrets={activeSecrets}
        canEdit={!disabled}
        onChange={(secretBindings) => update({ secretBindings })}
      />

      <DatasetMappingsEditor
        rows={value.datasetMappings}
        canEdit={!disabled}
        onChange={(datasetMappings) => update({ datasetMappings })}
      />

      <ExtractionEditor
        rows={value.extractionFields}
        canEdit={!disabled}
        onChange={(extractionFields) => update({ extractionFields })}
      />

      <div className="flex items-center justify-end gap-3">
        {!canEdit && <span className="text-sm text-muted-foreground">Bạn chỉ có quyền xem.</span>}
        <Button type="submit" disabled={disabled}>
          Lưu target
        </Button>
      </div>
    </form>
  );
}

function KeyValueTextArea({
  label,
  value,
  onChange,
  disabled,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  disabled: boolean;
}) {
  return (
    <label className="space-y-2">
      <span className="text-sm font-medium">{label}</span>
      <Textarea
        value={value}
        onChange={(event) => onChange(event.target.value)}
        disabled={disabled}
        placeholder="Key: Value"
        className="min-h-[110px] font-mono text-xs"
      />
    </label>
  );
}

function BindingsEditor({
  title,
  description,
  rows,
  activeSecrets,
  canEdit,
  onChange,
}: {
  title: string;
  description: string;
  rows: ChatbotTargetFormState['secretBindings'];
  activeSecrets: SecretResponse[];
  canEdit: boolean;
  onChange: (rows: ChatbotTargetFormState['secretBindings']) => void;
}) {
  const updateRow = (
    index: number,
    patch: Partial<ChatbotTargetFormState['secretBindings'][number]>,
  ) => onChange(rows.map((row, rowIndex) => (rowIndex === index ? { ...row, ...patch } : row)));

  return (
    <section className="rounded-lg border p-5">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-base font-semibold">{title}</h2>
          <p className="text-sm text-muted-foreground">{description}</p>
        </div>
        <Button
          type="button"
          variant="outline"
          disabled={!canEdit}
          onClick={() => onChange([...rows, { location: 'HEADER', keyOrPath: '', secretId: '' }])}
        >
          <Plus className="mr-2 h-4 w-4" />
          Thêm binding
        </Button>
      </div>
      <div className="mt-4 space-y-3">
        {rows.length === 0 ? (
          <p className="text-sm text-muted-foreground">Chưa có secret binding.</p>
        ) : (
          rows.map((row, index) => (
            <div
              key={`binding-${String(index)}`}
              className="grid gap-3 md:grid-cols-[150px_1fr_1fr_40px]"
            >
              <LocationSelect
                value={row.location}
                disabled={!canEdit}
                onChange={(location) => updateRow(index, { location })}
              />
              <Input
                value={row.keyOrPath}
                disabled={!canEdit}
                onChange={(event) => updateRow(index, { keyOrPath: event.target.value })}
                placeholder="Authorization hoặc $.token"
              />
              <select
                value={row.secretId}
                disabled={!canEdit || activeSecrets.length === 0}
                onChange={(event) => updateRow(index, { secretId: event.target.value })}
                className="h-10 rounded-md border border-input bg-background px-3 text-sm"
              >
                <option value="">Chọn secret đang hoạt động</option>
                {activeSecrets.map((secret) => (
                  <option key={secret.id} value={secret.id}>
                    {secret.name}
                  </option>
                ))}
              </select>
              <Button
                type="button"
                variant="ghost"
                disabled={!canEdit}
                onClick={() => onChange(rows.filter((_, rowIndex) => rowIndex !== index))}
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            </div>
          ))
        )}
      </div>
    </section>
  );
}

function DatasetMappingsEditor({
  rows,
  canEdit,
  onChange,
}: {
  rows: ChatbotTargetFormState['datasetMappings'];
  canEdit: boolean;
  onChange: (rows: ChatbotTargetFormState['datasetMappings']) => void;
}) {
  const updateRow = (
    index: number,
    patch: Partial<ChatbotTargetFormState['datasetMappings'][number]>,
  ) => onChange(rows.map((row, rowIndex) => (rowIndex === index ? { ...row, ...patch } : row)));

  return (
    <section className="rounded-lg border p-5">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-base font-semibold">Dataset mappings</h2>
          <p className="text-sm text-muted-foreground">
            Map cột dataset vào header, query hoặc body JSON. Schema editor nằm ở slice dataset.
          </p>
        </div>
        <Button
          type="button"
          variant="outline"
          disabled={!canEdit}
          onClick={() =>
            onChange([...rows, { location: 'BODY_JSON', keyOrPath: '', columnName: '' }])
          }
        >
          <Plus className="mr-2 h-4 w-4" />
          Thêm mapping
        </Button>
      </div>
      <div className="mt-4 space-y-3">
        {rows.map((row, index) => (
          <div
            key={`mapping-${String(index)}`}
            className="grid gap-3 md:grid-cols-[150px_1fr_1fr_40px]"
          >
            <LocationSelect
              value={row.location}
              disabled={!canEdit}
              onChange={(location) => updateRow(index, { location })}
            />
            <Input
              value={row.keyOrPath}
              disabled={!canEdit}
              onChange={(event) => updateRow(index, { keyOrPath: event.target.value })}
              placeholder="question hoặc $.question"
            />
            <Input
              value={row.columnName}
              disabled={!canEdit}
              onChange={(event) => updateRow(index, { columnName: event.target.value })}
              placeholder="question"
            />
            <Button
              type="button"
              variant="ghost"
              disabled={!canEdit}
              onClick={() => onChange(rows.filter((_, rowIndex) => rowIndex !== index))}
            >
              <Trash2 className="h-4 w-4" />
            </Button>
          </div>
        ))}
      </div>
    </section>
  );
}

function ExtractionEditor({
  rows,
  canEdit,
  onChange,
}: {
  rows: ChatbotTargetFormState['extractionFields'];
  canEdit: boolean;
  onChange: (rows: ChatbotTargetFormState['extractionFields']) => void;
}) {
  const updateRow = (index: number, patch: Partial<ExtractionFieldDraft>) =>
    onChange(rows.map((row, rowIndex) => (rowIndex === index ? { ...row, ...patch } : row)));

  return (
    <section className="rounded-lg border p-5">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-base font-semibold">Response extraction</h2>
          <p className="text-sm text-muted-foreground">
            Cấu hình field trích xuất từ response đã sanitize.
          </p>
        </div>
        <Button
          type="button"
          variant="outline"
          disabled={!canEdit}
          onClick={() =>
            onChange([...rows, { fieldName: '', source: 'JSON_PATH', path: '', required: false }])
          }
        >
          <Plus className="mr-2 h-4 w-4" />
          Thêm field
        </Button>
      </div>
      <div className="mt-4 space-y-3">
        {rows.map((row, index) => (
          <div
            key={`extraction-${String(index)}`}
            className="grid gap-3 md:grid-cols-[1fr_150px_1fr_110px_40px]"
          >
            <Input
              value={row.fieldName}
              disabled={!canEdit}
              onChange={(event) => updateRow(index, { fieldName: event.target.value })}
              placeholder="answer"
            />
            <select
              value={row.source}
              disabled={!canEdit}
              onChange={(event) =>
                updateRow(index, {
                  source: extractionSourceSchema.parse(event.target.value),
                })
              }
              className="h-10 rounded-md border border-input bg-background px-3 text-sm"
            >
              <option value="JSON_PATH">JSON path</option>
              <option value="BODY_TEXT">Body text</option>
            </select>
            <Input
              value={row.path}
              disabled={!canEdit || row.source === 'BODY_TEXT'}
              onChange={(event) => updateRow(index, { path: event.target.value })}
              placeholder="$.answer"
            />
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={row.required}
                disabled={!canEdit}
                onChange={(event) => updateRow(index, { required: event.target.checked })}
              />
              Bắt buộc
            </label>
            <Button
              type="button"
              variant="ghost"
              disabled={!canEdit}
              onClick={() => onChange(rows.filter((_, rowIndex) => rowIndex !== index))}
            >
              <Trash2 className="h-4 w-4" />
            </Button>
          </div>
        ))}
      </div>
    </section>
  );
}

function LocationSelect({
  value,
  disabled,
  onChange,
}: {
  value: BindingLocation;
  disabled: boolean;
  onChange: (value: BindingLocation) => void;
}) {
  return (
    <select
      value={value}
      disabled={disabled}
      onChange={(event) => onChange(bindingLocationSchema.parse(event.target.value))}
      className="h-10 rounded-md border border-input bg-background px-3 text-sm"
    >
      {bindingLocations.map((location) => (
        <option key={location} value={location}>
          {bindingLabel(location)}
        </option>
      ))}
    </select>
  );
}

function candidateToBindingDraft(candidate: SecretBindingCandidate) {
  return {
    location: candidate.location,
    keyOrPath: candidate.keyOrPath,
    secretId: '',
  };
}

async function handleSubmit(event: FormEvent<HTMLFormElement>, onSubmit: () => Promise<void>) {
  event.preventDefault();
  await onSubmit();
}
