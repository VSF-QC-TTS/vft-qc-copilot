package vn.vinfast.vfqc.api.runner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import vn.vinfast.vfqc.api.runner.enums.RunnerEventType;

public record RunnerEventRequest(
    @Schema(description = "Runner-generated idempotency id for this event.", example = "evt-run-001-started")
        @NotBlank(message = "Event id is required")
        String eventId,
    @Schema(description = "Test run affected by this event.", example = "27a47393-169d-4f01-92a0-59cf2dc10f8a")
        @NotNull(message = "Test run id is required")
        UUID testRunId,
    @Schema(description = "Runner event type.", example = "RUN_STARTED")
        @NotNull(message = "Event type is required")
        RunnerEventType eventType,
    @Schema(description = "Optional runner instance id.", example = "c1350b87-c35c-40b2-bbb1-b6f8815720fd")
        UUID runnerId,
    @Schema(description = "Command id associated with this event.", example = "6c0f4306-883b-43b6-94fd-826d14c33074")
        UUID commandId,
    @Schema(description = "Runner event occurrence timestamp.") OffsetDateTime occurredAt,
    @Schema(description = "Event-specific payload. Sensitive values are sanitized before persistence.")
        Map<String, Object> payload) {}
