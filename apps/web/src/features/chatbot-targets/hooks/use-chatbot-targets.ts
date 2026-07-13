import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createChatbotTarget,
  deleteChatbotTarget,
  getChatbotTarget,
  getChatbotTargetExamples,
  getChatbotTargetReadiness,
  getProjectChatbotTargets,
  inspectChatbotTargetCurl,
  testChatbotTarget,
  updateChatbotTarget,
} from '@/features/chatbot-targets/api/chatbot-targets.api';
import type {
  CreateChatbotTargetRequest,
  InspectCurlRequest,
  TestChatbotTargetRequest,
  UpdateChatbotTargetRequest,
} from '@/features/chatbot-targets/types/chatbot-target';

export const chatbotTargetKeys = {
  all: ['chatbot-targets'] as const,
  project: (projectId: string) => [...chatbotTargetKeys.all, 'project', projectId] as const,
  detail: (targetId: string) => [...chatbotTargetKeys.all, 'detail', targetId] as const,
  examples: (targetId: string) => [...chatbotTargetKeys.detail(targetId), 'examples'] as const,
  readiness: (targetId: string) => [...chatbotTargetKeys.detail(targetId), 'readiness'] as const,
};

export function useProjectChatbotTargets(projectId: string) {
  return useQuery({
    queryKey: chatbotTargetKeys.project(projectId),
    queryFn: ({ signal }) => getProjectChatbotTargets(projectId, signal),
    enabled: Boolean(projectId),
  });
}

export function useChatbotTarget(targetId: string) {
  return useQuery({
    queryKey: chatbotTargetKeys.detail(targetId),
    queryFn: ({ signal }) => getChatbotTarget(targetId, signal),
    enabled: Boolean(targetId),
  });
}

export function useCreateChatbotTarget(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateChatbotTargetRequest) => createChatbotTarget(projectId, data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: chatbotTargetKeys.project(projectId) });
    },
  });
}

export function useUpdateChatbotTarget() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ targetId, data }: { targetId: string; data: UpdateChatbotTargetRequest }) =>
      updateChatbotTarget(targetId, data),
    onSuccess: (target) => {
      void queryClient.invalidateQueries({ queryKey: chatbotTargetKeys.detail(target.id) });
      void queryClient.invalidateQueries({ queryKey: chatbotTargetKeys.project(target.projectId) });
      void queryClient.invalidateQueries({ queryKey: chatbotTargetKeys.readiness(target.id) });
    },
  });
}

export function useDeleteChatbotTarget(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (targetId: string) => deleteChatbotTarget(targetId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: chatbotTargetKeys.project(projectId) });
    },
  });
}

export function useInspectChatbotTargetCurl(projectId: string) {
  return useMutation({
    mutationFn: (data: InspectCurlRequest) => inspectChatbotTargetCurl(projectId, data),
  });
}

export function useTestChatbotTarget() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ targetId, data }: { targetId: string; data: TestChatbotTargetRequest }) =>
      testChatbotTarget(targetId, data),
    onSuccess: (_, { targetId }) => {
      void queryClient.invalidateQueries({ queryKey: chatbotTargetKeys.examples(targetId) });
      void queryClient.invalidateQueries({ queryKey: chatbotTargetKeys.readiness(targetId) });
      void queryClient.invalidateQueries({ queryKey: chatbotTargetKeys.detail(targetId) });
    },
  });
}

export function useChatbotTargetExamples(targetId: string) {
  return useQuery({
    queryKey: chatbotTargetKeys.examples(targetId),
    queryFn: ({ signal }) => getChatbotTargetExamples(targetId, signal),
    enabled: Boolean(targetId),
  });
}

export function useChatbotTargetReadiness(targetId: string) {
  return useQuery({
    queryKey: chatbotTargetKeys.readiness(targetId),
    queryFn: ({ signal }) => getChatbotTargetReadiness(targetId, signal),
    enabled: Boolean(targetId),
  });
}
