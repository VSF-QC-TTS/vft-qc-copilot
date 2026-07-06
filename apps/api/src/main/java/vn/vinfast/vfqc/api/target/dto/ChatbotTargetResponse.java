package vn.vinfast.vfqc.api.target.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import vn.vinfast.vfqc.api.target.enums.ChatbotTargetStatus;

public record ChatbotTargetResponse(
    UUID id,
    UUID projectId,
    String name,
    String description,
    String rawCurlSanitized,
    TargetRequestConfigDto requestConfig,
    ResponseExtractionConfigDto responseExtractionConfig,
    Long latestExampleId,
    ChatbotTargetStatus status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
