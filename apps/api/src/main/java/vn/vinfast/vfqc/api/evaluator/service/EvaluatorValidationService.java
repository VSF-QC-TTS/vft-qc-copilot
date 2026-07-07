package vn.vinfast.vfqc.api.evaluator.service;

import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.evaluator.dto.EvaluatorValidationResponse;
import vn.vinfast.vfqc.api.evaluator.dto.ValidateEvaluatorRequest;

public interface EvaluatorValidationService {

  /**
   * Validates an evaluator draft without persisting it.
   *
   * <p>Requires project view permission because validation may inspect project schema, target
   * extraction fields, and visible provider configuration. The method is read-only and returns a
   * positive validation response or throws a typed API exception for blockers.
   *
   * @param user current authenticated user
   * @param projectId project context for available field sources and provider visibility
   * @param request evaluator draft to validate
   * @return validation response when the draft is acceptable
   */
  EvaluatorValidationResponse validate(CurrentUser user, UUID projectId, ValidateEvaluatorRequest request);
}
