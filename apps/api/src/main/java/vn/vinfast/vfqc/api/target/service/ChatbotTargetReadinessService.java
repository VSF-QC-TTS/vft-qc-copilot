package vn.vinfast.vfqc.api.target.service;

import vn.vinfast.vfqc.api.target.dto.ChatbotTargetReadinessResponse;
import vn.vinfast.vfqc.api.target.entity.ChatbotTarget;

public interface ChatbotTargetReadinessService {

  /**
   * Calculates whether a target has enough sanitized request and extraction config to be activated.
   *
   * @param target target entity already loaded and permission-checked by the caller
   * @return readiness result with concrete blocker codes
   */
  ChatbotTargetReadinessResponse readiness(ChatbotTarget target);
}
