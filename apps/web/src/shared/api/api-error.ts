import { z } from 'zod';

const fieldErrorSchema = z.object({
  field: z.string(),
  message: z.string(),
});

const problemDetailSchema = z.looseObject({
  status: z.number().optional(),
  code: z.string().optional(),
  title: z.string().optional(),
  detail: z.string().optional(),
  messageKey: z.string().optional(),
  traceId: z.string().optional(),
  fieldErrors: z.array(fieldErrorSchema).optional(),
});

interface ApiErrorInit {
  status: number;
  code?: string;
  title?: string;
  detail?: string;
  messageKey?: string;
  traceId?: string;
  fieldErrors?: ReadonlyArray<z.infer<typeof fieldErrorSchema>>;
  networkError?: boolean;
  cause?: unknown;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string | undefined;
  readonly title: string | undefined;
  readonly detail: string | undefined;
  readonly messageKey: string | undefined;
  readonly traceId: string | undefined;
  readonly fieldErrors: ReadonlyArray<z.infer<typeof fieldErrorSchema>> | undefined;
  readonly authenticationError: boolean;
  readonly permissionError: boolean;
  readonly networkError: boolean;

  constructor(init: ApiErrorInit) {
    super(init.detail ?? init.title ?? `Request failed with status ${init.status}`, {
      cause: init.cause,
    });
    this.name = 'ApiError';
    this.status = init.status;
    this.code = init.code;
    this.title = init.title;
    this.detail = init.detail;
    this.messageKey = init.messageKey;
    this.traceId = init.traceId;
    this.fieldErrors = init.fieldErrors;
    this.authenticationError = init.status === 401;
    this.permissionError = init.status === 403;
    this.networkError = init.networkError ?? false;
  }
}

export class ApiContractError extends Error {
  constructor(message: string, options?: ErrorOptions) {
    super(message, options);
    this.name = 'ApiContractError';
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

export function createNetworkError(cause: unknown): ApiError {
  return new ApiError({
    status: 0,
    detail: 'Network request failed',
    networkError: true,
    cause,
  });
}

export async function normalizeErrorResponse(response: Response): Promise<ApiError> {
  const requestId = response.headers.get('X-Request-Id') ?? undefined;
  const text = await response.text();
  let problem: z.infer<typeof problemDetailSchema> | undefined;

  if (text.length > 0 && response.headers.get('Content-Type')?.includes('json')) {
    try {
      const parsed: unknown = JSON.parse(text);
      const result = problemDetailSchema.safeParse(parsed);
      problem = result.success ? result.data : undefined;
    } catch {
      problem = undefined;
    }
  }

  const traceId = problem?.traceId ?? requestId;

  return new ApiError({
    status: response.status,
    ...(problem?.code ? { code: problem.code } : {}),
    ...(problem?.title ? { title: problem.title } : {}),
    ...(problem?.detail ? { detail: problem.detail } : text ? { detail: text } : {}),
    ...(problem?.messageKey ? { messageKey: problem.messageKey } : {}),
    ...(traceId ? { traceId } : {}),
    ...(problem?.fieldErrors ? { fieldErrors: problem.fieldErrors } : {}),
  });
}
