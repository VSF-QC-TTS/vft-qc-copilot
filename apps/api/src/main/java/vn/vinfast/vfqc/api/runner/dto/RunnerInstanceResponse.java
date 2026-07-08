package vn.vinfast.vfqc.api.runner.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import vn.vinfast.vfqc.api.runner.enums.RunnerInstanceStatus;

public record RunnerInstanceResponse(
    UUID id,
    String runnerKey,
    String hostname,
    String version,
    RunnerInstanceStatus status,
    UUID currentCommandId,
    UUID currentTestRunId,
    OffsetDateTime lastHeartbeatAt,
    OffsetDateTime registeredAt,
    OffsetDateTime updatedAt) {}
