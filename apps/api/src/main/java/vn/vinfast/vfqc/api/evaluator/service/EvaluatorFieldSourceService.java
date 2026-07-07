package vn.vinfast.vfqc.api.evaluator.service;

import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.evaluator.dto.EvaluatorFieldSourceResponse;

public interface EvaluatorFieldSourceService {

  /**
   * Lists field sources available for evaluator rules in a project.
   *
   * <p>Requires project view permission. Dataset fields come from the latest project schema; target
   * extracted fields are included only when a visible chatbot target id is supplied.
   *
   * @param user current authenticated user
   * @param projectId project context
   * @param chatbotTargetId optional chatbot target used to expose extracted response fields
   * @return grouped field-source metadata for the evaluator builder UI
   */
  EvaluatorFieldSourceResponse list(CurrentUser user, UUID projectId, UUID chatbotTargetId);
}
