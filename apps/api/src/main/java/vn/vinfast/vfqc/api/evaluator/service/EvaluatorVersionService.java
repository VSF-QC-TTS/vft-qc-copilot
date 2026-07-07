package vn.vinfast.vfqc.api.evaluator.service;

import java.util.List;
import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.evaluator.dto.CreateEvaluatorVersionRequest;
import vn.vinfast.vfqc.api.evaluator.dto.EvaluatorVersionResponse;

public interface EvaluatorVersionService {

  /**
   * Lists immutable versions for an active evaluator.
   *
   * <p>Requires project view permission on the evaluator's project. Versions of soft-deleted
   * evaluators are hidden as not found.
   *
   * @param user current authenticated user
   * @param evaluatorId evaluator identifier
   * @return evaluator versions ordered newest first
   */
  List<EvaluatorVersionResponse> list(CurrentUser user, UUID evaluatorId);

  /**
   * Creates the next immutable evaluator version.
   *
   * <p>Requires project edit permission. The evaluator type is preserved from the parent evaluator;
   * this method only stores a new config/rule snapshot after validation.
   *
   * @param user current authenticated user
   * @param evaluatorId evaluator identifier
   * @param request version config and rules
   * @return created version detail
   */
  EvaluatorVersionResponse create(CurrentUser user, UUID evaluatorId, CreateEvaluatorVersionRequest request);

  /**
   * Loads one immutable evaluator version.
   *
   * <p>Requires project view permission on the parent evaluator. Versions whose parent evaluator is
   * soft-deleted are hidden as not found.
   *
   * @param user current authenticated user
   * @param evaluatorVersionId evaluator version identifier
   * @return evaluator version detail with rules
   */
  EvaluatorVersionResponse get(CurrentUser user, UUID evaluatorVersionId);
}
