package vn.vinfast.vfqc.api.target.dto;

import java.util.List;

public record ChatbotTargetReadinessResponse(boolean ready, List<ReadinessBlocker> blockers) {

  public record ReadinessBlocker(String code, String message) {}
}
