package vn.vinfast.vfqc.api.testrun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import vn.vinfast.vfqc.api.testrun.enums.TestRunTargetType;

public record RunTargetRequest(
    @NotNull(message = "Target type is required")
        @Schema(description = "Type of target to execute in this run", example = "CHATBOT_TARGET")
        TestRunTargetType targetType,
    @Schema(description = "Chatbot target id when targetType is CHATBOT_TARGET")
    UUID chatbotTargetId,
    @Schema(description = "Provider id when targetType is LLM_BASELINE")
    UUID providerId,
    @Schema(description = "Model name for an LLM baseline target", example = "gpt-4.1-mini")
    String model,
    @Schema(description = "Optional display name override for this run target", example = "Production chatbot")
    String name) {}
