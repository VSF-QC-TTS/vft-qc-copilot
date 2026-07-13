import { z } from 'zod';

export const projectSetupStepsSchema = z.object({
  hasDataset: z.boolean(),
  hasActiveDatasetVersion: z.boolean(),
  hasActiveChatbotTarget: z.boolean(),
  hasResponseExtraction: z.boolean(),
  hasEvaluator: z.boolean(),
  hasUsableJudgeProvider: z.boolean(),
});

export const projectSetupCountsSchema = z.object({
  datasets: z.number(),
  activeDatasetVersions: z.number(),
  activeChatbotTargets: z.number(),
  activeChatbotTargetsWithExtraction: z.number(),
  activeEvaluators: z.number(),
  usableJudgeProviders: z.number(),
  usableBaselineProviders: z.number(),
  comparisonCandidates: z.number(),
});

export const projectSetupBlockerSchema = z.object({
  code: z.string(),
  step: z.string(),
  severity: z.string(),
  message: z.string(),
  actionEndpoint: z.string(),
});

export const projectSetupStatusResponseSchema = z.object({
  projectId: z.string(),
  workspaceId: z.string(),
  steps: projectSetupStepsSchema,
  counts: projectSetupCountsSchema,
  canRunSingle: z.boolean(),
  canRunComparison: z.boolean(),
  blockers: z.array(projectSetupBlockerSchema),
});

export type ProjectSetupStatusResponse = z.infer<typeof projectSetupStatusResponseSchema>;
