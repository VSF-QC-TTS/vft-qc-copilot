package vn.vinfast.vfqc.api.target.service.impl;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import vn.vinfast.vfqc.api.target.dto.ChatbotTargetReadinessResponse;
import vn.vinfast.vfqc.api.target.entity.ChatbotTarget;
import vn.vinfast.vfqc.api.target.service.ChatbotTargetReadinessService;

@Service
public class ChatbotTargetReadinessServiceImpl implements ChatbotTargetReadinessService {

  @Override
  public ChatbotTargetReadinessResponse readiness(ChatbotTarget target) {
    var blockers = new ArrayList<ChatbotTargetReadinessResponse.ReadinessBlocker>();
    if (target.getMethod() == null || target.getMethod().isBlank()) {
      blockers.add(blocker("TARGET_METHOD_MISSING", "Target method is required."));
    }
    if (target.getUrl() == null || target.getUrl().isBlank()) {
      blockers.add(blocker("TARGET_URL_MISSING", "Target URL is required."));
    }
    if (target.getResponseExtractionConfigJson() == null
        || target.getResponseExtractionConfigJson().isEmpty()) {
      blockers.add(blocker("TARGET_EXTRACTION_MISSING", "Response extraction is required."));
    }
    return new ChatbotTargetReadinessResponse(blockers.isEmpty(), List.copyOf(blockers));
  }

  private ChatbotTargetReadinessResponse.ReadinessBlocker blocker(String code, String message) {
    return new ChatbotTargetReadinessResponse.ReadinessBlocker(code, message);
  }
}
