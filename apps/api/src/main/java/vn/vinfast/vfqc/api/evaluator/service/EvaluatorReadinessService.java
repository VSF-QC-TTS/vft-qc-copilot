package vn.vinfast.vfqc.api.evaluator.service;

import java.util.UUID;

public interface EvaluatorReadinessService {

  /**
   * Counts active evaluators for project setup-status checks.
   *
   * <p>This read-only helper does not perform user permission checks; callers must already operate
   * in an authorized project context.
   *
   * @param projectId project identifier
   * @return number of non-deleted evaluators in the project
   */
  long activeEvaluatorCount(UUID projectId);
}
