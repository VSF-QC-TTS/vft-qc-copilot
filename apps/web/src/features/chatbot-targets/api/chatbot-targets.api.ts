import { apiClient } from '@/shared/api/api';
import {
  chatbotTargetCollectionSchema,
  chatbotTargetExampleCollectionSchema,
  chatbotTargetExampleSchema,
  chatbotTargetReadinessSchema,
  chatbotTargetResponseSchema,
  inspectCurlResponseSchema,
  type CreateChatbotTargetRequest,
  type InspectCurlRequest,
  type TestChatbotTargetRequest,
  type UpdateChatbotTargetRequest,
} from '@/features/chatbot-targets/types/chatbot-target';

export async function getProjectChatbotTargets(projectId: string, signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/projects/${projectId}/chatbot-targets`,
    chatbotTargetCollectionSchema,
    { ...(signal && { signal }) },
  );
  return response.data.items;
}

export async function createChatbotTarget(projectId: string, data: CreateChatbotTargetRequest) {
  const response = await apiClient.requestJson(
    `/api/v1/projects/${projectId}/chatbot-targets`,
    chatbotTargetResponseSchema,
    {
      method: 'POST',
      body: JSON.stringify(data),
    },
  );
  return response.data;
}

export async function getChatbotTarget(targetId: string, signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/chatbot-targets/${targetId}`,
    chatbotTargetResponseSchema,
    { ...(signal && { signal }) },
  );
  return response.data;
}

export async function updateChatbotTarget(targetId: string, data: UpdateChatbotTargetRequest) {
  const response = await apiClient.requestJson(
    `/api/v1/chatbot-targets/${targetId}`,
    chatbotTargetResponseSchema,
    {
      method: 'PATCH',
      body: JSON.stringify(data),
    },
  );
  return response.data;
}

export async function deleteChatbotTarget(targetId: string) {
  await apiClient.requestVoid(`/api/v1/chatbot-targets/${targetId}`, { method: 'DELETE' });
}

export async function inspectChatbotTargetCurl(projectId: string, data: InspectCurlRequest) {
  const response = await apiClient.requestJson(
    `/api/v1/projects/${projectId}/chatbot-targets/curl/inspect`,
    inspectCurlResponseSchema,
    {
      method: 'POST',
      body: JSON.stringify(data),
    },
  );
  return response.data;
}

export async function testChatbotTarget(targetId: string, data: TestChatbotTargetRequest) {
  const response = await apiClient.requestJson(
    `/api/v1/chatbot-targets/${targetId}/test`,
    chatbotTargetExampleSchema,
    {
      method: 'POST',
      body: JSON.stringify(data),
    },
  );
  return response.data;
}

export async function getChatbotTargetExamples(targetId: string, signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/chatbot-targets/${targetId}/examples`,
    chatbotTargetExampleCollectionSchema,
    { ...(signal && { signal }) },
  );
  return response.data.items;
}

export async function getChatbotTargetReadiness(targetId: string, signal?: AbortSignal) {
  const response = await apiClient.requestJson(
    `/api/v1/chatbot-targets/${targetId}/readiness`,
    chatbotTargetReadinessSchema,
    { ...(signal && { signal }) },
  );
  return response.data;
}
