import { apiClient } from '@/shared/api/api';
import {
  evaluatorCollectionSchema,
  evaluatorFieldSourceResponseSchema,
  evaluatorOperatorCollectionSchema,
  evaluatorResponseSchema,
  evaluatorValidationResponseSchema,
  evaluatorVersionCollectionSchema,
  evaluatorVersionResponseSchema,
  type CreateEvaluatorRequest,
  type CreateEvaluatorVersionRequest,
  type UpdateEvaluatorRequest,
  type ValidateEvaluatorRequest,
} from '@/features/evaluators/types/evaluator';

export async function getProjectEvaluators(projectId: string, signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/projects/${projectId}/evaluators`,
    evaluatorCollectionSchema,
    { ...(signal && { signal }) },
  );
  return response.data.items;
}

export async function createEvaluator(projectId: string, data: CreateEvaluatorRequest) {
  const response = await apiClient.requestJson(
    `/api/v1/projects/${projectId}/evaluators`,
    evaluatorResponseSchema,
    { method: 'POST', body: JSON.stringify(data) },
  );
  return response.data;
}

export async function getEvaluator(evaluatorId: string, signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/evaluators/${evaluatorId}`,
    evaluatorResponseSchema,
    { ...(signal && { signal }) },
  );
  return response.data;
}

export async function updateEvaluator(evaluatorId: string, data: UpdateEvaluatorRequest) {
  const response = await apiClient.requestJson(
    `/api/v1/evaluators/${evaluatorId}`,
    evaluatorResponseSchema,
    { method: 'PATCH', body: JSON.stringify(data) },
  );
  return response.data;
}

export async function deleteEvaluator(evaluatorId: string) {
  await apiClient.requestVoid(`/api/v1/evaluators/${evaluatorId}`, { method: 'DELETE' });
}

export async function getEvaluatorVersions(evaluatorId: string, signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/evaluators/${evaluatorId}/versions`,
    evaluatorVersionCollectionSchema,
    { ...(signal && { signal }) },
  );
  return response.data.items;
}

export async function createEvaluatorVersion(
  evaluatorId: string,
  data: CreateEvaluatorVersionRequest,
) {
  const response = await apiClient.requestJson(
    `/api/v1/evaluators/${evaluatorId}/versions`,
    evaluatorVersionResponseSchema,
    { method: 'POST', body: JSON.stringify(data) },
  );
  return response.data;
}

export async function getEvaluatorVersion(versionId: string, signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/evaluator-versions/${versionId}`,
    evaluatorVersionResponseSchema,
    { ...(signal && { signal }) },
  );
  return response.data;
}

export async function getEvaluatorFieldSources(
  projectId: string,
  chatbotTargetId?: string,
  signal?: AbortSignal,
) {
  const params = chatbotTargetId ? `?chatbotTargetId=${chatbotTargetId}` : '';
  const response = await apiClient.requestJson(
    `/api/v1/projects/${projectId}/evaluator-field-sources${params}`,
    evaluatorFieldSourceResponseSchema,
    { ...(signal && { signal }) },
  );
  return response.data;
}

export async function getEvaluatorOperators(signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/evaluator-operators`,
    evaluatorOperatorCollectionSchema,
    { ...(signal && { signal }) },
  );
  return response.data.items;
}

export async function validateEvaluator(projectId: string, data: ValidateEvaluatorRequest) {
  const response = await apiClient.requestJson(
    `/api/v1/projects/${projectId}/evaluators/validate`,
    evaluatorValidationResponseSchema,
    { method: 'POST', body: JSON.stringify(data) },
  );
  return response.data;
}
