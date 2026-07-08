package vn.vinfast.vfqc.api.testrun.sse;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import vn.vinfast.vfqc.api.runner.enums.RunnerEventType;

public record TestRunSseEventResponse(
    String eventId,
    long sequenceNo,
    UUID testRunId,
    RunnerEventType eventType,
    OffsetDateTime occurredAt,
    OffsetDateTime createdAt,
    Map<String, Object> payload) {}
