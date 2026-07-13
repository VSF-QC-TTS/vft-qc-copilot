import type {
  BindingLocation,
  ChatbotTargetResponse,
  ChatbotTargetStatus,
  CreateChatbotTargetRequest,
  DatasetMapping,
  ExtractionSource,
  ResponseExtractionField,
  SecretBinding,
  TargetBodyType,
  TargetRequestConfig,
  UpdateChatbotTargetRequest,
} from '@/features/chatbot-targets/types/chatbot-target';

export interface SecretBindingDraft {
  location: BindingLocation;
  keyOrPath: string;
  secretId: string;
}

export interface DatasetMappingDraft {
  location: BindingLocation;
  keyOrPath: string;
  columnName: string;
}

export interface ExtractionFieldDraft {
  fieldName: string;
  source: ExtractionSource;
  path: string;
  required: boolean;
}

export interface ChatbotTargetFormState {
  name: string;
  description: string;
  sanitizedCurl: string;
  method: string;
  url: string;
  headersText: string;
  queryText: string;
  bodyType: TargetBodyType;
  body: string;
  secretBindings: SecretBindingDraft[];
  datasetMappings: DatasetMappingDraft[];
  extractionFields: ExtractionFieldDraft[];
  status: ChatbotTargetStatus;
}

export function emptyTargetFormState(): ChatbotTargetFormState {
  return {
    name: '',
    description: '',
    sanitizedCurl: '',
    method: 'POST',
    url: '',
    headersText: '',
    queryText: '',
    bodyType: 'JSON',
    body: '{\n  "question": "{{question}}"\n}',
    secretBindings: [],
    datasetMappings: [{ location: 'BODY_JSON', keyOrPath: 'question', columnName: 'question' }],
    extractionFields: [
      { fieldName: 'answer', source: 'JSON_PATH', path: '$.answer', required: true },
    ],
    status: 'DRAFT',
  };
}

export function formStateFromTarget(target: ChatbotTargetResponse): ChatbotTargetFormState {
  const requestConfig = target.requestConfig;
  return {
    name: target.name,
    description: target.description ?? '',
    sanitizedCurl: target.rawCurlSanitized ?? '',
    method: requestConfig?.method ?? 'POST',
    url: requestConfig?.url ?? '',
    headersText: recordToLines(requestConfig?.headers ?? {}),
    queryText: recordToLines(requestConfig?.queryParams ?? {}),
    bodyType: requestConfig?.bodyType ?? 'NONE',
    body: requestConfig?.body ?? '',
    secretBindings: (requestConfig?.secretBindings ?? []).map((binding) => ({
      location: binding.location,
      keyOrPath: binding.keyOrPath,
      secretId: binding.secretId,
    })),
    datasetMappings: (requestConfig?.datasetMappings ?? []).map((mapping) => ({
      location: mapping.location,
      keyOrPath: mapping.keyOrPath,
      columnName: mapping.columnName,
    })),
    extractionFields: (target.responseExtractionConfig?.fields ?? []).map((field) => ({
      fieldName: field.fieldName,
      source: field.source,
      path: field.path ?? '',
      required: field.required,
    })),
    status: target.status,
  };
}

export function applyRequestConfig(
  state: ChatbotTargetFormState,
  requestConfig: TargetRequestConfig,
): ChatbotTargetFormState {
  return {
    ...state,
    method: requestConfig.method ?? state.method,
    url: requestConfig.url ?? state.url,
    headersText: recordToLines(requestConfig.headers ?? {}),
    queryText: recordToLines(requestConfig.queryParams ?? {}),
    bodyType: requestConfig.bodyType ?? state.bodyType,
    body: requestConfig.body ?? state.body,
    secretBindings: (requestConfig.secretBindings ?? []).map((binding) => ({
      location: binding.location,
      keyOrPath: binding.keyOrPath,
      secretId: binding.secretId,
    })),
    datasetMappings: (requestConfig.datasetMappings ?? state.datasetMappings).map((mapping) => ({
      location: mapping.location,
      keyOrPath: mapping.keyOrPath,
      columnName: mapping.columnName,
    })),
  };
}

export function buildCreateRequest(state: ChatbotTargetFormState): CreateChatbotTargetRequest {
  return {
    name: state.name.trim(),
    description: state.description.trim(),
    rawCurl: state.sanitizedCurl,
    method: state.method.trim().toUpperCase(),
    url: state.url.trim(),
    headers: parseKeyValueLines(state.headersText),
    queryParams: parseKeyValueLines(state.queryText),
    bodyType: state.bodyType,
    body: state.body,
    secretBindings: validSecretBindings(state.secretBindings),
    datasetMappings: validDatasetMappings(state.datasetMappings),
    responseExtractionConfig: { fields: validExtractionFields(state.extractionFields) },
  };
}

export function buildUpdateRequest(state: ChatbotTargetFormState): UpdateChatbotTargetRequest {
  return { ...buildCreateRequest(state), status: state.status };
}

export function bindingLabel(location: BindingLocation) {
  if (location === 'HEADER') return 'Header';
  if (location === 'QUERY') return 'Query';
  return 'Body JSON';
}

export function statusLabel(status: ChatbotTargetStatus) {
  if (status === 'ACTIVE') return 'Hoạt động';
  if (status === 'READY') return 'Sẵn sàng';
  if (status === 'DISABLED') return 'Vô hiệu hóa';
  if (status === 'DELETED') return 'Đã xóa';
  return 'Bản nháp';
}

function parseKeyValueLines(value: string): Record<string, string> {
  const entries = value
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const separatorIndex = line.indexOf(':');
      if (separatorIndex <= 0) {
        throw new Error('Mỗi dòng header/query cần có dạng Key: Value');
      }
      return [line.slice(0, separatorIndex).trim(), line.slice(separatorIndex + 1).trim()] as const;
    });

  return Object.fromEntries(entries);
}

function recordToLines(value: Record<string, string>) {
  return Object.entries(value)
    .map(([key, entryValue]) => `${key}: ${entryValue}`)
    .join('\n');
}

function validSecretBindings(bindings: SecretBindingDraft[]): SecretBinding[] {
  return bindings
    .filter((binding) => binding.keyOrPath.trim() || binding.secretId.trim())
    .map((binding) => ({
      location: binding.location,
      keyOrPath: binding.keyOrPath.trim(),
      secretId: binding.secretId,
    }));
}

function validDatasetMappings(mappings: DatasetMappingDraft[]): DatasetMapping[] {
  return mappings
    .filter((mapping) => mapping.keyOrPath.trim() || mapping.columnName.trim())
    .map((mapping) => ({
      location: mapping.location,
      keyOrPath: mapping.keyOrPath.trim(),
      columnName: mapping.columnName.trim(),
    }));
}

function validExtractionFields(fields: ExtractionFieldDraft[]): ResponseExtractionField[] {
  return fields
    .filter((field) => field.fieldName.trim())
    .map((field) => ({
      fieldName: field.fieldName.trim(),
      source: field.source,
      path: field.path.trim(),
      required: field.required,
    }));
}
