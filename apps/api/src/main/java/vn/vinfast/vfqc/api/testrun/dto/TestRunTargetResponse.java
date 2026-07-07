package vn.vinfast.vfqc.api.testrun.dto;

import java.util.Map;
import java.util.UUID;
import vn.vinfast.vfqc.api.testrun.enums.TestRunTargetType;

public record TestRunTargetResponse(
    UUID id,
    TestRunTargetType targetType,
    UUID chatbotTargetId,
    UUID providerId,
    String model,
    String name,
    int orderIndex,
    Map<String, Object> config) {}
