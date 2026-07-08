package vn.vinfast.vfqc.api.runner.dto;

import java.util.UUID;
import vn.vinfast.vfqc.api.testrun.enums.TestRunStatus;

public record RunnerControlStateResponse(UUID testRunId, TestRunStatus status, boolean cancellationRequested) {}
