package vn.vinfast.vfqc.api.evaluator.service;

import java.util.List;
import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.evaluator.dto.CreateEvaluatorRequest;
import vn.vinfast.vfqc.api.evaluator.dto.EvaluatorResponse;
import vn.vinfast.vfqc.api.evaluator.dto.UpdateEvaluatorRequest;

public interface EvaluatorService {

  /**
   * Lists active evaluators for a project visible to the user.
   *
   * <p>Requires project view permission and never returns soft-deleted evaluators.
   *
   * @param user current authenticated user
   * @param projectId project that owns the evaluators
   * @return active project evaluators with latest-version summaries
   */
  List<EvaluatorResponse> list(CurrentUser user, UUID projectId);

  /**
   * Creates an evaluator and immutable initial version in one transaction.
   *
   * <p>Requires project edit permission. The evaluator name is normalized and checked for active
   * project duplicates before the version and rules are persisted.
   *
   * @param user current authenticated user
   * @param projectId project that will own the evaluator
   * @param request evaluator metadata, type, config, and initial rules
   * @return created evaluator with latest-version summary
   */
  EvaluatorResponse create(CurrentUser user, UUID projectId, CreateEvaluatorRequest request);

  /**
   * Loads one active evaluator by id.
   *
   * <p>Requires project view permission. Missing or soft-deleted evaluators are hidden as not found.
   *
   * @param user current authenticated user
   * @param evaluatorId evaluator identifier
   * @return evaluator detail with latest-version summary
   */
  EvaluatorResponse get(CurrentUser user, UUID evaluatorId);

  /**
   * Updates evaluator metadata without changing evaluator type or existing versions.
   *
   * <p>Requires project edit permission. Name changes are normalized and checked against active
   * evaluators in the same project.
   *
   * @param user current authenticated user
   * @param evaluatorId evaluator identifier
   * @param request metadata update payload
   * @return updated evaluator detail
   */
  EvaluatorResponse update(CurrentUser user, UUID evaluatorId, UpdateEvaluatorRequest request);

  /**
   * Soft-deletes an evaluator.
   *
   * <p>Requires project edit permission. Historical versions remain stored but are hidden through
   * user-facing evaluator APIs once the parent evaluator is deleted.
   *
   * @param user current authenticated user
   * @param evaluatorId evaluator identifier
   */
  void delete(CurrentUser user, UUID evaluatorId);
}
