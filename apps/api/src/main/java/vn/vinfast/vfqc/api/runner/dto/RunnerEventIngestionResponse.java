package vn.vinfast.vfqc.api.runner.dto;

import java.util.UUID;
import vn.vinfast.vfqc.api.runner.enums.RunnerEventStatus;

public record RunnerEventIngestionResponse(
    String eventId, UUID testRunId, long sequenceNo, RunnerEventStatus status) {}
